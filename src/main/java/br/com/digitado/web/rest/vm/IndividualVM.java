package br.com.digitado.web.rest.vm;

import java.time.Instant;
import java.util.List;

/**
 * ViewModels do modo individual. Como em DesempenhoVM, agrupados num arquivo só:
 * são peças da mesma tela e mudam juntas.
 *
 * Nenhum deles carrega o texto da palavra da rodada enquanto ela está aberta
 * (salvo quando o servidor não tem sintetizador de voz - ver PalavraIndividualVM).
 */
public final class IndividualVM {

    private IndividualVM() {}

    /**
     * A palavra da rodada.
     *
     * texto: NULO enquanto a rodada está aberta e o servidor tem o áudio dela - o
     * aluno ouve em GET /api/individual/audio. Só vem preenchido quando não há
     * sintetizador no servidor: aí o navegador precisa do texto para falar, a mesma
     * degradação consciente das salas.
     */
    public record PalavraIndividualVM(
        Long id,
        String texto,
        String dificuldade,
        int tempoLimite,
        long timestampInicio,
        long timestampServidor
    ) {}

    /** Estado da partida em andamento - o que a tela desenha. */
    public record EstadoIndividualVM(
        int rodada,
        int vidas,
        int vidasMaximas,
        int acertos,
        int sequencia,
        int melhorSequencia,
        long xpGanho,
        // Recorde de acertos do jogador ANTES desta partida (0 se nunca jogou)
        int recorde,
        // A rodada atual já foi respondida? Então a tela espera o "Próxima palavra"
        boolean respondida,
        PalavraIndividualVM palavra
    ) {}

    /** Uma palavra já ditada, para o resumo do fim. */
    public record RespostaIndividualVM(
        int rodada,
        String texto,
        String respostaDigitada,
        boolean correta,
        String tipoErro,
        int similaridade,
        long xp
    ) {}

    /** O fim da partida: até onde chegou, e se bateu o recorde. */
    public record ResumoIndividualVM(
        String encerramento,
        int palavras,
        int acertos,
        int erros,
        int naoRespondidas,
        int melhorSequencia,
        long xpGanho,
        int recordeAnterior,
        boolean novoRecorde,
        List<RespostaIndividualVM> respostas
    ) {}

    /**
     * Resultado de uma resposta: o que a tela de correção mostra e, se as vidas
     * acabaram, o resumo da partida.
     */
    public record ResultadoIndividualVM(
        boolean correta,
        // A resposta chegou depois do tempo da rodada (vale como não respondida)
        boolean foraDoTempo,
        String textoCorreto,
        String tipoErro,
        int similaridade,
        long xp,
        EstadoIndividualVM estado,
        ResumoIndividualVM resumo
    ) {}

    /** Uma partida encerrada, para a lista das últimas. */
    public record PartidaAnteriorVM(Instant data, int palavras, int acertos, int melhorSequencia, long xpGanho, String encerramento) {}

    /** Recorde pessoal e as últimas partidas - a tela de entrada do modo. */
    public record RecordesVM(int recorde, Instant dataRecorde, boolean emAndamento, List<PartidaAnteriorVM> ultimas) {}
}
