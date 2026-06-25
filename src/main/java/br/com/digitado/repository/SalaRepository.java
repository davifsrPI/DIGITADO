package br.com.digitado.repository;

import br.com.digitado.domain.Sala;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Repositório JPA para a entidade Sala — o Spring gera a implementação automaticamente
@SuppressWarnings("unused")
@Repository
public interface SalaRepository extends JpaRepository<Sala, Long> {
    // Busca uma sala pelo código de acesso (ex: "8H4XEZ")
    Optional<Sala> findByCodigo(String codigo);

    // Busca a sala pelo código já carregando o professor junto (LEFT JOIN FETCH),
    // evitando LazyInitializationException ao acessar sala.getProfessor() fora de transação
    @Query("SELECT s FROM Sala s LEFT JOIN FETCH s.professor WHERE s.codigo = :codigo")
    Optional<Sala> findByCodigoWithProfessor(@Param("codigo") String codigo);

    // Lista todas as salas filtrando pelo campo ativo (true = ativas, false = inativas)
    List<Sala> findByAtivo(boolean ativo);
}
