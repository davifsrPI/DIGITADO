package br.com.digitado.config;

import br.com.digitado.web.websocket.JogoSalaJwtInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JogoSalaJwtInterceptor jwtInterceptor;

    @Value("${jhipster.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Value("${jhipster.cors.allowed-origin-patterns:}")
    private List<String> allowedOriginPatterns;

    public WebSocketConfig(JogoSalaJwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint("/websocket/sala");
        List<String> origins = allowedOrigins.stream().filter(s -> !s.isBlank()).toList();
        List<String> patterns = allowedOriginPatterns.stream().filter(s -> !s.isBlank()).toList();
        if (!origins.isEmpty()) {
            endpoint.setAllowedOrigins(origins.toArray(new String[0]));
        } else if (!patterns.isEmpty()) {
            endpoint.setAllowedOriginPatterns(patterns.toArray(new String[0]));
        } else {
            endpoint.setAllowedOriginPatterns("*");
        }
        endpoint.withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtInterceptor);
    }
}
