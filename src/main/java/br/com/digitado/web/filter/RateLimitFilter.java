package br.com.digitado.web.filter;

import br.com.digitado.config.ApplicationProperties;
import br.com.digitado.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de requisições por identidade em /api/** - proteção contra abuso e ataques
 * (flood de requisições, força bruta de senha, scraping da API).
 *
 * Como funciona:
 * - Janela fixa de 1 minuto por identidade. Usuário autenticado é identificado pelo
 *   LOGIN (o limite acompanha a conta, não a máquina); anônimo é identificado pelo IP.
 * - O login (/api/authenticate) tem dois tetos, descritos em aplicarNoLogin: senha
 *   errada por conta + IP, e volume total de chamadas por IP.
 * - Os demais endpoints sensíveis (registro, reset de senha, verificação de e-mail)
 *   contam toda requisição por IP, porque neles o abuso é repetir a ação com SUCESSO.
 * - Estourou o limite: HTTP 429 (Too Many Requests) com Retry-After, corpo JSON e
 *   log WARN com a identidade (o requestId já sai via MDC para rastreabilidade).
 * - Limites configuráveis por ambiente em application.yml ("application.rate-limit").
 *
 * Roda DEPOIS da cadeia de segurança (ordem 0 > -100 do Spring Security), para que o
 * JWT já tenha sido processado e o login esteja disponível no SecurityContext.
 *
 * Implementação em memória (ConcurrentHashMap) - suficiente para uma instância única,
 * sem dependência externa. Se o app escalar horizontalmente, trocar por um contador
 * compartilhado (ex.: Redis) mantendo este filtro como ponto único de aplicação.
 */
@Component
@Order(0)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    // Só lê o campo "username" do corpo do login. Instância própria para não
    // depender da configuração do ObjectMapper da aplicação.
    private static final ObjectMapper JSON = new ObjectMapper();

    // Janela de contagem: minuto corrente + contador de requisições
    private record Janela(long minuto, AtomicInteger contador) {}

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    private final ApplicationProperties applicationProperties;

    public RateLimitFilter(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Só limita a API; estáticos, websocket e management ficam de fora
        return !request.getRequestURI().startsWith("/api/") || !applicationProperties.getRateLimit().isEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long minutoAtual = System.currentTimeMillis() / 60000;

        // Limpeza oportunista: janelas de minutos passados são descartadas para o
        // mapa não crescer indefinidamente
        if (janelas.size() > 10_000) {
            janelas.entrySet().removeIf(e -> e.getValue().minuto() != minutoAtual);
        }

        if (isLogin(request.getRequestURI())) {
            aplicarNoLogin(request, response, filterChain, minutoAtual);
            return;
        }

        String grupo = grupoSensivel(request.getRequestURI());

        int limite;
        String identidade;

        if (grupo != null) {
            // Cada endpoint sensível tem o SEU contador. Antes todos dividiam uma
            // chave só por IP, e o efeito era absurdo: a tela de cadastro consulta
            // /api/public/verificar-email a cada pausa na digitação do e-mail, o que
            // gastava o orçamento inteiro antes de a pessoa clicar em "Criar conta" -
            // e o cadastro morria com 429 sem nunca criar a conta.
            //
            // Contam por IP porque quem se cadastra ou pede reset ainda não tem login,
            // e contam TODA requisição, inclusive a bem-sucedida: aqui o abuso é fazer
            // a coisa dar certo muitas vezes (criar contas em massa, varrer e-mails).
            limite = "email".equals(grupo)
                ? applicationProperties.getRateLimit().getVerificacaoEmailPorMinuto()
                : applicationProperties.getRateLimit().getAutenticacaoPorMinuto();
            identidade = grupo + ":" + ipDoCliente(request);
        } else {
            limite = applicationProperties.getRateLimit().getRequisicoesPorMinuto();
            identidade = identidadeDoChamador(request);
        }

        if (janela(identidade, minutoAtual).contador().incrementAndGet() > limite) {
            bloquear(request, response, identidade, limite);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Dois tetos no login, porque um número só não separa "errou a senha" de
     * "está martelando o servidor":
     *
     * 1. Senhas erradas por CONTA + IP. É a trava de força bruta. A conta entra na
     *    chave para que um aluno errando a própria senha não derrube os colegas,
     *    que saem todos do mesmo IP da escola.
     * 2. Total de chamadas por IP, acertando ou errando. Só o teto 1 deixaria quem
     *    tem uma conta válida chamar o login à vontade, e cada chamada gasta um
     *    BCrypt (lento de propósito) do servidor.
     */
    private void aplicarNoLogin(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain, long minutoAtual)
        throws ServletException, IOException {
        // O corpo precisa ser bufferizado antes de ser lido aqui, senão o
        // controller recebe um corpo vazio.
        CorpoEmCacheRequest requisicao = new CorpoEmCacheRequest(request);

        String ip = ipDoCliente(request);
        int limiteFalhas = applicationProperties.getRateLimit().getAutenticacaoPorMinuto();
        int limiteTotal = applicationProperties.getRateLimit().getAutenticacaoTotalPorMinuto();

        String chaveFalhas = "falha:" + contaTentada(requisicao.corpo()) + "@" + ip;
        Janela janelaFalhas = janela(chaveFalhas, minutoAtual);
        if (janelaFalhas.contador().get() >= limiteFalhas) {
            bloquear(request, response, chaveFalhas, limiteFalhas);
            return;
        }

        String chaveVolume = "login:" + ip;
        if (janela(chaveVolume, minutoAtual).contador().incrementAndGet() > limiteTotal) {
            bloquear(request, response, chaveVolume, limiteTotal);
            return;
        }

        filterChain.doFilter(requisicao, response);

        // 401 é o que o Spring Security devolve para senha errada. Erro de
        // validação (400) e sucesso não contam como tentativa de força bruta.
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            janelaFalhas.contador().incrementAndGet();
        }
    }

    private Janela janela(String chave, long minutoAtual) {
        return janelas.compute(chave, (k, atual) ->
            atual == null || atual.minuto() != minutoAtual ? new Janela(minutoAtual, new AtomicInteger()) : atual
        );
    }

    /**
     * Login que está sendo tentado, lido do corpo da requisição. Serve só como
     * chave de contagem, então é normalizado para minúsculas e truncado: o valor
     * vem de fora e não pode virar chave gigante no mapa.
     *
     * Corpo ilegível vira uma chave única ("?"), que é o comportamento seguro:
     * essas tentativas passam a compartilhar o mesmo contador.
     */
    private String contaTentada(byte[] corpo) {
        try {
            JsonNode json = JSON.readTree(corpo);
            String username = json.path("username").asText("");
            if (username.isBlank()) {
                return "?";
            }
            return username.toLowerCase(Locale.ROOT).substring(0, Math.min(username.length(), 100));
        } catch (IOException e) {
            return "?";
        }
    }

    private void bloquear(HttpServletRequest request, HttpServletResponse response, String identidade, int limite) throws IOException {
        LOG.warn("Rate limit excedido: {} passou de {} req/min em {} {}", identidade, limite, request.getMethod(), request.getRequestURI());
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response
            .getWriter()
            .write(
                "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Limite de requisições excedido. Tente novamente em instantes.\"}"
            );
    }

    // Só o login tem a regra dos dois tetos. Nos outros endpoints sensíveis o
    // acerto também é abuso (criar contas em massa, disparar e-mail de reset,
    // enumerar e-mails), então lá toda requisição conta.
    private boolean isLogin(String uri) {
        return uri.startsWith("/api/authenticate");
    }

    /**
     * Grupo de contagem dos endpoints não autenticados que são alvo típico de abuso:
     * registro em massa de contas, disparo de e-mails de reset e a checagem de e-mail
     * do cadastro (enumeração de contas). O login tem tratamento próprio, em isLogin.
     *
     * Devolver um grupo por endpoint, e não um rótulo único, é o que dá a cada um o
     * SEU orçamento. Enquanto dividiam a mesma chave, a checagem de e-mail - que a
     * tela dispara a cada pausa na digitação - derrubava o cadastro e o login logo
     * em seguida, e a pessoa via só "usuário ou senha incorretos".
     */
    private String grupoSensivel(String uri) {
        if (uri.startsWith("/api/register")) {
            return "registro";
        }
        if (uri.startsWith("/api/account/reset-password")) {
            return "reset";
        }
        if (uri.startsWith("/api/public/verificar-email")) {
            return "email";
        }
        return null;
    }

    // Usuário logado conta pelo login (o limite segue a conta); anônimo, pelo IP
    private String identidadeDoChamador(HttpServletRequest request) {
        return SecurityUtils.getCurrentUserLogin()
            .filter(l -> !"anonymousUser".equals(l))
            .map(l -> "user:" + l)
            .orElse("ip:" + ipDoCliente(request));
    }

    /**
     * IP do cliente. O X-Forwarded-For SÓ é considerado quando a propriedade
     * application.rate-limit.confiar-x-forwarded-for está ligada - ou seja,
     * quando existe um proxy reverso confiável na frente que SOBRESCREVE o
     * cabeçalho. Sem proxy, o cliente pode enviar qualquer valor nesse header
     * e ganhar uma identidade nova a cada requisição, anulando o rate limit.
     */
    private String ipDoCliente(HttpServletRequest request) {
        if (applicationProperties.getRateLimit().isConfiarXForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
