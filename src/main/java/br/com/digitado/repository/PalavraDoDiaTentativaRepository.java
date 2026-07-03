package br.com.digitado.repository;

import br.com.digitado.domain.PalavraDoDiaTentativa;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the PalavraDoDiaTentativa entity.
 */
@Repository
public interface PalavraDoDiaTentativaRepository extends JpaRepository<PalavraDoDiaTentativa, Long> {
    // Tentativa do usuário logado no dia — é o que garante "uma chance por conta"
    Optional<PalavraDoDiaTentativa> findByDataAndLogin(LocalDate data, String login);

    boolean existsByDataAndLogin(LocalDate data, String login);
}
