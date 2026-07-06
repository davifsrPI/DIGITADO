package br.com.digitado.repository;

import br.com.digitado.domain.PalavraDoDiaTentativa;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the PalavraDoDiaTentativa entity.
 */
@Repository
public interface PalavraDoDiaTentativaRepository extends JpaRepository<PalavraDoDiaTentativa, Long> {
    // Tentativa do usuário logado no dia — é o que garante "uma chance por conta"
    Optional<PalavraDoDiaTentativa> findByDataAndLogin(LocalDate data, String login);

    boolean existsByDataAndLogin(LocalDate data, String login);

    // Retenção (LGPD arts. 15/16): o login só é necessário para travar a segunda
    // tentativa DO DIA; depois do prazo, anonimiza — a estatística agregada fica,
    // o vínculo com a pessoa desaparece
    @Modifying
    @Query("update PalavraDoDiaTentativa t set t.login = null where t.login is not null and t.data < :corte")
    int anonimizarAnterioresA(@Param("corte") LocalDate corte);
}
