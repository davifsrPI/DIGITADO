package br.com.digitado.repository;

import br.com.digitado.domain.EstatisticaPartida;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Repositório do snapshot de estatísticas da última partida de cada sala.
@SuppressWarnings("unused")
@Repository
public interface EstatisticaPartidaRepository extends JpaRepository<EstatisticaPartida, Long> {
    Optional<EstatisticaPartida> findBySalaCodigo(String salaCodigo);

    // Quais das salas informadas já têm estatísticas gravadas - uma consulta só
    // para a lista "Minhas Salas" decidir onde mostrar o botão "Ver estatísticas"
    // (em vez de um exists por card)
    @Query("SELECT e.salaCodigo FROM EstatisticaPartida e WHERE e.salaCodigo IN :codigos")
    List<String> findSalaCodigosIn(@Param("codigos") Collection<String> codigos);
}
