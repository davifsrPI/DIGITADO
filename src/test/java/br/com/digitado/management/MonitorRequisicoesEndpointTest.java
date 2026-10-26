package br.com.digitado.management;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.management.MonitorRequisicoes.ErroHttp;
import br.com.digitado.management.MonitorRequisicoes.Rota;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * A tabela "Requisições por API" e a lista de erros recentes: os timers do
 * Micrometer viram uma linha por método + rota, e os erros saem da observação de
 * cada requisição.
 */
class MonitorRequisicoesEndpointTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RegistroErrosHttp erros = new RegistroErrosHttp();
    private final MonitorRequisicoesEndpoint endpoint = new MonitorRequisicoesEndpoint(registry, erros);

    private void requisicoes(String metodo, String uri, String status, String excecao, int quantas, long ms) {
        Timer timer = Timer.builder("http.server.requests")
            .tag("method", metodo)
            .tag("uri", uri)
            .tag("status", status)
            .tag("exception", excecao)
            .register(registry);
        for (int i = 0; i < quantas; i++) {
            timer.record(Duration.ofMillis(ms));
        }
    }

    @Test
    void juntaOsTimersNumaLinhaPorMetodoERota() {
        requisicoes("GET", "/api/salas/{codigo}", "200", "none", 6, 10);
        requisicoes("GET", "/api/salas/{codigo}", "404", "none", 2, 5);
        requisicoes("GET", "/api/salas/{codigo}", "500", "IllegalStateException", 1, 300);
        requisicoes("POST", "/api/salas", "201", "none", 3, 20);
        // as perguntas dos próprios painéis ficam de fora
        requisicoes("GET", MonitorDesempenhoEndpoint.URI_DO_PAINEL, "200", "none", 50, 1);
        requisicoes("GET", MonitorRequisicoesEndpoint.URI_DO_PAINEL, "200", "none", 50, 1);

        List<Rota> rotas = endpoint.rotas();

        assertThat(rotas).extracting(Rota::uri).containsExactly("/api/salas/{codigo}", "/api/salas");
        Rota sala = rotas.get(0);
        assertThat(sala.metodo()).isEqualTo("GET");
        assertThat(sala.requisicoes()).isEqualTo(9);
        assertThat(sala.sucesso()).isEqualTo(6);
        assertThat(sala.erros4xx()).isEqualTo(2);
        assertThat(sala.erros5xx()).isEqualTo(1);
        assertThat(sala.tempoTotalMs()).isEqualTo(370.0);
        assertThat(sala.maximoMs()).isEqualTo(300.0);
        assertThat(sala.porStatus()).containsEntry("200", 6L).containsEntry("404", 2L).containsEntry("500", 1L);
        assertThat(sala.excecoes()).containsOnlyKeys("IllegalStateException");
    }

    @Test
    void semRequisicaoNenhumaATabelaVemVazia() {
        assertThat(endpoint.ler().rotas()).isEmpty();
        assertThat(endpoint.ler().errosRecentes()).isEmpty();
    }

    private ServerRequestObservationContext observacao(String metodo, String caminho, int status) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, caminho);
        request.setQueryString("token=segredo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
        context.setPathPattern("/api/salas/{codigo}");
        return context;
    }

    @Test
    void guardaOErroComCausaRaizEPilhaMasNaoAQueryString() {
        ServerRequestObservationContext context = observacao("GET", "/api/salas/ABC123", 500);
        context.setError(new IllegalStateException("falhou ao montar", new IllegalArgumentException("código inválido")));

        erros.onStart(context);
        erros.onStop(context);

        ErroHttp erro = erros.recentes().get(0);
        assertThat(erro.metodo()).isEqualTo("GET");
        assertThat(erro.rota()).isEqualTo("/api/salas/{codigo}");
        assertThat(erro.caminho()).isEqualTo("/api/salas/ABC123");
        assertThat(erro.status()).isEqualTo(500);
        assertThat(erro.duracaoMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(erro.excecao()).isEqualTo(IllegalStateException.class.getName());
        assertThat(erro.mensagem()).isEqualTo("falhou ao montar");
        assertThat(erro.causaRaiz()).isEqualTo(IllegalArgumentException.class.getName() + ": código inválido");
        // a linha deste teste está na pilha, marcada como código do DIGITADO
        assertThat(erro.pilha()).isNotEmpty().hasSizeLessThanOrEqualTo(RegistroErrosHttp.LINHAS_DA_PILHA);
        assertThat(erro.pilha().get(0)).startsWith("» br.com.digitado.management.MonitorRequisicoesEndpointTest");
        assertThat(erro.toString()).doesNotContain("segredo");
    }

    @Test
    void excecaoTratadaPeloExceptionHandlerTambemEntra() {
        ServerRequestObservationContext context = observacao("POST", "/api/salas", 400);
        context.getCarrier().setAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE, new IllegalArgumentException("nome vazio"));

        erros.onStop(context);

        assertThat(erros.recentes().get(0).excecao()).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(erros.recentes().get(0).mensagem()).isEqualTo("nome vazio");
    }

    @Test
    void guardaAMensagemDeAntesDeOExceptionTranslatorReescreverOCorpo() {
        ServerRequestObservationContext context = observacao("GET", "/api/admin/desempenho", 400);
        ProblemDetail corpo = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        corpo.setTitle("Tipo de erro desconhecido: X");
        ErrorResponseException ex = new ErrorResponseException(HttpStatus.BAD_REQUEST, corpo, null);

        assertThat(erros.resolveException(context.getCarrier(), context.getResponse(), null, ex)).isNull();
        // o que o ExceptionTranslator faz depois, antes de a observação terminar
        corpo.setTitle("Bad Request");
        erros.onStop(context);

        assertThat(erros.recentes().get(0).excecao()).isEqualTo(ErrorResponseException.class.getName());
        assertThat(erros.recentes().get(0).mensagem()).isEqualTo("Tipo de erro desconhecido: X");
    }

    @Test
    void respostaDeSucessoEPerguntaDoPainelNaoEntram() {
        erros.onStop(observacao("GET", "/api/salas/ABC123", 200));
        erros.onStop(observacao("GET", MonitorRequisicoesEndpoint.URI_DO_PAINEL, 401));
        erros.onStop(observacao("GET", MonitorDesempenhoEndpoint.URI_DO_PAINEL, 401));

        assertThat(erros.recentes()).isEmpty();
        assertThat(erros.totalRegistrado()).isZero();
    }

    @Test
    void guardaSoOsUltimosDoMaisNovoParaOMaisAntigo() {
        for (int i = 0; i < RegistroErrosHttp.CAPACIDADE + 5; i++) {
            erros.onStop(observacao("GET", "/api/x/" + i, 404));
        }

        assertThat(erros.recentes()).hasSize(RegistroErrosHttp.CAPACIDADE);
        assertThat(erros.recentes().get(0).caminho()).isEqualTo("/api/x/" + (RegistroErrosHttp.CAPACIDADE + 4));
        assertThat(erros.totalRegistrado()).isEqualTo(RegistroErrosHttp.CAPACIDADE + 5);
    }
}
