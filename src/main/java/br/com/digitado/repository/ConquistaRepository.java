package br.com.digitado.repository;

import br.com.digitado.domain.Conquista;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Conquista entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ConquistaRepository extends JpaRepository<Conquista, Long> {
    // Lookup do catálogo pelo nome (chave usada pelo motor de conquistas);
    // findFirst tolera nomes duplicados acidentais no catálogo
    Optional<Conquista> findFirstByNome(String nome);
}
