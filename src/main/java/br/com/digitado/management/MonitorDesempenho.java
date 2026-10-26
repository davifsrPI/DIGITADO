package br.com.digitado.management;

import java.util.List;
import java.util.Map;

/**
 * Uma leitura do painel "Desempenho" (Administração > Métricas), no espírito da
 * aba Desempenho do Gerenciador de Tarefas do Windows: CPU, memória da máquina,
 * memória da JVM, coletor de lixo, threads e requisições HTTP.
 *
 * É uma FOTO do instante. Contadores que só crescem (coletas e tempo do coletor de
 * lixo, requisições HTTP) vêm acumulados, e a tela tira a taxa da diferença entre
 * duas leituras seguidas - o servidor não guarda histórico nem trabalha quando
 * ninguém está olhando.
 *
 * Valor que o sistema não informa vem NULO (ex.: carga média no Windows), nunca
 * zero: zero pareceria máquina ociosa.
 */
public record MonitorDesempenho(
    long instante,
    Cpu cpu,
    MemoriaMaquina memoria,
    MemoriaJvm memoriaJvm,
    ColetorDeLixo coletorDeLixo,
    Threads threads,
    Http http,
    Sistema sistema
) {
    /** Uso de CPU em %, de 0 a 100: a máquina inteira e só o processo do DIGITADO. */
    public record Cpu(Double maquina, Double processo, int processadores, Double cargaMedia) {}

    /**
     * Memória física da máquina, em bytes.
     *
     * confirmada/limiteConfirmada vêm da "área de troca" que a JVM informa: no
     * Windows é a carga de confirmação (RAM + arquivo de paginação), a mesma linha
     * "Confirmada" do Gerenciador de Tarefas; no Linux, o swap.
     */
    public record MemoriaMaquina(Long total, Long disponivel, Long emUso, Long confirmada, Long limiteConfirmada) {}

    /** Uma área de memória da JVM (Eden, Old Gen, Metaspace...), em bytes. maxima nula = sem teto. */
    public record AreaMemoria(String nome, boolean heap, long usada, long confirmada, Long maxima) {}

    public record MemoriaJvm(
        long heapUsada,
        long heapConfirmada,
        Long heapMaxima,
        long naoHeapUsada,
        long naoHeapConfirmada,
        long buffersDiretos,
        int classesCarregadas,
        List<AreaMemoria> areas
    ) {}

    public record Coletor(String nome, long coletas, long tempoMs) {}

    /** Totais desde que a JVM subiu. */
    public record ColetorDeLixo(long coletas, long tempoMs, List<Coletor> coletores) {}

    /** porEstado: RUNNABLE, WAITING, TIMED_WAITING, BLOCKED (e NEW/TERMINATED se houver). */
    public record Threads(int ativas, int daemon, int pico, long iniciadas, Map<String, Integer> porEstado) {}

    /** Totais desde que a JVM subiu, sem contar o próprio painel perguntando. */
    public record Http(long requisicoes, double tempoTotalMs, long erros) {}

    public record Sistema(
        String nomeSistema,
        String versaoSistema,
        String arquitetura,
        String jvm,
        String versaoJava,
        Long pid,
        long inicio,
        long tempoAtividadeMs
    ) {}
}
