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
    // Busca exata pelo texto, ignorando maiúsculas/minúsculas (usada para evitar duplicatas)
    Optional<Palavra> findByTextoIgnoreCase(String texto);

    // Retorna até 5 palavras ativas cujo texto contenha o trecho digitado — usada no autocomplete
    List<Palavra> findTop5ByTextoContainingIgnoreCaseAndAtivaTrue(String texto);

    // Sorteia N palavras ativas de uma determinada dificuldade usando ORDER BY RAND()
    // Usado ao iniciar o jogo para montar a lista de palavras da rodada
    @Query(value = "SELECT * FROM palavra WHERE dificuldade = :dif AND ativa = true ORDER BY RAND() LIMIT :n", nativeQuery = true)
    List<Palavra> findRandomByDificuldade(@Param("dif") String dif, @Param("n") int n);

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
