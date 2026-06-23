package br.com.digitado.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * A Ranking.
 */
@Entity
@Table(name = "ranking")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Ranking implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "posicao")
    private Integer posicao;

    @Column(name = "pontuacao_total")
    private Integer pontuacaoTotal;

    @Column(name = "ultima_atualizacao")
    private Instant ultimaAtualizacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "professor", "alunos" }, allowSetters = true)
    private Sala sala;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "salas", "listasPalavras", "palavrasCriadas", "salasAlunos" }, allowSetters = true)
    private Usuario aluno;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Ranking id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPosicao() {
        return this.posicao;
    }

    public Ranking posicao(Integer posicao) {
        this.setPosicao(posicao);
        return this;
    }

    public void setPosicao(Integer posicao) {
        this.posicao = posicao;
    }

    public Integer getPontuacaoTotal() {
        return this.pontuacaoTotal;
    }

    public Ranking pontuacaoTotal(Integer pontuacaoTotal) {
        this.setPontuacaoTotal(pontuacaoTotal);
        return this;
    }

    public void setPontuacaoTotal(Integer pontuacaoTotal) {
        this.pontuacaoTotal = pontuacaoTotal;
    }

    public Instant getUltimaAtualizacao() {
        return this.ultimaAtualizacao;
    }

    public Ranking ultimaAtualizacao(Instant ultimaAtualizacao) {
        this.setUltimaAtualizacao(ultimaAtualizacao);
        return this;
    }

    public void setUltimaAtualizacao(Instant ultimaAtualizacao) {
        this.ultimaAtualizacao = ultimaAtualizacao;
    }

    public Sala getSala() {
        return this.sala;
    }

    public void setSala(Sala sala) {
        this.sala = sala;
    }

    public Ranking sala(Sala sala) {
        this.setSala(sala);
        return this;
    }

    public Usuario getAluno() {
        return this.aluno;
    }

    public void setAluno(Usuario usuario) {
        this.aluno = usuario;
    }

    public Ranking aluno(Usuario usuario) {
        this.setAluno(usuario);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ranking)) {
            return false;
        }
        return getId() != null && getId().equals(((Ranking) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Ranking{" +
            "id=" + getId() +
            ", posicao=" + getPosicao() +
            ", pontuacaoTotal=" + getPontuacaoTotal() +
            ", ultimaAtualizacao='" + getUltimaAtualizacao() + "'" +
            "}";
    }
}
