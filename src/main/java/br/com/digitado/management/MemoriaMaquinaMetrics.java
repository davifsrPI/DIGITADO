package br.com.digitado.management;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/**
 * Memória da MÁQUINA onde o DIGITADO roda - não só a da JVM.
 *
 * As métricas que já vinham do Spring (jvm_memory_*) dizem quanto o processo usa
 * do heap que ganhou; não dizem se a máquina inteira está sem memória. Numa escola
 * o servidor costuma ser um PC comum, dividindo a RAM com navegador, MySQL e o que
 * mais estiver aberto, e é a máquina sem memória que deixa a partida lenta.
 *
 * Lê do próprio sistema operacional, pela JVM, e por isso funciona do mesmo jeito
 * no Windows (setup-ambiente.ps1) e no container (iniciar-container.ps1). No
 * container o número é o do container: com limite de memória, o limite; sem
 * limite, o da máquina virtual do Docker.
 *
 * Saem em /management/prometheus como digitado_maquina_memoria_* e alimentam o
 * dashboard "DIGITADO - Máquina" do Grafana e o alerta MemoriaDaMaquinaAlta.
 */
@Component
public class MemoriaMaquinaMetrics implements MeterBinder {

    private final OperatingSystemMXBean sistema = ManagementFactory.getOperatingSystemMXBean();

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!(sistema instanceof com.sun.management.OperatingSystemMXBean so)) {
            // JVM sem a extensão da Oracle/OpenJDK: não há de onde ler, e a ausência
            // da métrica é melhor que um zero que pareça máquina vazia
            return;
        }
        bytes(registry, "digitado.maquina.memoria.fisica", "Memória física total da máquina", so, s -> s.getTotalMemorySize());
        bytes(registry, "digitado.maquina.memoria.livre", "Memória física disponível na máquina", so, s -> s.getFreeMemorySize());
        bytes(registry, "digitado.maquina.memoria.usada", "Memória física em uso na máquina (todos os programas)", so, s ->
            Math.max(0, s.getTotalMemorySize() - s.getFreeMemorySize())
        );
        bytes(registry, "digitado.maquina.swap.tamanho", "Área de troca (swap/arquivo de paginação) total", so, s ->
            s.getTotalSwapSpaceSize()
        );
        bytes(registry, "digitado.maquina.swap.livre", "Área de troca (swap/arquivo de paginação) livre", so, s -> s.getFreeSwapSpaceSize()
        );
        Gauge.builder("digitado.maquina.memoria.uso", so, MemoriaMaquinaMetrics::fracaoUsada)
            .description("Fração da memória física da máquina em uso, de 0 a 1")
            .baseUnit("ratio")
            .register(registry);
    }

    private static void bytes(
        MeterRegistry registry,
        String nome,
        String descricao,
        com.sun.management.OperatingSystemMXBean so,
        ToDoubleFunction<com.sun.management.OperatingSystemMXBean> leitura
    ) {
        Gauge.builder(nome, so, leitura).description(descricao).baseUnit(BaseUnits.BYTES).register(registry);
    }

    static double fracaoUsada(com.sun.management.OperatingSystemMXBean so) {
        long total = so.getTotalMemorySize();
        if (total <= 0) {
            return Double.NaN;
        }
        return Math.max(0, Math.min(1, (double) (total - so.getFreeMemorySize()) / total));
    }
}
