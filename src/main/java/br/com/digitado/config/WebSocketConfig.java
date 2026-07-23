package br.com.digitado.config;

import br.com.digitado.web.websocket.JogoSalaJwtInterceptor;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Configuração do WebSocket com STOMP — habilita comunicação em tempo real entre servidor e clientes
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JogoSalaJwtInterceptor jwtInterceptor;
    private final ApplicationProperties applicationProperties;

    public WebSocketConfig(JogoSalaJwtInterceptor jwtInterceptor, ApplicationProperties applicationProperties) {
        this.jwtInterceptor = jwtInterceptor;
        this.applicationProperties = applicationProperties;
    }

    // Define os prefixos do broker de mensagens:
    // /topic → broadcasts para todos os inscritos num tópico (ex: placar da sala)
    // /queue → mensagens privadas para um usuário específico (ex: feedback individual)
    // /app  → prefixo dos endpoints da aplicação (recebe mensagens do cliente)
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    // Registra o endpoint SockJS em /websocket/sala.
    // Origens aceitas no handshake vêm de application.websocket.allowed-origins:
    // "*" no dev (front em localhost:9000), VAZIO em produção — sem padrão
    // registrado vale o default do Spring, que só aceita a MESMA origem do site.
    // Defesa em profundidade: o JWT no interceptor abaixo continua obrigatório.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        StompWebSocketEndpointRegistration endpoint = registry.addEndpoint("/websocket/sala");
        String origens = applicationProperties.getWebsocket().getAllowedOrigins();
        if (origens != null && !origens.isBlank()) {
            String[] padroes = Arrays.stream(origens.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new);
            if (padroes.length > 0) {
                endpoint.setAllowedOriginPatterns(padroes);
            }
        }
        endpoint.withSockJS();
    }

    // Registra o interceptor que lê o token JWT do cabeçalho da conexão STOMP
    // e autentica o usuário antes de qualquer mensagem ser processada
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtInterceptor);
    }
}
