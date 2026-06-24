package br.com.digitado.web.filter;

import br.com.digitado.service.OnlineUsersService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OnlineUsersInterceptor implements HandlerInterceptor {

    private final OnlineUsersService onlineUsersService;

    public OnlineUsersInterceptor(OnlineUsersService onlineUsersService) {
        this.onlineUsersService = onlineUsersService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            onlineUsersService.markOnline(auth.getName());
        }
        return true;
    }
}
