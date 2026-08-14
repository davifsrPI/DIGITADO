package br.com.digitado.web.websocket;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

// Interceptor do canal STOMP que autentica o usuário via JWT no momento da conexão WebSocket.
// Funciona como o equivalente do filtro de segurança HTTP, mas para mensagens STOMP.
@Component
public class JogoSalaJwtInterceptor implements ChannelInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaJwtInterceptor.class);

    private final JwtDecoder jwtDecoder;

    public JogoSalaJwtInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // CONNECT: handshake do STOMP - o JWT é OBRIGATÓRIO. Sem token válido a
        // conexão é rejeitada aqui (frame ERROR + fechamento), impedindo inclusive
        // SUBSCRIBE anônimo nos tópicos da sala (o estado do jogo é broadcast neles).
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                LOG.warn("WebSocket CONNECT sem token - conexão rejeitada");
                throw new MessagingException("Autenticação obrigatória para conectar ao WebSocket");
            }
            String token = authHeader.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(token);

                // O campo "auth" do JWT contém as roles separadas por vírgula (ex: "ROLE_USER,ROLE_ADMIN")
                String authClaim = jwt.getClaimAsString("auth");
                List<GrantedAuthority> authorities = List.of();
                if (authClaim != null && !authClaim.isBlank()) {
                    authorities = List.of(authClaim.split(","))
                        .stream()
                        .map(String::trim)
                        .filter(a -> !a.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .<GrantedAuthority>map(a -> a)
                        .toList();
                }

                // Associa o usuário autenticado à sessão STOMP para que os controllers
                // possam acessar principal.getName() e obter o login
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, authorities);
                accessor.setUser(auth);
            } catch (Exception e) {
                LOG.warn("WebSocket JWT inválido - conexão rejeitada: {}", e.getMessage());
                throw new MessagingException("Token inválido ou expirado");
            }
        }

        // Defesa em profundidade: nenhuma inscrição ou mensagem sem sessão autenticada
        // (não deveria acontecer, já que o CONNECT sem token é rejeitado acima)
        if ((StompCommand.SUBSCRIBE.equals(accessor.getCommand()) || StompCommand.SEND.equals(accessor.getCommand()))) {
            if (accessor.getUser() == null) {
                LOG.warn("WebSocket {} sem sessão autenticada - bloqueado", accessor.getCommand());
                throw new MessagingException("Sessão WebSocket não autenticada");
            }
        }
        return message;
    }
}
