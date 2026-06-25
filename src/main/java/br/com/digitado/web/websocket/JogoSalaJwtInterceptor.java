package br.com.digitado.web.websocket;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
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

        // Só processa o frame CONNECT — é o handshake inicial do STOMP onde o token é enviado
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            // Extrai o token Bearer do cabeçalho e tenta decodificar o JWT
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
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
                    // Token inválido ou expirado — a conexão prossegue sem autenticação,
                    // mas os controllers vão rejeitar por principal == null
                    LOG.warn("WebSocket JWT inválido: {}", e.getMessage());
                }
            }
        }
        return message;
    }
}
