package br.com.digitado.web.filter;

import br.com.digitado.config.ApplicationProperties;
import br.com.digitado.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * Limite de requisições por identidade em /api/** — proteção contra abuso e ataques
 * (flood de requisições, força bruta de senha, scraping da API).
 *
 * Como funciona:
 * - Janela fixa de 1 minuto por identidade. Usuário autenticado é identificado pelo
 *   LOGIN (o limite acompanha a conta, não a máquina); anônimo é identificado pelo IP.
 * - Endpoints de autenticação (/api/authenticate) têm limite bem mais rígido e são
 *   sempre contados por IP — senha errada em sequência é o padrão típico de força bruta.
 * - Estourou o limite: HTTP 429 (Too Many Requests) com Retry-After, corpo JSON e
 *   log WARN com a identidade (o requestId já sai via MDC para rastreabilidade).
 * - Limites configuráveis por ambiente em application.yml ("application.rate-limit").
 *
 * Roda DEPOIS da cadeia de segurança (ordem 0 > -100 do Spring Security), para que o
 * JWT já tenha sido processado e o login esteja disponível no SecurityContext.
 *
 * Implementação em memória (ConcurrentHashMap) — suficiente para uma instância única,
 * sem dependência externa. Se o app escalar horizontalmente, trocar por um contador
 * compartilhado (ex.: Redis) mantendo este filtro como ponto único de aplicação.
 */
@Component
@Order(0)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

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
        boolean sensivel = isEndpointSensivel(request.getRequestURI());

        int limite = sensivel
            ? applicationProperties.getRateLimit().getAutenticacaoPorMinuto()
            : applicationProperties.getRateLimit().getRequisicoesPorMinuto();

        // Endpoints sensíveis contam sempre por IP (quem tenta senha/registro
        // ainda não tem login); o resto conta pela conta logada, ou pelo IP se anônimo
        String identidade = sensivel ? "ip:" + ipDoCliente(request) : identidadeDoChamador(request);

        long minutoAtual = System.currentTimeMillis() / 60000;
        Janela janela = janelas.compute(identidade, (k, atual) ->
            atual == null || atual.minuto() != minutoAtual ? new Janela(minutoAtual, new AtomicInteger()) : atual
        );

        if (janela.contador().incrementAndGet() > limite) {
            LOG.warn(
                "Rate limit excedido: {} passou de {} req/min em {} {}",
                identidade,
                limite,
                request.getMethod(),
                request.getRequestURI()
            );
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response
                .getWriter()
                .write(
                    "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Limite de requisições excedido. Tente novamente em instantes.\"}"
                );
            return;
        }

        // Limpeza oportunista: janelas de minutos passados são descartadas para o
        // mapa não crescer indefinidamente
        if (janelas.size() > 10_000) {
            janelas.entrySet().removeIf(e -> e.getValue().minuto() != minutoAtual);
        }

        filterChain.doFilter(request, response);
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
     * application.rate-limit.confiar-x-forwarded-for está ligada — ou seja,
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
