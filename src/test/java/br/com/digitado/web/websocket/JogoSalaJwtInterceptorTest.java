package br.com.digitado.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        return new JogoSalaJwtInterceptor(decoder);
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

    @Test
    void contaAdminNaoConectaNoJogo() {
        JogoSalaJwtInterceptor interceptor = interceptorQueDecodifica("ROLE_ADMIN,ROLE_USER");
        assertThatThrownBy(() -> interceptor.preSend(connect(), null)).isInstanceOf(MessagingException.class);
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
