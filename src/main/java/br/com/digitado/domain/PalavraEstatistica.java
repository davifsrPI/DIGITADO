package br.com.digitado.domain;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Estatísticas agregadas de uma palavra: quantas pessoas a tentaram (na palavra
 * do dia ou nas partidas) e quantas acertaram.
 *
 * Os contadores são incrementados EXCLUSIVAMENTE pelo backend, no momento em que
 * ele valida a resposta (PalavraDoDiaService e JogoSalaService) — o frontend nunca
 * envia contadores, só a resposta digitada, então não há como forjar acertos.
 */
@Entity
@Table(name = "palavra_estatistica")
public class PalavraEstatistica implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Uma linha por palavra (unique) — referencia palavra.id
    @Column(name = "palavra_id", nullable = false, unique = true)
    private Long palavraId;

    // Total de pessoas que tentaram a palavra (acertando ou não)
    @Column(name = "total_tentativas", nullable = false)
    private Long totalTentativas = 0L;

    // Quantas dessas tentativas foram corretas
    @Column(name = "total_acertos", nullable = false)
    private Long totalAcertos = 0L;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPalavraId() {
        return palavraId;
    }

    public void setPalavraId(Long palavraId) {
        this.palavraId = palavraId;
    }

    public Long getTotalTentativas() {
        return totalTentativas;
    }

    public void setTotalTentativas(Long totalTentativas) {
        this.totalTentativas = totalTentativas;
    }

    public Long getTotalAcertos() {
        return totalAcertos;
    }

    public void setTotalAcertos(Long totalAcertos) {
        this.totalAcertos = totalAcertos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalavraEstatistica)) return false;
        return getId() != null && getId().equals(((PalavraEstatistica) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "PalavraEstatistica{id=" +
            getId() +
            ", palavraId=" +
            getPalavraId() +
            ", tentativas=" +
            getTotalTentativas() +
            ", acertos=" +
            getTotalAcertos() +
            "}"
        );
    }
}
