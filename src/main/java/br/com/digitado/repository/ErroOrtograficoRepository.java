package br.com.digitado.repository;

import br.com.digitado.domain.ErroOrtografico;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ErroOrtografico entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ErroOrtograficoRepository extends JpaRepository<ErroOrtografico, Long> {}
