package br.com.digitado.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.config.ApplicationProperties;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.security.TentativasCodigoSala;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
    private SalaRepository salaRepository;

    @BeforeEach
    void setup() {
        ApplicationProperties props = new ApplicationProperties();
        props.getRateLimit().setRequisicoesPorMinuto(5);
        props.getRateLimit().setAutenticacaoPorMinuto(2);
        props.getRateLimit().setCodigosErradosPorMinuto(3);
        salaRepository = Mockito.mock(SalaRepository.class);
        // Só a sala ABCDEF existe
        Mockito.when(salaRepository.existsById(Mockito.anyString())).thenAnswer(i -> "ABCDEF".equals(i.getArgument(0)));
        filter = new RateLimitFilter(props, new TentativasCodigoSala(props), salaRepository);
        SecurityContextHolder.clearContext();
    }

    // GET numa rota de sala, respondendo como o SalaResource: 404 para sala que não existe
    private MockHttpServletResponse buscarSala(String codigo) throws Exception {
        String uri = "/api/salas/" + codigo;
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("10.0.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) ->
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus("ABCDEF".equals(codigo) ? 200 : 404)
        );
        return response;
    }

    @Test
    void chutarCodigosDeSalaEmSerieBloqueiaAConta() throws Exception {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("chutador", null, java.util.List.of()));
        // O código certo não gasta nada
        assertThat(buscarSala("ABCDEF").getStatus()).isEqualTo(200);
        assertThat(buscarSala("ZZZZZ2").getStatus()).isEqualTo(404);
        assertThat(buscarSala("ZZZZZ3").getStatus()).isEqualTo(404);
        assertThat(buscarSala("ZZZZZ4").getStatus()).isEqualTo(404);
        // Esgotou os 3 códigos errados: nem o certo responde mais neste minuto,
        // senão o bloqueio entregaria qual chute era a sala de verdade
        assertThat(buscarSala("ZZZZZ5").getStatus()).isEqualTo(429);
        assertThat(buscarSala("ABCDEF").getStatus()).isEqualTo(429);
    }

    @Test
    void outraContaNaoHerdaOBloqueioDosChutes() throws Exception {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("chutador2", null, java.util.List.of()));
        for (int i = 0; i < 4; i++) {
            buscarSala("YYYYY" + (i + 2));
        }
        assertThat(buscarSala("ABCDEF").getStatus()).isEqualTo(429);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("colega", null, java.util.List.of()));
        assertThat(buscarSala("ABCDEF").getStatus()).isEqualTo(200);
    }

    @Test
    void codigoDaSalaNaUrl() {
        assertThat(RateLimitFilter.codigoSalaDaUrl("/api/salas/ABCDEF")).isEqualTo("ABCDEF");
        assertThat(RateLimitFilter.codigoSalaDaUrl("/api/salas/ABCDEF/identificacao")).isEqualTo("ABCDEF");
        assertThat(RateLimitFilter.codigoSalaDaUrl("/api/salas")).isNull();
        assertThat(RateLimitFilter.codigoSalaDaUrl("/api/salas/codigo/novo")).isNull();
        assertThat(RateLimitFilter.codigoSalaDaUrl("/api/salas/1v1/publicas")).isNull();
        assertThat(RateLimitFilter.codigoSalaDaUrl("/api/palavras/1")).isNull();
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

    // Login que termina em 401 (senha errada), como o Spring Security responde
    private MockHttpServletResponse loginComSenhaErrada(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/authenticate");
        request.setRequestURI("/api/authenticate");
        request.setRemoteAddr(ip);
        request.setContentType("application/json");
        request.setContent("{\"username\":\"aluno\",\"password\":\"errada\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));
        return response;
    }

    @Test
    void autenticacaoTemLimiteMaisRigido() throws Exception {
        // Limite de autenticação = 2 senhas ERRADAS: a terceira tentativa já é barrada.
        // (Desde que o login passou a contar só a senha errada, o acerto não gasta
        // o limite - por isso as tentativas aqui terminam em 401.)
        assertThat(loginComSenhaErrada("10.0.0.2").getStatus()).isEqualTo(401);
        assertThat(loginComSenhaErrada("10.0.0.2").getStatus()).isEqualTo(401);
        assertThat(loginComSenhaErrada("10.0.0.2").getStatus()).isEqualTo(429);
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
