package br.com.digitado.repository;

import br.com.digitado.domain.ParticipanteSala;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Identificação (nome real, turma, apelido) de cada aluno em cada sala.
@SuppressWarnings("unused")
@Repository
public interface ParticipanteSalaRepository extends JpaRepository<ParticipanteSala, Long> {
    Optional<ParticipanteSala> findBySalaCodigoAndLogin(String salaCodigo, String login);

    // Todos os alunos identificados na sala - alimenta a lista do professor, que
    // mapeia o login de cada linha do placar para o nome verdadeiro
    List<ParticipanteSala> findBySalaCodigoOrderByNomeAsc(String salaCodigo);
}
