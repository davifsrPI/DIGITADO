package br.com.digitado.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A Sala.
 */
@Entity
@Table(name = "sala")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Sala implements Serializable {

    private static final long serialVersionUID = 1L;

    // O código de acesso (ex: "8H4XEZ") é a chave primária da sala — não existe id numérico
    @Id
    @NotNull
    @Column(name = "codigo", nullable = false)
    private String codigo;

    @NotNull
    @Column(name = "nome", nullable = false)
    private String nome;

    @Lob
    @Column(name = "descricao")
    private String descricao;

    @Column(name = "ativo")
    private Boolean ativo;

    /**
     * Data/hora em que a sala foi criada. Junto com o código (que tem constraint
     * única no banco — ux_sala__codigo), identifica a sala sem ambiguidade.
     * updatable = false: uma vez criada, a data nunca muda (nem via PUT).
     */
    @Column(name = "data_criacao", updatable = false)
    private Instant dataCriacao = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "salas", "listasPalavras", "palavrasCriadas", "salasAlunos" }, allowSetters = true)
    private Usuario professor;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "salasAlunos")
    @JsonIgnoreProperties(value = { "salas", "listasPalavras", "palavrasCriadas", "salasAlunos" }, allowSetters = true)
    private Set<Usuario> alunos = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(Instant dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public Sala dataCriacao(Instant dataCriacao) {
        this.setDataCriacao(dataCriacao);
        return this;
    }

    public String getNome() {
        return this.nome;
    }

    public Sala nome(String nome) {
        this.setNome(nome);
        return this;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCodigo() {
        return this.codigo;
    }

    public Sala codigo(String codigo) {
        this.setCodigo(codigo);
        return this;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescricao() {
        return this.descricao;
    }

    public Sala descricao(String descricao) {
        this.setDescricao(descricao);
        return this;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Boolean getAtivo() {
        return this.ativo;
    }

    public Sala ativo(Boolean ativo) {
        this.setAtivo(ativo);
        return this;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public Usuario getProfessor() {
        return this.professor;
    }

    public void setProfessor(Usuario usuario) {
        this.professor = usuario;
    }

    public Sala professor(Usuario usuario) {
        this.setProfessor(usuario);
        return this;
    }

    public Set<Usuario> getAlunos() {
        return this.alunos;
    }

    public void setAlunos(Set<Usuario> usuarios) {
        if (this.alunos != null) {
            this.alunos.forEach(i -> i.removeSalasAluno(this));
        }
        if (usuarios != null) {
            usuarios.forEach(i -> i.addSalasAluno(this));
        }
        this.alunos = usuarios;
    }

    public Sala alunos(Set<Usuario> usuarios) {
        this.setAlunos(usuarios);
        return this;
    }

    public Sala addAlunos(Usuario usuario) {
        this.alunos.add(usuario);
        usuario.getSalasAlunos().add(this);
        return this;
    }

    public Sala removeAlunos(Usuario usuario) {
        this.alunos.remove(usuario);
        usuario.getSalasAlunos().remove(this);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Sala)) {
            return false;
        }
        return getCodigo() != null && getCodigo().equals(((Sala) o).getCodigo());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Sala{" +
            "codigo='" + getCodigo() + "'" +
            ", nome='" + getNome() + "'" +
            ", descricao='" + getDescricao() + "'" +
            ", ativo='" + getAtivo() + "'" +
            "}";
    }
}
