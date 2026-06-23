package br.com.digitado.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * A ListaPalavras.
 */
@Entity
@Table(name = "lista_palavras")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ListaPalavras implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "nome_lista", nullable = false)
    private String nomeLista;

    @Lob
    @Column(name = "descricao")
    private String descricao;

    @Column(name = "ativo")
    private Boolean ativo;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_lista_palavras__palavras",
        joinColumns = @JoinColumn(name = "lista_palavras_id"),
        inverseJoinColumns = @JoinColumn(name = "palavras_id")
    )
    @JsonIgnoreProperties(value = { "criador", "listas" }, allowSetters = true)
    private Set<Palavra> palavras = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "salas", "listasPalavras", "palavrasCriadas", "salasAlunos" }, allowSetters = true)
    private Usuario professor;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ListaPalavras id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNomeLista() {
        return this.nomeLista;
    }

    public ListaPalavras nomeLista(String nomeLista) {
        this.setNomeLista(nomeLista);
        return this;
    }

    public void setNomeLista(String nomeLista) {
        this.nomeLista = nomeLista;
    }

    public String getDescricao() {
        return this.descricao;
    }

    public ListaPalavras descricao(String descricao) {
        this.setDescricao(descricao);
        return this;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Boolean getAtivo() {
        return this.ativo;
    }

    public ListaPalavras ativo(Boolean ativo) {
        this.setAtivo(ativo);
        return this;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public Set<Palavra> getPalavras() {
        return this.palavras;
    }

    public void setPalavras(Set<Palavra> palavras) {
        this.palavras = palavras;
    }

    public ListaPalavras palavras(Set<Palavra> palavras) {
        this.setPalavras(palavras);
        return this;
    }

    public ListaPalavras addPalavras(Palavra palavra) {
        this.palavras.add(palavra);
        return this;
    }

    public ListaPalavras removePalavras(Palavra palavra) {
        this.palavras.remove(palavra);
        return this;
    }

    public Usuario getProfessor() {
        return this.professor;
    }

    public void setProfessor(Usuario usuario) {
        this.professor = usuario;
    }

    public ListaPalavras professor(Usuario usuario) {
        this.setProfessor(usuario);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ListaPalavras)) {
            return false;
        }
        return getId() != null && getId().equals(((ListaPalavras) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ListaPalavras{" +
            "id=" + getId() +
            ", nomeLista='" + getNomeLista() + "'" +
            ", descricao='" + getDescricao() + "'" +
            ", ativo='" + getAtivo() + "'" +
            "}";
    }
}
