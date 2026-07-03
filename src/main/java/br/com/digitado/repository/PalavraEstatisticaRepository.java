package br.com.digitado.repository;

import br.com.digitado.domain.PalavraEstatistica;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the PalavraEstatistica entity.
 */
@Repository
public interface PalavraEstatisticaRepository extends JpaRepository<PalavraEstatistica, Long> {
    Optional<PalavraEstatistica> findByPalavraId(Long palavraId);

    // Upsert atômico (MySQL): cria a linha da palavra na primeira tentativa e depois
    // só incrementa os contadores — seguro mesmo com vários jogadores respondendo
    // ao mesmo tempo em salas diferentes
    @Modifying
    @Query(
        value = "insert into palavra_estatistica (palavra_id, total_tentativas, total_acertos) values (:palavraId, 1, :acerto) " +
        "on duplicate key update total_tentativas = total_tentativas + 1, total_acertos = total_acertos + :acerto",
        nativeQuery = true
    )
    void incrementarTentativa(@Param("palavraId") long palavraId, @Param("acerto") int acerto);
}
