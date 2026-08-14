package br.com.digitado.repository;

import br.com.digitado.domain.Sala;
import br.com.digitado.domain.enumeration.TipoSala;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// Repositório JPA para a entidade Sala - o Spring gera a implementação automaticamente.
// A chave primária é o próprio código de acesso (ex: "8H4XEZ").
@SuppressWarnings("unused")
@Repository
public interface SalaRepository extends JpaRepository<Sala, String> {
    // Busca uma sala pelo código de acesso - equivale a findById, mantido pela legibilidade dos chamadores
    default Optional<Sala> findByCodigo(String codigo) {
        return findById(codigo);
    }

    // Busca a sala pelo código já carregando o professor junto (LEFT JOIN FETCH),
    // evitando LazyInitializationException ao acessar sala.getProfessor() fora de transação
    @Query("SELECT s FROM Sala s LEFT JOIN FETCH s.professor WHERE s.codigo = :codigo")
    Optional<Sala> findByCodigoWithProfessor(@Param("codigo") String codigo);

    // Lista todas as salas filtrando pelo campo ativo (true = ativas, false = inativas)
    List<Sala> findByAtivo(boolean ativo);

    // Duelos 1v1 públicos e ativos - aparecem na lista global para qualquer jogador entrar.
    // Ordenados dos mais recentes para os mais antigos.
    List<Sala> findByTipoAndPrivadaFalseAndAtivoTrueOrderByDataCriacaoDesc(TipoSala tipo);
}
