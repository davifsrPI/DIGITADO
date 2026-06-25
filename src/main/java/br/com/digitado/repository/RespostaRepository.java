package br.com.digitado.repository;

import br.com.digitado.domain.Resposta;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface RespostaRepository extends JpaRepository<Resposta, Long> {
    List<Resposta> findByAlunoId(Long alunoId);
}
