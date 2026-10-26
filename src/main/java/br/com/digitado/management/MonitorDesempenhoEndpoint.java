package br.com.digitado.management;

import br.com.digitado.management.MonitorDesempenho.AreaMemoria;
import br.com.digitado.management.MonitorDesempenho.Coletor;
import br.com.digitado.management.MonitorDesempenho.ColetorDeLixo;
import br.com.digitado.management.MonitorDesempenho.Cpu;
import br.com.digitado.management.MonitorDesempenho.Http;
import br.com.digitado.management.MonitorDesempenho.MemoriaJvm;
import br.com.digitado.management.MonitorDesempenho.MemoriaMaquina;
import br.com.digitado.management.MonitorDesempenho.Sistema;
import br.com.digitado.management.MonitorDesempenho.Threads;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * GET /management/desempenho: a leitura que alimenta o painel "Desempenho" da tela
 * Administração > Métricas, no estilo do Gerenciador de Tarefas do Windows.
 *
 * Fica no actuator, e não em /api, por dois motivos:
 * - /management/** já é só de ADMIN na SecurityConfiguration, como o resto da tela
 *   de métricas (jhimetrics, threaddump);
 * - o painel pergunta até duas vezes por segundo, e o limite de 100 requisições por
 *   minuto do RateLimitFilter, que vale para /api, bloquearia o próprio admin.
 *
 * Lê direto dos MXBeans da JVM - os mesmos que o Micrometer usa -, sem guardar
 * nada entre uma leitura e outra. As requisições HTTP vêm do Micrometer
 * (http.server.requests), sem contar as do próprio painel.
 */
@Component
@Endpoint(id = "desempenho")
public class MonitorDesempenhoEndpoint {

    /** URL deste endpoint: as perguntas do painel não entram na conta de requisições. */
    static final String URI_DO_PAINEL = "/management/desempenho";

    private static final String METRICA_HTTP = "http.server.requests";

    /** Os estados que a tela mostra, nesta ordem; NEW/TERMINATED só aparecem se houver. */
    private static final List<Thread.State> ESTADOS_EXIBIDOS = List.of(
        Thread.State.RUNNABLE,
        Thread.State.WAITING,
        Thread.State.TIMED_WAITING,
        Thread.State.BLOCKED
    );

    private final MeterRegistry registry;
    private final OperatingSystemMXBean sistema = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoria = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadsMx = ManagementFactory.getThreadMXBean();
    private final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    private final ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();

    public MonitorDesempenhoEndpoint(MeterRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public MonitorDesempenho ler() {
        return new MonitorDesempenho(
            System.currentTimeMillis(),
            cpu(),
            memoriaMaquina(),
            memoriaJvm(),
            coletorDeLixo(),
            threads(),
            http(),
            sistema()
        );
    }

    private Cpu cpu() {
        Double maquina = null;
        Double processo = null;
        if (sistema instanceof com.sun.management.OperatingSystemMXBean so) {
            maquina = percentual(so.getCpuLoad());
            processo = percentual(so.getProcessCpuLoad());
        }
        double carga = sistema.getSystemLoadAverage();
        return new Cpu(maquina, processo, sistema.getAvailableProcessors(), carga < 0 ? null : arredondar(carga, 2));
    }

    private MemoriaMaquina memoriaMaquina() {
        if (!(sistema instanceof com.sun.management.OperatingSystemMXBean so)) {
            return new MemoriaMaquina(null, null, null, null, null);
        }
        long total = so.getTotalMemorySize();
        long livre = so.getFreeMemorySize();
        long limite = so.getTotalSwapSpaceSize();
        long livreTroca = so.getFreeSwapSpaceSize();
        boolean temTotal = total > 0 && livre >= 0;
        boolean temTroca = limite > 0 && livreTroca >= 0;
        return new MemoriaMaquina(
            temTotal ? total : null,
            temTotal ? livre : null,
            temTotal ? Math.max(0, total - livre) : null,
            temTroca ? Math.max(0, limite - livreTroca) : null,
            temTroca ? limite : null
        );
    }

    private MemoriaJvm memoriaJvm() {
        MemoryUsage heap = memoria.getHeapMemoryUsage();
        MemoryUsage naoHeap = memoria.getNonHeapMemoryUsage();
        List<AreaMemoria> areas = new ArrayList<>();
        for (MemoryPoolMXBean area : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage uso = area.isValid() ? area.getUsage() : null;
            if (uso != null) {
                areas.add(
                    new AreaMemoria(
                        area.getName(),
                        area.getType() == MemoryType.HEAP,
                        uso.getUsed(),
                        uso.getCommitted(),
                        teto(uso.getMax())
                    )
                );
            }
        }
        long buffersDiretos = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
            .stream()
            .filter(b -> "direct".equals(b.getName()))
            .mapToLong(BufferPoolMXBean::getMemoryUsed)
            .filter(v -> v > 0)
            .sum();
        return new MemoriaJvm(
            heap.getUsed(),
            heap.getCommitted(),
            teto(heap.getMax()),
            naoHeap.getUsed(),
            naoHeap.getCommitted(),
            buffersDiretos,
            classes.getLoadedClassCount(),
            areas
        );
    }

    private static ColetorDeLixo coletorDeLixo() {
        long coletas = 0;
        long tempoMs = 0;
        List<Coletor> coletores = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            // -1 = o coletor não informa; conta como nada, não como "menos um"
            long c = Math.max(0, gc.getCollectionCount());
            long t = Math.max(0, gc.getCollectionTime());
            coletores.add(new Coletor(gc.getName(), c, t));
            coletas += c;
            tempoMs += t;
        }
        return new ColetorDeLixo(coletas, tempoMs, coletores);
    }

    private Threads threads() {
        Map<String, Integer> porEstado = new LinkedHashMap<>();
        ESTADOS_EXIBIDOS.forEach(estado -> porEstado.put(estado.name(), 0));
        // profundidade 0: só o estado, sem montar pilha de chamadas (barato o bastante
        // para rodar a cada meio segundo)
        for (ThreadInfo info : threadsMx.getThreadInfo(threadsMx.getAllThreadIds(), 0)) {
            if (info != null) {
                porEstado.merge(info.getThreadState().name(), 1, Integer::sum);
            }
        }
        return new Threads(
            threadsMx.getThreadCount(),
            threadsMx.getDaemonThreadCount(),
            threadsMx.getPeakThreadCount(),
            threadsMx.getTotalStartedThreadCount(),
            porEstado
        );
    }

    Http http() {
        long requisicoes = 0;
        long erros = 0;
        double tempoMs = 0;
        for (Timer timer : registry.find(METRICA_HTTP).timers()) {
            if (URI_DO_PAINEL.equals(timer.getId().getTag("uri"))) {
                continue;
            }
            long n = timer.count();
            requisicoes += n;
            tempoMs += timer.totalTime(TimeUnit.MILLISECONDS);
            String status = timer.getId().getTag("status");
            if (status != null && status.startsWith("5")) {
                erros += n;
            }
        }
        return new Http(requisicoes, arredondar(tempoMs, 1), erros);
    }

    private Sistema sistema() {
        Long pid;
        try {
            pid = runtime.getPid();
        } catch (UnsupportedOperationException e) {
            pid = null;
        }
        return new Sistema(
            sistema.getName(),
            sistema.getVersion(),
            sistema.getArch(),
            runtime.getVmName() + " · " + runtime.getVmVendor(),
            Runtime.version().toString(),
            pid,
            runtime.getStartTime(),
            runtime.getUptime()
        );
    }

    /** Fração de 0 a 1 do MXBean em %, com uma casa; negativo = o sistema não soube dizer. */
    static Double percentual(double fracao) {
        if (Double.isNaN(fracao) || fracao < 0) {
            return null;
        }
        return arredondar(Math.min(1, fracao) * 100, 1);
    }

    /** max do MemoryUsage: -1 (ou 0) quer dizer "sem teto definido". */
    private static Long teto(long max) {
        return max > 0 ? max : null;
    }

    private static double arredondar(double valor, int casas) {
        double fator = Math.pow(10, casas);
        return Math.round(valor * fator) / fator;
    }
}
