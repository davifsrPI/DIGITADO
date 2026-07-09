package br.com.digitado.domain;

import br.com.digitado.domain.enumeration.ModoAtividade;
import br.com.digitado.domain.enumeration.StatusAtividade;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * A Atividade.
 */
@Entity
@Table(name = "atividade")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Atividade implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "titulo", nullable = false)
    private String titulo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "modo", nullable = false)
    private ModoAtividade modo;

    @Column(name = "data_inicio")
    private Instant dataInicio;

    @Column(name = "data_fim")
    private Instant dataFim;

    @Column(name = "tempo_limite")
    private Integer tempoLimite;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusAtividade status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sala_codigo")
    @JsonIgnoreProperties(value = { "professor", "alunos" }, allowSetters = true)
    private Sala sala;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "palavras", "professor" }, allowSetters = true)
    private ListaPalavras lista;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Atividade id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return this.titulo;
    }

    public Atividade titulo(String titulo) {
        this.setTitulo(titulo);
        return this;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public ModoAtividade getModo() {
        return this.modo;
    }

    public Atividade modo(ModoAtividade modo) {
        this.setModo(modo);
        return this;
    }

    public void setModo(ModoAtividade modo) {
        this.modo = modo;
    }

    public Instant getDataInicio() {
        return this.dataInicio;
    }

    public Atividade dataInicio(Instant dataInicio) {
        this.setDataInicio(dataInicio);
        return this;
    }

    public void setDataInicio(Instant dataInicio) {
        this.dataInicio = dataInicio;
    }

    public Instant getDataFim() {
        return this.dataFim;
    }

    public Atividade dataFim(Instant dataFim) {
        this.setDataFim(dataFim);
        return this;
    }

    public void setDataFim(Instant dataFim) {
        this.dataFim = dataFim;
    }

    public Integer getTempoLimite() {
        return this.tempoLimite;
    }

    public Atividade tempoLimite(Integer tempoLimite) {
        this.setTempoLimite(tempoLimite);
        return this;
    }

    public void setTempoLimite(Integer tempoLimite) {
        this.tempoLimite = tempoLimite;
    }

    public StatusAtividade getStatus() {
        return this.status;
    }

    public Atividade status(StatusAtividade status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(StatusAtividade status) {
        this.status = status;
    }

    public Sala getSala() {
        return this.sala;
    }

    public void setSala(Sala sala) {
        this.sala = sala;
    }

    public Atividade sala(Sala sala) {
        this.setSala(sala);
        return this;
    }

    public ListaPalavras getLista() {
        return this.lista;
    }

    public void setLista(ListaPalavras listaPalavras) {
        this.lista = listaPalavras;
    }

    public Atividade lista(ListaPalavras listaPalavras) {
        this.setLista(listaPalavras);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Atividade)) {
            return false;
        }
        return getId() != null && getId().equals(((Atividade) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Atividade{" +
            "id=" + getId() +
            ", titulo='" + getTitulo() + "'" +
            ", modo='" + getModo() + "'" +
            ", dataInicio='" + getDataInicio() + "'" +
            ", dataFim='" + getDataFim() + "'" +
            ", tempoLimite=" + getTempoLimite() +
            ", status='" + getStatus() + "'" +
            "}";
    }
}
