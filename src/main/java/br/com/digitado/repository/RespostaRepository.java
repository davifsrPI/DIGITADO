package br.com.digitado.repository;

import br.com.digitado.domain.Resposta;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Resposta entity.
 */
@SuppressWarnings("unused")
@Repository
public interface RespostaRepository extends JpaRepository<Resposta, Long> {}
