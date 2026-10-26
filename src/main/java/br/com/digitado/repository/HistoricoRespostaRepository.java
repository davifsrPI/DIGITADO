package br.com.digitado.repository;

import br.com.digitado.domain.HistoricoResposta;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas do histórico de respostas - a base dos painéis de desempenho.
 *
 * Todas as agregações são feitas NO BANCO: um jogador veterano pode ter dezenas
 * de milhares de linhas, e trazer isso para a memória só para contar acertos
 * derrubaria a tela. Os índices que sustentam cada consulta estão no changelog
 * 20260824140000 (login+data, login+palavra, data).
 *
 * As consultas nativas devolvem Object[] (mesmo padrão de
 * PalavraRepository.buscarEstatistica): a ordem das colunas está documentada em
 * cada método e a conversão fica no HistoricoRespostaService.
 */
@SuppressWarnings("unused")
@Repository
public interface HistoricoRespostaRepository extends JpaRepository<HistoricoResposta, Long> {
    // Tela de consulta do admin (AdminTabelasResource): paginada, com busca pelo login
    Page<HistoricoResposta> findByLoginContainingIgnoreCase(String login, Pageable pageable);

    /** [total, acertos, primeiraResposta, ultimaResposta] do titular. */
    @Query(
        value = "SELECT COUNT(*), SUM(CASE WHEN correta THEN 1 ELSE 0 END), MIN(data_resposta), MAX(data_resposta) " +
        "FROM historico_resposta WHERE login = :login",
        nativeQuery = true
    )
    List<Object[]> resumoDoUsuario(@Param("login") String login);

    /** [dificuldade, total, acertos] por faixa de dificuldade. */
    @Query(
        value = "SELECT COALESCE(dificuldade, 'MEDIO'), COUNT(*), SUM(CASE WHEN correta THEN 1 ELSE 0 END) " +
        "FROM historico_resposta WHERE login = :login GROUP BY COALESCE(dificuldade, 'MEDIO')",
        nativeQuery = true
    )
    List<Object[]> porDificuldade(@Param("login") String login);

    /** [mes 'YYYY-MM', total, acertos] - a linha do tempo desde a primeira resposta. */
    @Query(
        value = "SELECT DATE_FORMAT(data_resposta, '%Y-%m'), COUNT(*), SUM(CASE WHEN correta THEN 1 ELSE 0 END) " +
        "FROM historico_resposta WHERE login = :login " +
        "GROUP BY DATE_FORMAT(data_resposta, '%Y-%m') ORDER BY 1",
        nativeQuery = true
    )
    List<Object[]> evolucaoMensal(@Param("login") String login);

    /**
     * [palavraId, texto, dificuldade, total, erros] - as palavras que o titular
     * mais erra. Ordena por quantidade de erros e, no empate, pela taxa de erro:
     * errar 3 de 3 dói mais do que errar 3 de 30.
     */
    @Query(
        value = "SELECT h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO'), COUNT(*), " +
        "SUM(CASE WHEN h.correta THEN 0 ELSE 1 END) AS erros " +
        "FROM historico_resposta h JOIN palavra p ON p.id = h.palavra_id " +
        "WHERE h.login = :login GROUP BY h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO') " +
        "HAVING erros > 0 ORDER BY erros DESC, erros / COUNT(*) DESC, p.texto LIMIT :limite",
        nativeQuery = true
    )
    List<Object[]> maisErradas(@Param("login") String login, @Param("limite") int limite);

    /** [palavraId, texto, dificuldade, total, acertos] - onde o titular vai bem. */
    @Query(
        value = "SELECT h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO'), COUNT(*), " +
        "SUM(CASE WHEN h.correta THEN 1 ELSE 0 END) AS acertos " +
        "FROM historico_resposta h JOIN palavra p ON p.id = h.palavra_id " +
        "WHERE h.login = :login GROUP BY h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO') " +
        "HAVING acertos > 0 ORDER BY acertos DESC, acertos / COUNT(*) DESC, p.texto LIMIT :limite",
        nativeQuery = true
    )
    List<Object[]> maisAcertadas(@Param("login") String login, @Param("limite") int limite);

    /** [tipoErro, total] - em que o titular tropeça (acento, troca de letra...). */
    @Query(
        value = "SELECT COALESCE(tipo_erro, 'OUTRO'), COUNT(*) FROM historico_resposta " +
        "WHERE login = :login AND correta = false GROUP BY COALESCE(tipo_erro, 'OUTRO') ORDER BY 2 DESC",
        nativeQuery = true
    )
    List<Object[]> errosPorTipo(@Param("login") String login);

    /**
     * As últimas N respostas do titular, da mais nova para a mais antiga - é o que
     * alimenta o ponteiro do acelerador (desempenho RECENTE) e a sequência atual
     * de acertos, sem carregar o histórico inteiro.
     *
     * Projeta 1/0 em vez da coluna booleana crua e recebe como Object: booleano em
     * consulta nativa chega como Boolean, Byte ou Integer dependendo do driver, e
     * declarar List&lt;Boolean&gt; aqui viraria ClassCastException em tempo de
     * execução. A conversão fica no HistoricoRespostaService.acertou.
     */
    @Query(
        value = "SELECT CASE WHEN correta THEN 1 ELSE 0 END FROM historico_resposta " +
        "WHERE login = :login ORDER BY data_resposta DESC, id DESC LIMIT :limite",
        nativeQuery = true
    )
    List<Object> ultimasRespostas(@Param("login") String login, @Param("limite") int limite);

    /**
     * A última resposta do titular a UMA palavra (1 = acertou, 0 = errou), vazia se
     * ele nunca a respondeu. Consultada ANTES de gravar a resposta nova: é o que diz
     * se um acerto agora é a recuperação de um erro ("Aprendendo com os Erros").
     */
    @Query(
        value = "SELECT CASE WHEN correta THEN 1 ELSE 0 END FROM historico_resposta " +
        "WHERE login = :login AND palavra_id = :palavraId ORDER BY data_resposta DESC, id DESC LIMIT 1",
        nativeQuery = true
    )
    List<Object> ultimaRespostaNaPalavra(@Param("login") String login, @Param("palavraId") long palavraId);

    /**
     * [palavraId, tentativas, acertos, tempoMedioMs, similaridadeMedia] de cada
     * palavra que o titular já respondeu - as quatro medidas da equação que escolhe
     * a próxima palavra do modo individual (PartidaIndividualService).
     *
     * Similaridade nula (palavra do dia, respostas antigas) entra como 100 quando
     * a resposta estava certa e fica fora da média quando estava errada: o acerto
     * diz quanto ele acertou, o erro antigo não diz. Tempo nulo fica fora da média.
     */
    @Query(
        value = "SELECT palavra_id, COUNT(*), SUM(CASE WHEN correta THEN 1 ELSE 0 END), AVG(tempo_ms), " +
        "AVG(COALESCE(similaridade, CASE WHEN correta THEN 100 END)) " +
        "FROM historico_resposta WHERE login = :login GROUP BY palavra_id",
        nativeQuery = true
    )
    List<Object[]> estatisticaPorPalavraDoJogador(@Param("login") String login);

    // ===== Visão do administrador (agregada, só sobre os ALUNOS) =====

    /**
     * Recorte usado por TODAS as consultas do painel do admin: fora quem tem
     * ROLE_ADMIN.
     *
     * O administrador entra no jogo para testar - as respostas dele não são
     * desempenho de turma e distorceriam a média, ainda mais numa base pequena.
     * O histórico dele continua existindo e aparece normalmente no "Meu
     * Desempenho" dele; some apenas do relatório agregado.
     */
    String SO_ALUNOS =
        " NOT EXISTS (SELECT 1 FROM jhi_user ju JOIN jhi_user_authority jua ON jua.user_id = ju.id " +
        "WHERE ju.login = h.login AND jua.authority_name = 'ROLE_ADMIN') ";

    /**
     * [total, acertos, alunosDistintos, naoRespondidas] de todos os alunos.
     * naoRespondidas: rodadas em que o tempo acabou sem resposta - contam como
     * erro na taxa, mas o painel mostra à parte (não é o mesmo que errar a grafia).
     */
    @Query(
        value = "SELECT COUNT(*), SUM(CASE WHEN h.correta THEN 1 ELSE 0 END), COUNT(DISTINCT h.login), " +
        "SUM(CASE WHEN h.tipo_erro = 'NAO_RESPONDEU' THEN 1 ELSE 0 END) " +
        "FROM historico_resposta h WHERE" +
        SO_ALUNOS,
        nativeQuery = true
    )
    List<Object[]> resumoGlobal();

    // ===== Painel do admin filtrado por UM tipo de erro =====

    /** Expressão do tipo de erro de uma linha (erro sem classificação conta como OUTRO). */
    String TIPO_DA_LINHA = " COALESCE(h.tipo_erro, 'OUTRO') ";

    /** [ocorrencias, alunosAfetados, palavrasAfetadas] de um tipo de erro. */
    @Query(
        value = "SELECT COUNT(*), COUNT(DISTINCT h.login), COUNT(DISTINCT h.palavra_id) FROM historico_resposta h " +
        "WHERE h.correta = false AND" +
        TIPO_DA_LINHA +
        "= :tipo AND" +
        SO_ALUNOS,
        nativeQuery = true
    )
    List<Object[]> resumoTipoErro(@Param("tipo") String tipo);

    /** [mes 'YYYY-MM', respostasNoMes, ocorrenciasDoTipo] dos últimos meses. */
    @Query(
        value = "SELECT DATE_FORMAT(h.data_resposta, '%Y-%m'), COUNT(*), " +
        "SUM(CASE WHEN h.correta = false AND" +
        TIPO_DA_LINHA +
        "= :tipo THEN 1 ELSE 0 END) " +
        "FROM historico_resposta h WHERE h.data_resposta >= :desde AND" +
        SO_ALUNOS +
        "GROUP BY DATE_FORMAT(h.data_resposta, '%Y-%m') ORDER BY 1",
        nativeQuery = true
    )
    List<Object[]> evolucaoTipoErro(@Param("tipo") String tipo, @Param("desde") Instant desde);

    /** [palavraId, texto, dificuldade, respostas, ocorrencias] - onde este erro mais acontece. */
    @Query(
        value = "SELECT h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO'), COUNT(*), " +
        "SUM(CASE WHEN h.correta = false AND" +
        TIPO_DA_LINHA +
        "= :tipo THEN 1 ELSE 0 END) AS ocorrencias " +
        "FROM historico_resposta h JOIN palavra p ON p.id = h.palavra_id WHERE" +
        SO_ALUNOS +
        "GROUP BY h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO') " +
        "HAVING ocorrencias > 0 ORDER BY ocorrencias DESC, ocorrencias / COUNT(*) DESC, p.texto LIMIT :limite",
        nativeQuery = true
    )
    List<Object[]> palavrasComTipoErro(@Param("tipo") String tipo, @Param("limite") int limite);

    /** [mes 'YYYY-MM', total, acertos, alunosDistintos] dos últimos meses. */
    @Query(
        value = "SELECT DATE_FORMAT(h.data_resposta, '%Y-%m'), COUNT(*), SUM(CASE WHEN h.correta THEN 1 ELSE 0 END), " +
        "COUNT(DISTINCT h.login) FROM historico_resposta h WHERE h.data_resposta >= :desde AND" +
        SO_ALUNOS +
        "GROUP BY DATE_FORMAT(h.data_resposta, '%Y-%m') ORDER BY 1",
        nativeQuery = true
    )
    List<Object[]> evolucaoMensalGlobal(@Param("desde") Instant desde);

    /** [palavraId, texto, dificuldade, total, erros] - as pedras no sapato da turma. */
    @Query(
        value = "SELECT h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO'), COUNT(*), " +
        "SUM(CASE WHEN h.correta THEN 0 ELSE 1 END) AS erros " +
        "FROM historico_resposta h JOIN palavra p ON p.id = h.palavra_id WHERE" +
        SO_ALUNOS +
        "GROUP BY h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO') " +
        "HAVING erros > 0 AND COUNT(*) >= :minRespostas ORDER BY erros / COUNT(*) DESC, erros DESC, p.texto LIMIT :limite",
        nativeQuery = true
    )
    List<Object[]> maisErradasGlobal(@Param("minRespostas") int minRespostas, @Param("limite") int limite);

    /** [palavraId, texto, dificuldade, total, acertos] - o que a turma já domina. */
    @Query(
        value = "SELECT h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO'), COUNT(*), " +
        "SUM(CASE WHEN h.correta THEN 1 ELSE 0 END) AS acertos " +
        "FROM historico_resposta h JOIN palavra p ON p.id = h.palavra_id WHERE" +
        SO_ALUNOS +
        "GROUP BY h.palavra_id, p.texto, COALESCE(h.dificuldade, 'MEDIO') " +
        "HAVING acertos > 0 AND COUNT(*) >= :minRespostas ORDER BY acertos / COUNT(*) DESC, acertos DESC, p.texto LIMIT :limite",
        nativeQuery = true
    )
    List<Object[]> maisAcertadasGlobal(@Param("minRespostas") int minRespostas, @Param("limite") int limite);

    /**
     * Quanto cada aluno se DESENVOLVEU: [total, taxaPrimeiraMetade, taxaSegundaMetade].
     *
     * O histórico de cada aluno é partido ao meio na ordem cronológica e as duas
     * metades são comparadas - a diferença é o quanto ele evoluiu. Metades em vez
     * de "primeiras 30 x últimas 30" porque metade nunca se sobrepõe: com 40
     * respostas as duas janelas de 30 dividiriam 20 registros e a evolução
     * apareceria menor do que é.
     *
     * Sem login na projeção: o painel só mostra MÉDIAS da turma, então a
     * identidade de quem evoluiu quanto nem sai do banco.
     */
    @Query(
        value = "SELECT n.total, " +
        "AVG(CASE WHEN n.ordem <= FLOOR(n.total / 2) THEN n.acertou END) * 100, " +
        "AVG(CASE WHEN n.ordem > FLOOR(n.total / 2) THEN n.acertou END) * 100 " +
        "FROM (SELECT h.login AS login, CASE WHEN h.correta THEN 1 ELSE 0 END AS acertou, " +
        "ROW_NUMBER() OVER (PARTITION BY h.login ORDER BY h.data_resposta, h.id) AS ordem, " +
        "COUNT(*) OVER (PARTITION BY h.login) AS total " +
        "FROM historico_resposta h WHERE" +
        SO_ALUNOS +
        ") n WHERE n.total >= :minRespostas GROUP BY n.login, n.total",
        nativeQuery = true
    )
    List<Object[]> evolucaoPorAluno(@Param("minRespostas") int minRespostas);

    // ===== Erros destrinchados (subtipo e trecho trocado) =====

    /**
     * [tipo, subtipo, total] dos erros da turma, de todos os tipos ou de um só.
     * Subtipo nulo: resposta gravada antes da classificação detalhada.
     */
    @Query(
        value = "SELECT" +
        TIPO_DA_LINHA +
        ", h.subtipo_erro, COUNT(*) FROM historico_resposta h " +
        "WHERE h.correta = false AND (:tipo IS NULL OR" +
        TIPO_DA_LINHA +
        "= :tipo) AND" +
        SO_ALUNOS +
        "GROUP BY" +
        TIPO_DA_LINHA +
        ", h.subtipo_erro ORDER BY 3 DESC",
        nativeQuery = true
    )
    List<Object[]> errosPorSubtipo(@Param("tipo") String tipo);

    /**
     * [tipo, subtipo, detalhe, ocorrencias, alunos, palavras, exemplos 'a|b|c|d'] -
     * os trechos que a turma mais troca ("ç→ss", "é→e"), de todos os tipos ou de um só.
     * Os exemplos são as palavras onde aconteceu, em ordem alfabética.
     */
    @Query(
        value = "SELECT h.tipo_erro, h.subtipo_erro, h.detalhe_erro, COUNT(*), COUNT(DISTINCT h.login), " +
        "COUNT(DISTINCT h.palavra_id), SUBSTRING_INDEX(GROUP_CONCAT(DISTINCT p.texto ORDER BY p.texto SEPARATOR '|'), '|', 4) " +
        "FROM historico_resposta h JOIN palavra p ON p.id = h.palavra_id " +
        "WHERE h.correta = false AND h.detalhe_erro IS NOT NULL AND (:tipo IS NULL OR h.tipo_erro = :tipo) AND" +
        SO_ALUNOS +
        "GROUP BY h.tipo_erro, h.subtipo_erro, h.detalhe_erro ORDER BY 4 DESC, 5 DESC, h.detalhe_erro LIMIT :limite",
        nativeQuery = true
    )
    List<Object[]> confusoesMaisComuns(@Param("tipo") String tipo, @Param("limite") int limite);

    /**
     * [palavraId, tipo, subtipo, detalhe, ocorrencias] dos erros nas palavras pedidas,
     * do mais comum ao menos comum em cada uma. Sem filtro de tipo, a rodada sem
     * resposta fica de fora: a pergunta é "como a turma ERRA esta palavra".
     */
    @Query(
        value = "SELECT h.palavra_id," +
        TIPO_DA_LINHA +
        ", h.subtipo_erro, h.detalhe_erro, COUNT(*) FROM historico_resposta h " +
        "WHERE h.correta = false AND h.palavra_id IN (:palavras) AND " +
        "(:tipo IS NULL OR" +
        TIPO_DA_LINHA +
        "= :tipo) AND (:tipo IS NOT NULL OR" +
        TIPO_DA_LINHA +
        "<> 'NAO_RESPONDEU') AND" +
        SO_ALUNOS +
        "GROUP BY h.palavra_id," +
        TIPO_DA_LINHA +
        ", h.subtipo_erro, h.detalhe_erro ORDER BY h.palavra_id, 5 DESC",
        nativeQuery = true
    )
    List<Object[]> errosPorPalavra(@Param("palavras") Collection<Long> palavras, @Param("tipo") String tipo);

    /** [tipoErro, total] da turma - onde os alunos mais tropeçam. */
    @Query(
        value = "SELECT COALESCE(h.tipo_erro, 'OUTRO'), COUNT(*) FROM historico_resposta h " +
        "WHERE h.correta = false AND" +
        SO_ALUNOS +
        "GROUP BY COALESCE(h.tipo_erro, 'OUTRO') ORDER BY 2 DESC",
        nativeQuery = true
    )
    List<Object[]> errosPorTipoGlobal();
}
