package br.com.digitado.repository;

import br.com.digitado.domain.Conquista;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Conquista entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ConquistaRepository extends JpaRepository<Conquista, Long> {}
