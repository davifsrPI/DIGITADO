package br.com.digitado.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * A UsuarioConquista.
 */
@Entity
@Table(name = "usuario_conquista")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UsuarioConquista implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "data_conquista")
    private Instant dataConquista;

    @Column(name = "progresso")
    private Integer progresso;

    @Column(name = "concluida")
    private Boolean concluida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "salas", "listasPalavras", "palavrasCriadas", "salasAlunos" }, allowSetters = true)
    private Usuario aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    private Conquista conquista;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public UsuarioConquista id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getDataConquista() {
        return this.dataConquista;
    }

    public UsuarioConquista dataConquista(Instant dataConquista) {
        this.setDataConquista(dataConquista);
        return this;
    }

    public void setDataConquista(Instant dataConquista) {
        this.dataConquista = dataConquista;
    }

    public Integer getProgresso() {
        return this.progresso;
    }

    public UsuarioConquista progresso(Integer progresso) {
        this.setProgresso(progresso);
        return this;
    }

    public void setProgresso(Integer progresso) {
        this.progresso = progresso;
    }

    public Boolean getConcluida() {
        return this.concluida;
    }

    public UsuarioConquista concluida(Boolean concluida) {
        this.setConcluida(concluida);
        return this;
    }

    public void setConcluida(Boolean concluida) {
        this.concluida = concluida;
    }

    public Usuario getAluno() {
        return this.aluno;
    }

    public void setAluno(Usuario usuario) {
        this.aluno = usuario;
    }

    public UsuarioConquista aluno(Usuario usuario) {
        this.setAluno(usuario);
        return this;
    }

    public Conquista getConquista() {
        return this.conquista;
    }

    public void setConquista(Conquista conquista) {
        this.conquista = conquista;
    }

    public UsuarioConquista conquista(Conquista conquista) {
        this.setConquista(conquista);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsuarioConquista)) {
            return false;
        }
        return getId() != null && getId().equals(((UsuarioConquista) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UsuarioConquista{" +
            "id=" + getId() +
            ", dataConquista='" + getDataConquista() + "'" +
            ", progresso=" + getProgresso() +
            ", concluida='" + getConcluida() + "'" +
            "}";
    }
}
