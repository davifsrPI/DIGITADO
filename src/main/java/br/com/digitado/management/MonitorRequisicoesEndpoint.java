package br.com.digitado.management;

import br.com.digitado.management.MonitorRequisicoes.Rota;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * GET /management/requisicoes: as requisições de cada API e os últimos erros, para a
 * tabela "Requisições por API" do painel Desempenho (Administração > Métricas).
 *
 * Separado de /management/desempenho de propósito: a leitura do gráfico vai a cada
 * meio segundo e precisa ser leve; esta é maior (uma linha por rota, mais os erros
 * com pilha) e só é pedida enquanto o admin está olhando o item HTTP.
 *
 * Mesmas regras do outro endpoint: fica no actuator (/management/** é só de ADMIN
 * e não passa pelo limite de 100 requisições por minuto de /api) e as perguntas
 * dos dois painéis não entram na conta.
 */
@Component
@Endpoint(id = "requisicoes")
public class MonitorRequisicoesEndpoint {

    static final String URI_DO_PAINEL = "/management/requisicoes";

    private static final String METRICA_HTTP = "http.server.requests";

    private final MeterRegistry registry;
    private final RegistroErrosHttp registroErros;

    public MonitorRequisicoesEndpoint(MeterRegistry registry, RegistroErrosHttp registroErros) {
        this.registry = registry;
        this.registroErros = registroErros;
    }

    @ReadOperation
    public MonitorRequisicoes ler() {
        return new MonitorRequisicoes(System.currentTimeMillis(), rotas(), registroErros.recentes(), registroErros.totalRegistrado());
    }

    /**
     * Um timer do Micrometer por combinação de método, rota, status, resultado e
     * exceção; aqui eles se juntam numa linha por método + rota.
     */
    List<Rota> rotas() {
        Map<String, Acumulado> porRota = new LinkedHashMap<>();
        for (Timer timer : registry.find(METRICA_HTTP).timers()) {
            String uri = timer.getId().getTag("uri");
            if (MonitorDesempenhoEndpoint.URI_DO_PAINEL.equals(uri) || URI_DO_PAINEL.equals(uri)) {
                continue;
            }
            String metodo = timer.getId().getTag("method");
            long n = timer.count();
            if (n == 0) {
                continue;
            }
            Acumulado a = porRota.computeIfAbsent(metodo + " " + uri, k -> new Acumulado(metodo, uri));
            a.requisicoes += n;
            a.tempoTotalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            a.maximoMs = Math.max(a.maximoMs, timer.max(TimeUnit.MILLISECONDS));
            String status = timer.getId().getTag("status");
            if (status != null) {
                a.porStatus.merge(status, n, Long::sum);
                if (status.startsWith("5")) {
                    a.erros5xx += n;
                } else if (status.startsWith("4")) {
                    a.erros4xx += n;
                } else {
                    a.sucesso += n;
                }
            }
            String excecao = timer.getId().getTag("exception");
            if (excecao != null && !excecao.isBlank() && !"none".equalsIgnoreCase(excecao)) {
                a.excecoes.merge(excecao, n, Long::sum);
            }
        }
        List<Rota> rotas = new ArrayList<>();
        for (Acumulado a : porRota.values()) {
            rotas.add(
                new Rota(
                    a.metodo,
                    a.uri,
                    a.requisicoes,
                    arredondar(a.tempoTotalMs),
                    arredondar(a.maximoMs),
                    a.sucesso,
                    a.erros4xx,
                    a.erros5xx,
                    a.porStatus,
                    a.excecoes
                )
            );
        }
        rotas.sort(
            Comparator.comparingLong(Rota::requisicoes).reversed().thenComparing(Rota::uri, Comparator.nullsLast(String::compareTo))
        );
        return rotas;
    }

    private static double arredondar(double ms) {
        return Math.round(ms * 10) / 10.0;
    }

    // O que vai se somando de cada rota antes de virar Rota
    private static final class Acumulado {

        final String metodo;
        final String uri;
        long requisicoes;
        double tempoTotalMs;
        double maximoMs;
        long sucesso;
        long erros4xx;
        long erros5xx;
        final Map<String, Long> porStatus = new TreeMap<>();
        final Map<String, Long> excecoes = new TreeMap<>();

        Acumulado(String metodo, String uri) {
            this.metodo = metodo;
            this.uri = uri;
        }
    }
}
