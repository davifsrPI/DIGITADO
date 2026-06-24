package br.com.digitado.config;

import br.com.digitado.web.filter.OnlineUsersInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfiguration implements WebMvcConfigurer {

    private final OnlineUsersInterceptor onlineUsersInterceptor;

    public MvcConfiguration(OnlineUsersInterceptor onlineUsersInterceptor) {
        this.onlineUsersInterceptor = onlineUsersInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onlineUsersInterceptor).addPathPatterns("/api/**");
    }
}
