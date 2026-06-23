package br.com.digitado.repository;

import br.com.digitado.domain.ListaPalavras;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ListaPalavras entity.
 *
 * When extending this class, extend ListaPalavrasRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface ListaPalavrasRepository extends ListaPalavrasRepositoryWithBagRelationships, JpaRepository<ListaPalavras, Long> {
    default Optional<ListaPalavras> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findById(id));
    }

    default List<ListaPalavras> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAll());
    }

    default Page<ListaPalavras> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAll(pageable));
    }
}
