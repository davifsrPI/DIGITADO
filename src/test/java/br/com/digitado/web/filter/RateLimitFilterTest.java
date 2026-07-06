package br.com.digitado.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.config.ApplicationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Testes do limite de requisições (proteção contra abuso):
 * dentro do limite passa, acima retorna 429; autenticação tem teto mais rígido;
 * identidades diferentes não interferem entre si.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setup() {
        ApplicationProperties props = new ApplicationProperties();
        props.getRateLimit().setRequisicoesPorMinuto(5);
        props.getRateLimit().setAutenticacaoPorMinuto(2);
        filter = new RateLimitFilter(props);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void teardown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse chamar(String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void permiteAteOLimiteEBloqueiaAcima() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(chamar("/api/palavras", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse bloqueada = chamar("/api/palavras", "10.0.0.1");
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("60");
        assertThat(bloqueada.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void autenticacaoTemLimiteMaisRigido() throws Exception {
        // Limite de autenticação = 2: a terceira tentativa de senha já é barrada
        assertThat(chamar("/api/authenticate", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(chamar("/api/authenticate", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(chamar("/api/authenticate", "10.0.0.2").getStatus()).isEqualTo(429);
    }

    @Test
    void identidadesDiferentesNaoInterferem() throws Exception {
        // Esgota o limite do primeiro IP
        for (int i = 0; i < 6; i++) {
            chamar("/api/palavras", "10.0.0.3");
        }
        assertThat(chamar("/api/palavras", "10.0.0.3").getStatus()).isEqualTo(429);
        // Outro IP continua liberado
        assertThat(chamar("/api/palavras", "10.0.0.4").getStatus()).isEqualTo(200);
    }

    @Test
    void usuarioLogadoContaPeloLoginENaoPeloIp() throws Exception {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("aluno-teste", null, java.util.List.of()));
        // Esgota o limite da conta, mesmo trocando de IP a cada chamada
        for (int i = 0; i < 5; i++) {
            assertThat(chamar("/api/palavras", "10.0.1." + i).getStatus()).isEqualTo(200);
        }
        assertThat(chamar("/api/palavras", "10.0.1.99").getStatus()).isEqualTo(429);
    }

    @Test
    void naoFiltraForaDaApi() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(chamar("/management/health", "10.0.0.5").getStatus()).isEqualTo(200);
        }
    }
}
