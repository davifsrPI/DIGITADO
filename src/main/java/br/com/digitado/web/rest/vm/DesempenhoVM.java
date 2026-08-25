package br.com.digitado.web.rest.vm;

import java.time.Instant;
import java.util.List;

/**
 * ViewModels dos painéis de desempenho - o do titular ("Meu Desempenho") e o
 * agregado do administrador. Agrupados num arquivo só porque são peças do mesmo
 * relatório e mudam sempre juntas.
 *
 * Nada aqui é calculado no frontend: as telas só desenham o que o
 * HistoricoRespostaService já apurou no banco.
 */
public final class DesempenhoVM {

    private DesempenhoVM() {}

    /**
     * O "acelerador" do titular: em que faixa está a taxa de acerto.
     * Os limites vivem em HistoricoRespostaService.classificar.
     */
    public enum Nivel {
        RUIM,
        MEDIO,
        BOM,
    }

    /** Uma palavra do ranking pessoal (mais errada ou mais acertada). */
    public record PalavraDesempenhoVM(Long palavraId, String texto, String dificuldade, long total, long acertos, long erros, int taxa) {}

    /** Um ponto da linha do tempo: um mês com o que foi respondido nele. */
    public record PontoEvolucaoVM(String mes, long total, long acertos, int taxa, long jogadores) {}

    /** Quanto do total foi respondido em cada faixa de dificuldade. */
    public record FaixaDificuldadeVM(String dificuldade, long total, long acertos, int taxa) {}

    /** Contagem de erros por tipo (acentuação, troca de letra, ...). */
    public record TipoErroVM(String tipo, long total, int percentual) {}

    /**
     * Painel completo do titular - servido por GET /api/meu-desempenho, montado a
     * partir do login do token. Ninguém consulta o de outra pessoa.
     *
     * taxaGeral é o retrato desde o começo; taxaRecente olha só as últimas
     * respostas e é o que move o ponteiro do acelerador - é ela que responde
     * "como estou HOJE". tendencia é a diferença entre as duas, em pontos
     * percentuais: positiva significa que a pessoa está melhorando.
     */
    public record MeuDesempenhoVM(
        Instant primeiraResposta,
        Instant ultimaResposta,
        long totalRespostas,
        long totalAcertos,
        long totalErros,
        int taxaGeral,
        int taxaRecente,
        int tendencia,
        Nivel nivel,
        String mensagemNivel,
        int sequenciaAtual,
        int melhorSequencia,
        List<FaixaDificuldadeVM> porDificuldade,
        List<PontoEvolucaoVM> evolucao,
        List<PalavraDesempenhoVM> maisErradas,
        List<PalavraDesempenhoVM> maisAcertadas,
        List<TipoErroVM> errosPorTipo
    ) {}

    /**
     * Quanto a turma se DESENVOLVEU: o histórico de cada aluno é partido ao meio
     * na ordem cronológica, compara-se a taxa da segunda metade com a da primeira,
     * e o que sai daqui é a MÉDIA dessas evoluções.
     *
     * Só médias e contagens - nenhum aluno é identificado. O painel do admin
     * responde "a turma está evoluindo?", não "quem evoluiu quanto".
     */
    public record DesenvolvimentoVM(
        long alunosAvaliados,
        int mediaTaxaInicial,
        int mediaTaxaAtual,
        int mediaEvolucao,
        long alunosMelhoraram,
        long alunosEstaveis,
        long alunosPioraram,
        String resumo
    ) {}

    /**
     * Comparação de dois meses seguidos: é o "relatório" pedido.
     *
     * Responde duas perguntas diferentes:
     * 1) no mês corrente houve mais ACERTOS do que ERROS em todas as palavras
     *    usadas? (maisAcertosQueErros);
     * 2) o resultado MELHOROU ou PIOROU em relação ao mês anterior?
     *    (variacaoTaxa em pontos percentuais + veredito).
     */
    public record RelatorioMensalVM(
        String mesAtual,
        String mesAnterior,
        long respostasMesAtual,
        long respostasMesAnterior,
        long acertosMesAtual,
        long errosMesAtual,
        long acertosMesAnterior,
        long errosMesAnterior,
        int taxaMesAtual,
        int taxaMesAnterior,
        int variacaoTaxa,
        long variacaoRespostas,
        boolean maisAcertosQueErros,
        String balancoDoMes,
        String veredito,
        String resumo
    ) {}

    /**
     * Painel do administrador - GET /api/admin/desempenho, restrito à role ADMIN.
     *
     * Duas exclusões deliberadas:
     * - as respostas de quem tem ROLE_ADMIN ficam de fora de TODOS os números
     *   (o admin joga para testar; isso não é desempenho de turma);
     * - não existe lista por aluno. Só agregados, médias e o ranking de palavras -
     *   nenhuma resposta digitada e nenhum nome individual saem daqui.
     */
    public record DesempenhoGeralVM(
        long totalRespostas,
        long totalAcertos,
        long totalErros,
        int taxaGeral,
        long totalAlunos,
        long alunosBons,
        long alunosMedios,
        long alunosRuins,
        DesenvolvimentoVM desenvolvimento,
        RelatorioMensalVM relatorioMensal,
        List<PontoEvolucaoVM> evolucao,
        List<PalavraDesempenhoVM> palavrasMaisAcertadas,
        List<PalavraDesempenhoVM> palavrasMaisErradas,
        List<TipoErroVM> errosPorTipo
    ) {}
}
