package br.com.digitado.repository;

import br.com.digitado.domain.Palavra;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Palavra entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PalavraRepository extends JpaRepository<Palavra, Long> {
    Optional<Palavra> findByTextoIgnoreCase(String texto);

    List<Palavra> findTop5ByTextoContainingIgnoreCaseAndAtivaTrue(String texto);
}
