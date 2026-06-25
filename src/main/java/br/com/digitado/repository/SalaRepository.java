package br.com.digitado.repository;

import br.com.digitado.domain.Sala;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface SalaRepository extends JpaRepository<Sala, Long> {
    Optional<Sala> findByCodigo(String codigo);

    @Query("SELECT s FROM Sala s LEFT JOIN FETCH s.professor WHERE s.codigo = :codigo")
    Optional<Sala> findByCodigoWithProfessor(@Param("codigo") String codigo);

    List<Sala> findByAtivo(boolean ativo);
}
