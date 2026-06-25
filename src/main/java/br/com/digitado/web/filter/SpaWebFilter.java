package br.com.digitado.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

// Filtro que suporta o roteamento do React (Single Page Application):
// qualquer rota do frontend sem extensão de arquivo é redirecionada para o index.html,
// permitindo que o React Router cuide da navegação no lado do cliente
public class SpaWebFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        // Deixa passar normalmente: rotas de API, gerenciamento, docs, websocket e arquivos com extensão (js, css, png...)
        // Tudo o mais (rotas do React como /lobby, /sala/ABC123) é encaminhado ao index.html
        if (
            !path.startsWith("/api") &&
            !path.startsWith("/management") &&
            !path.startsWith("/v3/api-docs") &&
            !path.startsWith("/websocket") &&
            !path.contains(".") &&
            path.matches("/(.*)")
        ) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
