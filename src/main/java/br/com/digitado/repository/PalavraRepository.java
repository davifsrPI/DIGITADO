package br.com.digitado.repository;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Dificuldade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Repositório JPA para a entidade Palavra
@SuppressWarnings("unused")
@Repository
public interface PalavraRepository extends JpaRepository<Palavra, Long> {
    // Busca exata pelo texto, ignorando maiúsculas/minúsculas (usada para evitar
    // duplicatas na busca e no cadastro rápido). findFIRST + ordem por id de
    // propósito: se o banco já tiver o mesmo texto duplicado (importações antigas),
    // devolve sempre o registro mais antigo em vez de estourar
    // IncorrectResultSizeDataAccessException — que derrubava /buscar e /sugerir.
    Optional<Palavra> findFirstByTextoIgnoreCaseOrderByIdAsc(String texto);

    // Retorna até 5 palavras ativas cujo texto contenha o trecho digitado — usada no autocomplete
    List<Palavra> findTop5ByTextoContainingIgnoreCaseAndAtivaTrue(String texto);

    /**
     * Classificação de dificuldade em SQL — mesma regra do getter
     * Palavra.getDificuldade(), mantenha as duas em sincronia (o limiar 15 é
     * Palavra.MIN_TENTATIVAS_PARA_METRICA):
     * - >= 15 tentativas: CALCULADA pela taxa de acerto
     *   (0–35% = DIFICIL, 36–65% = MEDIO, 66%+ = FACIL);
     * - < 15 tentativas: vale a coluna dificuldade (cadastrada); se nula, a palavra
     *   entra "aleatoriamente" numa das faixas via id % 3 (determinístico, estável).
     *
     * Deixei como constante porque as duas queries abaixo concatenam ela no @Query,
     * assim não corro o risco de escrever diferente em cada uma.
     */
    String CASE_DIFICULDADE_SQL =
        "(CASE WHEN total_tentativas < 15 THEN " +
        "COALESCE(dificuldade, CASE MOD(id, 3) WHEN 0 THEN 'FACIL' WHEN 1 THEN 'MEDIO' ELSE 'DIFICIL' END) " +
        "WHEN total_acertos * 100.0 / total_tentativas <= 35 THEN 'DIFICIL' " +
        "WHEN total_acertos * 100.0 / total_tentativas <= 65 THEN 'MEDIO' " +
        "ELSE 'FACIL' END)";

    // Sorteia N palavras ativas de uma determinada dificuldade usando ORDER BY RAND()
    @Query(
        value = "SELECT * FROM palavra WHERE ativa = true AND " + CASE_DIFICULDADE_SQL + " = :dif ORDER BY RAND() LIMIT :n",
        nativeQuery = true
    )
    List<Palavra> findRandomByDificuldade(@Param("dif") String dif, @Param("n") int n);

    // Variante que exclui ids (palavras da partida anterior da sala) — evita que
    // duas partidas seguidas repitam as mesmas palavras. Mesma regra de dificuldade.
    @Query(
        value = "SELECT * FROM palavra WHERE ativa = true AND id NOT IN (:ids) AND " +
        CASE_DIFICULDADE_SQL +
        " = :dif ORDER BY RAND() LIMIT :n",
        nativeQuery = true
    )
    List<Palavra> findRandomByDificuldadeExcluindo(@Param("dif") String dif, @Param("n") int n, @Param("ids") List<Long> ids);

    // Sorteia N palavras ativas quaisquer, excluindo as já escolhidas — usado para
    // completar a partida quando alguma faixa de dificuldade (calculada pela taxa
    // de acerto) não tem palavras suficientes, ex: banco novo onde quase tudo é MEDIO
    @Query(value = "SELECT * FROM palavra WHERE ativa = true AND id NOT IN (:ids) ORDER BY RAND() LIMIT :n", nativeQuery = true)
    List<Palavra> findRandomAtivasExcluindo(@Param("ids") List<Long> ids, @Param("n") int n);

    // Usados pela Palavra do Dia: total de palavras ativas e busca paginada em ordem
    // estável de id, para o sorteio determinístico do dia (mesma palavra o dia todo)
    long countByAtivaTrue();

    Page<Palavra> findByAtivaTrue(Pageable pageable);

    // Incremento atômico das colunas de estatística da palavra (total_tentativas e
    // total_acertos) — único ponto que escreve nesses contadores. Seguro mesmo com
    // vários jogadores respondendo ao mesmo tempo em salas diferentes.
    @Modifying
    @Query(
        value = "update palavra set total_tentativas = total_tentativas + 1, total_acertos = total_acertos + :acerto where id = :palavraId",
        nativeQuery = true
    )
    void incrementarEstatistica(@Param("palavraId") long palavraId, @Param("acerto") int acerto);

    // Leitura direta dos contadores no banco (ignora o cache de entidades do JPA,
    // garantindo o valor recém-incrementado)
    @Query(value = "select total_tentativas, total_acertos from palavra where id = :palavraId", nativeQuery = true)
    List<Object[]> buscarEstatistica(@Param("palavraId") long palavraId);
}
