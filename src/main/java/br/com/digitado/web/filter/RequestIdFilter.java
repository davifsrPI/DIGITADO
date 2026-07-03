package br.com.digitado.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de rastreabilidade: garante que TODA requisição tenha um request ID único.
 *
 * - Aceita um X-Request-ID vindo de fora (gateway/proxy/cliente) para correlação
 *   entre serviços; caso contrário gera um UUID.
 * - Coloca o ID no MDC ("requestId"), então ele aparece automaticamente em todos os
 *   logs da requisição (no padrão do console e nos logs JSON via logstash-encoder).
 * - Devolve o ID no header da resposta, para que o cliente possa reportar o ID
 *   ao abrir um chamado e o suporte consiga filtrar os logs exatos.
 *
 * Roda com a maior precedência possível para que até os logs dos filtros de
 * segurança já carreguem o ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    // Limites para não aceitar valores maliciosos/lixo vindos do cliente
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Sempre limpa o MDC — a thread volta para o pool e será reutilizada
            MDC.remove(MDC_KEY);
        }
    }

    // Aceita apenas caracteres seguros (evita log injection via header)
    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() > MAX_LENGTH || !cleaned.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return cleaned;
    }
}
