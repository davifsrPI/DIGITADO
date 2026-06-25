package br.com.digitado.config;

import br.com.digitado.web.websocket.JogoSalaJwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Configuração do WebSocket com STOMP — habilita comunicação em tempo real entre servidor e clientes
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JogoSalaJwtInterceptor jwtInterceptor;

    public WebSocketConfig(JogoSalaJwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
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

    // Registra o endpoint SockJS em /websocket/sala
    // setAllowedOriginPatterns("*") aceita conexões de qualquer origem —
    // a segurança real é feita pelo JWT no interceptor abaixo
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/websocket/sala").setAllowedOriginPatterns("*").withSockJS();
    }

    // Registra o interceptor que lê o token JWT do cabeçalho da conexão STOMP
    // e autentica o usuário antes de qualquer mensagem ser processada
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtInterceptor);
    }
}
