package br.com.digitado.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.digitado.domain.Sala;
import br.com.digitado.domain.enumeration.TipoSala;
import br.com.digitado.repository.SalaRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** O WebSocket é do jogo: conta admin não conecta, e uma sessão não inunda o servidor. */
class JogoSalaJwtInterceptorTest {

    private JogoSalaJwtInterceptor interceptorQueDecodifica(String auth) {
        JwtDecoder decoder = Mockito.mock(JwtDecoder.class);
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "HS512")
            .subject("alguem")
            .claim("auth", auth)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        Mockito.when(decoder.decode("t")).thenReturn(jwt);
        SalaRepository salas = Mockito.mock(SalaRepository.class);
        Mockito.when(salas.findById("DUE234")).thenReturn(java.util.Optional.of(new Sala().codigo("DUE234").tipo(TipoSala.UM_V_UM)));
        Mockito.when(salas.findById("TUR234")).thenReturn(java.util.Optional.of(new Sala().codigo("TUR234").tipo(TipoSala.TURMA)));
        return new JogoSalaJwtInterceptor(decoder, salas);
    }

    private static Message<byte[]> connect() {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        acc.setNativeHeader("Authorization", "Bearer t");
        acc.setSessionId("s1");
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    private static Message<byte[]> send(String sessao) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SEND);
        acc.setSessionId(sessao);
        acc.setDestination("/app/sala/ABC234/estado");
        acc.setUser(new UsernamePasswordAuthenticationToken("aluno", null, java.util.List.of()));
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    private static Message<byte[]> sendComo(String destino, String... papeis) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SEND);
        acc.setSessionId("s-" + destino);
        acc.setDestination(destino);
        acc.setUser(
            new UsernamePasswordAuthenticationToken(
                "quem",
                null,
                java.util.Arrays.stream(papeis).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList()
            )
        );
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    @Test
    void contaAdminConectaParaDuelar() {
        JogoSalaJwtInterceptor interceptor = interceptorQueDecodifica("ROLE_ADMIN,ROLE_USER");
        assertThat(interceptor.preSend(connect(), null)).isNotNull();
        // O token de verdade separa os papéis com ESPAÇO (AuthenticateController)
        assertThat(interceptorQueDecodifica("ROLE_ADMIN ROLE_USER").preSend(connect(), null)).isNotNull();
    }

    @Test
    void contaSemPapelDeJogoNaoConecta() {
        JogoSalaJwtInterceptor interceptor = interceptorQueDecodifica("");
        assertThatThrownBy(() -> interceptor.preSend(connect(), null)).isInstanceOf(MessagingException.class);
    }

    @Test
    void adminSoAgeEmDuelo() {
        JogoSalaJwtInterceptor interceptor = interceptorQueDecodifica("ROLE_USER");
        // Duelo: passa
        assertThat(interceptor.preSend(sendComo("/app/sala/DUE234/entrar", "ROLE_ADMIN", "ROLE_USER"), null)).isNotNull();
        assertThat(interceptor.preSend(sendComo("/topic/sala/DUE234", "ROLE_ADMIN"), null)).isNotNull();
        // Sala de turma, sala inexistente e destino que não é de sala: descartados
        assertThat(interceptor.preSend(sendComo("/app/sala/TUR234/entrar", "ROLE_ADMIN", "ROLE_USER"), null)).isNull();
        assertThat(interceptor.preSend(sendComo("/topic/sala/TUR234", "ROLE_ADMIN"), null)).isNull();
        assertThat(interceptor.preSend(sendComo("/app/sala/NAOHA2/estado", "ROLE_ADMIN"), null)).isNull();
        assertThat(interceptor.preSend(sendComo("/topic/qualquer", "ROLE_ADMIN"), null)).isNull();
        // O jogador continua em qualquer sala
        assertThat(interceptor.preSend(sendComo("/app/sala/TUR234/entrar", "ROLE_USER"), null)).isNotNull();
    }

    @Test
    void codigoDaSalaNoDestino() {
        assertThat(JogoSalaJwtInterceptor.salaDoDestino("/app/sala/ABC234/responder")).isEqualTo("ABC234");
        assertThat(JogoSalaJwtInterceptor.salaDoDestino("/user/queue/sala/ABC234/feedback")).isEqualTo("ABC234");
        assertThat(JogoSalaJwtInterceptor.salaDoDestino("/topic/sala/ABC234")).isEqualTo("ABC234");
        assertThat(JogoSalaJwtInterceptor.salaDoDestino("/topic/outra")).isNull();
    }

    @Test
    void jogadorConecta() {
        JogoSalaJwtInterceptor interceptor = interceptorQueDecodifica("ROLE_USER");
        assertThat(interceptor.preSend(connect(), null)).isNotNull();
    }

    @Test
    void mensagensAcimaDoTetoDaSessaoSaoDescartadas() {
        JogoSalaJwtInterceptor interceptor = interceptorQueDecodifica("ROLE_USER");
        int aceitas = 0;
        for (int i = 0; i < JogoSalaJwtInterceptor.MAX_MENSAGENS + 20; i++) {
            if (interceptor.preSend(send("flood"), null) != null) aceitas++;
        }
        // Na virada da janela de 10 s podem passar algumas a mais, nunca o flood inteiro
        assertThat(aceitas).isLessThan(JogoSalaJwtInterceptor.MAX_MENSAGENS * 2);
        assertThat(aceitas).isGreaterThanOrEqualTo(JogoSalaJwtInterceptor.MAX_MENSAGENS);
        // Outra sessão não herda o teto
        assertThat(interceptor.preSend(send("outra"), null)).isNotNull();
    }
}
