package br.com.digitado.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * A Resposta.
 */
@Entity
@Table(name = "resposta")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Resposta implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "resposta_digitada")
    private String respostaDigitada;

    @Column(name = "correta")
    private Boolean correta;

    @Column(name = "tempo_resposta")
    private Integer tempoResposta;

    @Column(name = "pontuacao")
    private Integer pontuacao;

    @Column(name = "data_resposta")
    private Instant dataResposta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "sala", "lista" }, allowSetters = true)
    private Atividade atividade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "salas", "listasPalavras", "palavrasCriadas", "salasAlunos" }, allowSetters = true)
    private Usuario aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "criador", "listas" }, allowSetters = true)
    private Palavra palavra;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Resposta id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRespostaDigitada() {
        return this.respostaDigitada;
    }

    public Resposta respostaDigitada(String respostaDigitada) {
        this.setRespostaDigitada(respostaDigitada);
        return this;
    }

    public void setRespostaDigitada(String respostaDigitada) {
        this.respostaDigitada = respostaDigitada;
    }

    public Boolean getCorreta() {
        return this.correta;
    }

    public Resposta correta(Boolean correta) {
        this.setCorreta(correta);
        return this;
    }

    public void setCorreta(Boolean correta) {
        this.correta = correta;
    }

    public Integer getTempoResposta() {
        return this.tempoResposta;
    }

    public Resposta tempoResposta(Integer tempoResposta) {
        this.setTempoResposta(tempoResposta);
        return this;
    }

    public void setTempoResposta(Integer tempoResposta) {
        this.tempoResposta = tempoResposta;
    }

    public Integer getPontuacao() {
        return this.pontuacao;
    }

    public Resposta pontuacao(Integer pontuacao) {
        this.setPontuacao(pontuacao);
        return this;
    }

    public void setPontuacao(Integer pontuacao) {
        this.pontuacao = pontuacao;
    }

    public Instant getDataResposta() {
        return this.dataResposta;
    }

    public Resposta dataResposta(Instant dataResposta) {
        this.setDataResposta(dataResposta);
        return this;
    }

    public void setDataResposta(Instant dataResposta) {
        this.dataResposta = dataResposta;
    }

    public Atividade getAtividade() {
        return this.atividade;
    }

    public void setAtividade(Atividade atividade) {
        this.atividade = atividade;
    }

    public Resposta atividade(Atividade atividade) {
        this.setAtividade(atividade);
        return this;
    }

    public Usuario getAluno() {
        return this.aluno;
    }

    public void setAluno(Usuario usuario) {
        this.aluno = usuario;
    }

    public Resposta aluno(Usuario usuario) {
        this.setAluno(usuario);
        return this;
    }

    public Palavra getPalavra() {
        return this.palavra;
    }

    public void setPalavra(Palavra palavra) {
        this.palavra = palavra;
    }

    public Resposta palavra(Palavra palavra) {
        this.setPalavra(palavra);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Resposta)) {
            return false;
        }
        return getId() != null && getId().equals(((Resposta) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Resposta{" +
            "id=" + getId() +
            ", respostaDigitada='" + getRespostaDigitada() + "'" +
            ", correta='" + getCorreta() + "'" +
            ", tempoResposta=" + getTempoResposta() +
            ", pontuacao=" + getPontuacao() +
            ", dataResposta='" + getDataResposta() + "'" +
            "}";
    }
}
