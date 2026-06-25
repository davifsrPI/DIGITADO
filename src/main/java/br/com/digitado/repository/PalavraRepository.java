package br.com.digitado.repository;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Dificuldade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface PalavraRepository extends JpaRepository<Palavra, Long> {
    Optional<Palavra> findByTextoIgnoreCase(String texto);

    List<Palavra> findTop5ByTextoContainingIgnoreCaseAndAtivaTrue(String texto);

    @Query(value = "SELECT * FROM palavra WHERE dificuldade = :dif AND ativa = true ORDER BY RAND() LIMIT :n", nativeQuery = true)
    List<Palavra> findRandomByDificuldade(@Param("dif") String dif, @Param("n") int n);
}
