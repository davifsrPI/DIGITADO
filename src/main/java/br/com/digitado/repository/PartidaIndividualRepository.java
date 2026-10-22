package br.com.digitado.repository;

import br.com.digitado.domain.PartidaIndividual;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Partidas encerradas do modo individual - recorde e últimas partidas do jogador.
@SuppressWarnings("unused")
@Repository
public interface PartidaIndividualRepository extends JpaRepository<PartidaIndividual, Long> {
    // O recorde: mais acertos e, no empate, a partida mais antiga (quem chegou lá primeiro)
    Optional<PartidaIndividual> findFirstByLoginOrderByAcertosDescDataFimAsc(String login);

    List<PartidaIndividual> findTop5ByLoginOrderByDataFimDesc(String login);

    // Tela de consulta do admin (AdminTabelasResource): paginada, com busca pelo login
    Page<PartidaIndividual> findByLoginContainingIgnoreCase(String login, Pageable pageable);
}
