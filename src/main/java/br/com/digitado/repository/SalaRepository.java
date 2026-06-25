package br.com.digitado.repository;

import br.com.digitado.domain.Sala;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface SalaRepository extends JpaRepository<Sala, Long> {
    Optional<Sala> findByCodigo(String codigo);

    List<Sala> findByAtivo(boolean ativo);
}
