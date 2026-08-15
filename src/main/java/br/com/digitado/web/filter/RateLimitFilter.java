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

        boolean sensivel = isEndpointSensivel(request.getRequestURI());

        int limite = sensivel
            ? applicationProperties.getRateLimit().getAutenticacaoPorMinuto()
            : applicationProperties.getRateLimit().getRequisicoesPorMinuto();

        // Endpoints sensíveis contam sempre por IP (quem tenta registro/reset ainda
        // não tem login); o resto conta pela conta logada, ou pelo IP se anônimo.
        // Aqui toda requisição conta, inclusive a bem-sucedida: no cadastro o abuso
        // é justamente criar muita conta COM sucesso, então contar só falha
        // deixaria passar o que se quer barrar.
        String identidade = sensivel ? "ip:" + ipDoCliente(request) : identidadeDoChamador(request);

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

    // Endpoints não autenticados que são alvo típico de força bruta/flood:
    // login (senha), registro em massa de contas, disparo de e-mails de reset
    // e a checagem de e-mail do cadastro (enumeração de contas existentes)
    private boolean isEndpointSensivel(String uri) {
        return (
            uri.startsWith("/api/authenticate") ||
            uri.startsWith("/api/register") ||
            uri.startsWith("/api/account/reset-password") ||
            uri.startsWith("/api/public/verificar-email")
        );
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
