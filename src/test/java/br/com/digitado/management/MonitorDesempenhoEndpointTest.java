package br.com.digitado.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A leitura do painel "Desempenho" (Administração > Métricas). */
class MonitorDesempenhoEndpointTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MonitorDesempenhoEndpoint endpoint = new MonitorDesempenhoEndpoint(registry);

    private void requisicoes(String uri, String status, int quantas, long ms) {
        Timer timer = Timer.builder("http.server.requests").tags("uri", uri, "status", status).register(registry);
        for (int i = 0; i < quantas; i++) {
            timer.record(Duration.ofMillis(ms));
        }
    }

    @Test
    @DisplayName("requisições HTTP somam todas as rotas, menos as do próprio painel")
    void requisicoesSemOProprioPainel() {
        requisicoes("/api/salas", "200", 3, 10);
        requisicoes("/api/individual/responder", "500", 2, 100);
        // o painel perguntando duas vezes por segundo não pode virar "tráfego"
        requisicoes(MonitorDesempenhoEndpoint.URI_DO_PAINEL, "200", 50, 1);

        MonitorDesempenho.Http http = endpoint.http();

        assertThat(http.requisicoes()).isEqualTo(5);
        assertThat(http.erros()).isEqualTo(2);
        assertThat(http.tempoTotalMs()).isEqualTo(230.0);
    }

    @Test
    void semTrafegoNenhumEhZero() {
        assertThat(endpoint.http()).isEqualTo(new MonitorDesempenho.Http(0, 0.0, 0));
    }

    @Test
    @DisplayName("CPU: fração do MXBean vira % com uma casa; negativo é 'não sei', não zero")
    void percentualDeCpu() {
        assertThat(MonitorDesempenhoEndpoint.percentual(0.1234)).isEqualTo(12.3);
        assertThat(MonitorDesempenhoEndpoint.percentual(0)).isEqualTo(0.0);
        assertThat(MonitorDesempenhoEndpoint.percentual(1.2)).isEqualTo(100.0);
        assertThat(MonitorDesempenhoEndpoint.percentual(-1)).isNull();
        assertThat(MonitorDesempenhoEndpoint.percentual(Double.NaN)).isNull();
    }

    @Test
    @DisplayName("a leitura completa traz números coerentes da JVM que está rodando o teste")
    void leituraCompleta() {
        MonitorDesempenho leitura = endpoint.ler();

        assertThat(leitura.instante()).isPositive();
        assertThat(leitura.cpu().processadores()).isPositive();

        MonitorDesempenho.MemoriaMaquina maquina = leitura.memoria();
        assertThat(maquina.total()).isPositive();
        assertThat(maquina.emUso() + maquina.disponivel()).isEqualTo(maquina.total());

        MonitorDesempenho.MemoriaJvm jvm = leitura.memoriaJvm();
        assertThat(jvm.heapUsada()).isPositive().isLessThanOrEqualTo(jvm.heapConfirmada());
        assertThat(jvm.classesCarregadas()).isPositive();
        assertThat(jvm.areas()).anyMatch(MonitorDesempenho.AreaMemoria::heap).anyMatch(a -> !a.heap());

        MonitorDesempenho.Threads threads = leitura.threads();
        assertThat(threads.ativas()).isPositive().isLessThanOrEqualTo(threads.pico());
        assertThat(threads.porEstado()).containsKeys("RUNNABLE", "WAITING", "TIMED_WAITING", "BLOCKED");
        // quem está lendo é uma thread rodando
        assertThat(threads.porEstado().get("RUNNABLE")).isPositive();

        assertThat(leitura.coletorDeLixo().coletores()).isNotEmpty();
        assertThat(leitura.coletorDeLixo().coletas()).isEqualTo(
            leitura.coletorDeLixo().coletores().stream().mapToLong(MonitorDesempenho.Coletor::coletas).sum()
        );

        assertThat(leitura.sistema().pid()).isEqualTo(ProcessHandle.current().pid());
        assertThat(leitura.sistema().tempoAtividadeMs()).isPositive();
        assertThat(leitura.sistema().nomeSistema()).isNotBlank();
    }
}
