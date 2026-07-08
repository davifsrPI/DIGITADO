package br.com.digitado.repository;

import br.com.digitado.domain.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Usuario entity.
 *
 * When extending this class, extend UsuarioRepositoryWithBagRelationships too.
 * For more information refer to https://github.com/jhipster/generator-jhipster/issues/17990.
 */
@Repository
public interface UsuarioRepository extends UsuarioRepositoryWithBagRelationships, JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);

    // Incremento atômico do XP — único ponto que escreve na coluna (a entidade
    // mapeia xp como insertable/updatable = false). Seguro sob concorrência.
    @Modifying
    @Query(value = "update usuario set xp = xp + :delta where id = :usuarioId", nativeQuery = true)
    void incrementarXp(@Param("usuarioId") long usuarioId, @Param("delta") long delta);

    // Top 50 do Ranking Mundial, do maior para o menor XP
    List<Usuario> findTop50ByOrderByXpDescIdAsc();

    // Posição do usuário no ranking = quantos têm XP maior + 1
    long countByXpGreaterThan(Long xp);

    // Leitura direta do XP no banco — a entidade mapeia xp como insertable/updatable
    // = false, então após incrementarXp o valor em memória fica defasado
    @Query(value = "select xp from usuario where id = :usuarioId", nativeQuery = true)
    Long lerXp(@Param("usuarioId") long usuarioId);

    default Optional<Usuario> findOneWithEagerRelationships(Long id) {
        return this.fetchBagRelationships(this.findById(id));
    }

    default List<Usuario> findAllWithEagerRelationships() {
        return this.fetchBagRelationships(this.findAll());
    }

    default Page<Usuario> findAllWithEagerRelationships(Pageable pageable) {
        return this.fetchBagRelationships(this.findAll(pageable));
    }
}
