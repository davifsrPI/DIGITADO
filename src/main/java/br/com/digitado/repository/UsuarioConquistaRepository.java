package br.com.digitado.repository;

import br.com.digitado.domain.UsuarioConquista;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the UsuarioConquista entity.
 */
@SuppressWarnings("unused")
@Repository
public interface UsuarioConquistaRepository extends JpaRepository<UsuarioConquista, Long> {
    // Busca todas as conquistas de um aluno já carregando a Conquista junto (fetch join),
    // evitando N+1 queries ao montar a tela de conquistas do usuário
    @Query("select uc from UsuarioConquista uc left join fetch uc.conquista where uc.aluno.id = :alunoId")
    List<UsuarioConquista> findByAlunoIdWithConquista(@Param("alunoId") Long alunoId);

    // Registro de progresso de UMA conquista para UM aluno - usado pelo motor de
    // conquistas. findFirst tolera eventuais duplicatas históricas.
    Optional<UsuarioConquista> findFirstByAlunoIdAndConquistaId(Long alunoId, Long conquistaId);
}
