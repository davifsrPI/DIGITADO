package br.com.digitado.web.websocket;

import br.com.digitado.security.AuthoritiesConstants;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

                // O WebSocket é só do JOGO, e a conta de administração não joga (ver
                // SecurityConfiguration.somenteJogador): com ela o admin entrava em
                // qualquer sala com o poder de professor
                boolean admin = authorities.stream().anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getAuthority()));
                boolean jogador = authorities.stream().anyMatch(a -> AuthoritiesConstants.USER.equals(a.getAuthority()));
                if (admin || !jogador) {
                    LOG.warn("WebSocket CONNECT de conta que não é de jogador ({}) - conexão rejeitada", jwt.getSubject());
                    throw new MessagingException("A conta de administração não participa dos jogos");
                }

                // Associa o usuário autenticado à sessão STOMP para que os controllers
                // possam acessar principal.getName() e obter o login
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, authorities);
                accessor.setUser(auth);
            } catch (MessagingException e) {
                throw e;
            } catch (Exception e) {
                LOG.warn("WebSocket JWT inválido - conexão rejeitada: {}", e.getMessage());
                throw new MessagingException("Token inválido ou expirado");
            }
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand()) && accessor.getSessionId() != null) {
            janelas.remove(accessor.getSessionId());
        }

        // Defesa em profundidade: nenhuma inscrição ou mensagem sem sessão autenticada
        // (não deveria acontecer, já que o CONNECT sem token é rejeitado acima)
        if ((StompCommand.SUBSCRIBE.equals(accessor.getCommand()) || StompCommand.SEND.equals(accessor.getCommand()))) {
            if (accessor.getUser() == null) {
                LOG.warn("WebSocket {} sem sessão autenticada - bloqueado", accessor.getCommand());
                throw new MessagingException("Sessão WebSocket não autenticada");
            }
            // O RateLimitFilter só cobre /api/**: sem este teto, uma única conexão
            // mandava milhares de mensagens por segundo (cada "estado" monta o placar
            // inteiro da sala). Mensagem acima do teto é DESCARTADA, a conexão segue.
            if (excedeuLimite(accessor.getSessionId())) {
                LOG.warn(
                    "WebSocket: sessão de {} passou de {} mensagens em {}s - mensagem descartada",
                    nomeDe(accessor),
                    MAX_MENSAGENS,
                    JANELA_MS / 1000
                );
                return null;
            }
        }
        return message;
    }

    // Teto de mensagens (SEND + SUBSCRIBE) por sessão a cada janela. Uma tela de jogo
    // manda poucas por rodada (entrar, responder, pedir o estado); o professor, mais
    // algumas (iniciar, próxima, pausar). 40 a cada 10 s é folga de sobra para isso.
    static final int MAX_MENSAGENS = 40;
    static final long JANELA_MS = 10_000;

    private record Janela(long inicio, AtomicInteger contador) {}

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    boolean excedeuLimite(String sessaoId) {
        if (sessaoId == null) {
            return false;
        }
        long agora = System.currentTimeMillis();
        long janelaAtual = agora / JANELA_MS;
        // Limpeza oportunista: sessões que caíram sem DISCONNECT não ficam para sempre
        if (janelas.size() > 10_000) {
            janelas.entrySet().removeIf(e -> e.getValue().inicio() != janelaAtual);
        }
        Janela janela = janelas.compute(sessaoId, (k, atual) ->
            atual == null || atual.inicio() != janelaAtual ? new Janela(janelaAtual, new AtomicInteger()) : atual
        );
        return janela.contador().incrementAndGet() > MAX_MENSAGENS;
    }

    private static String nomeDe(StompHeaderAccessor accessor) {
        return accessor.getUser() != null ? accessor.getUser().getName() : "?";
    }
}
