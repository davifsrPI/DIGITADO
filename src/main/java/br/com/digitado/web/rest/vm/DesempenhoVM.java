package br.com.digitado.web.rest.vm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
     * Um jeito específico de errar dentro de um tipo ("Faltou o acento" dentro de
     * "Acentuação"). subtipo nulo = respostas gravadas antes da classificação
     * detalhada, que só guardaram o tipo.
     */
    public record SubtipoErroVM(String tipo, String subtipo, long total, int percentualDoTipo) {}

    /**
     * Um trecho que a turma troca, no formato "certo→digitado" ("é→e", "ç→ss"):
     * quantas vezes, quantos alunos, em quantas palavras e algumas delas de exemplo.
     */
    public record ConfusaoVM(
        String tipo,
        String subtipo,
        String detalhe,
        long ocorrencias,
        long alunos,
        long palavras,
        int percentualDosErros,
        List<String> exemplos
    ) {}

    /** O erro mais comum numa palavra, com a fatia que ele tem dos erros dela. */
    public record ErroComumVM(String tipo, String subtipo, String detalhe, long ocorrencias, int percentualDosErros) {}

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
        long totalNaoRespondeu,
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
        List<TipoErroVM> errosPorTipo,
        /** Cada tipo aberto nos seus subtipos. */
        List<SubtipoErroVM> subtiposErro,
        /** Os trechos que a turma mais troca, de todos os tipos. */
        List<ConfusaoVM> confusoes,
        /** palavraId -> o erro mais comum, para as palavras de palavrasMaisErradas. */
        Map<Long, ErroComumVM> erroMaisComumPorPalavra,
        FiltroTipoErroVM filtro
    ) {}

    /** Um mês do recorte por tipo de erro: quantas respostas e quantas tiveram aquele erro. */
    public record PontoTipoErroVM(String mes, long respostas, long ocorrencias, double taxa) {}

    /** Uma palavra onde o tipo de erro filtrado acontece. percentual = ocorrências / respostas. */
    public record PalavraTipoErroVM(Long palavraId, String texto, String dificuldade, long respostas, long ocorrencias, int percentual) {}

    /**
     * O painel recortado por UM tipo de erro (filtro "Tipos de erro da turma").
     *
     * taxa* são a fatia das respostas do mês que tiveram este erro, com uma casa
     * decimal: um tipo de erro raramente passa de poucos por cento das respostas, e
     * arredondar para inteiro faria 1,4% e 0,6% parecerem a mesma coisa.
     * veredito: MELHOROU quando a turma cometeu MENOS este erro que no mês passado.
     */
    public record FiltroTipoErroVM(
        String tipo,
        long ocorrencias,
        int percentualDosErros,
        double percentualDasRespostas,
        long alunosAfetados,
        long palavrasAfetadas,
        double taxaMesAtual,
        double taxaMesAnterior,
        String veredito,
        String resumo,
        List<PontoTipoErroVM> evolucao,
        List<PalavraTipoErroVM> palavras,
        /** O tipo aberto nos seus subtipos. */
        List<SubtipoErroVM> subtipos,
        /** Os trechos mais trocados dentro deste tipo. */
        List<ConfusaoVM> confusoes,
        /** palavraId -> o jeito mais comum deste erro, para as palavras de "palavras". */
        Map<Long, ErroComumVM> erroMaisComumPorPalavra
    ) {}
}
