package br.com.digitado.repository;

import br.com.digitado.domain.UsuarioConquista;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the UsuarioConquista entity.
 */
@SuppressWarnings("unused")
@Repository
public interface UsuarioConquistaRepository extends JpaRepository<UsuarioConquista, Long> {}
