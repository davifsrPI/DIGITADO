package br.com.digitado.domain;

import br.com.digitado.domain.enumeration.Dificuldade;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * A Palavra.
 */
@Entity
@Table(name = "palavra")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Palavra implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "texto", nullable = false)
    private String texto;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "dificuldade", nullable = false)
    private Dificuldade dificuldade;

    @Column(name = "categoria")
    private String categoria;

    @Column(name = "idioma")
    private String idioma;

    @Column(name = "possui_acento")
    private Boolean possuiAcento;

    @Column(name = "ativa")
    private Boolean ativa;

    /**
     * Estatísticas da palavra: total de pessoas que a fizeram (acertando ou não)
     * e total de acertos — somando palavra do dia e partidas.
     *
     * - @JsonIgnore: os contadores nunca saem nas respostas da API que serializam
     *   Palavra (o backend só expõe quando quer, via VM da palavra do dia);
     * - insertable/updatable = false: o JPA nunca escreve nessas colunas — o
     *   incremento é feito exclusivamente por SQL atômico (PalavraRepository),
     *   então o CRUD de palavras não zera nem adultera os números.
     */
    @JsonIgnore
    @Column(name = "total_tentativas", insertable = false, updatable = false)
    private Long totalTentativas;

    @JsonIgnore
    @Column(name = "total_acertos", insertable = false, updatable = false)
    private Long totalAcertos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "salas", "listasPalavras", "palavrasCriadas", "salasAlunos" }, allowSetters = true)
    private Usuario criador;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "palavras")
    @JsonIgnoreProperties(value = { "palavras", "professor" }, allowSetters = true)
    private Set<ListaPalavras> listas = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getTotalTentativas() {
        return totalTentativas != null ? totalTentativas : 0L;
    }

    public Long getTotalAcertos() {
        return totalAcertos != null ? totalAcertos : 0L;
    }

    public Long getId() {
        return this.id;
    }

    public Palavra id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTexto() {
        return this.texto;
    }

    public Palavra texto(String texto) {
        this.setTexto(texto);
        return this;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public Dificuldade getDificuldade() {
        return this.dificuldade;
    }

    public Palavra dificuldade(Dificuldade dificuldade) {
        this.setDificuldade(dificuldade);
        return this;
    }

    public void setDificuldade(Dificuldade dificuldade) {
        this.dificuldade = dificuldade;
    }

    public String getCategoria() {
        return this.categoria;
    }

    public Palavra categoria(String categoria) {
        this.setCategoria(categoria);
        return this;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getIdioma() {
        return this.idioma;
    }

    public Palavra idioma(String idioma) {
        this.setIdioma(idioma);
        return this;
    }

    public void setIdioma(String idioma) {
        this.idioma = idioma;
    }

    public Boolean getPossuiAcento() {
        return this.possuiAcento;
    }

    public Palavra possuiAcento(Boolean possuiAcento) {
        this.setPossuiAcento(possuiAcento);
        return this;
    }

    public void setPossuiAcento(Boolean possuiAcento) {
        this.possuiAcento = possuiAcento;
    }

    public Boolean getAtiva() {
        return this.ativa;
    }

    public Palavra ativa(Boolean ativa) {
        this.setAtiva(ativa);
        return this;
    }

    public void setAtiva(Boolean ativa) {
        this.ativa = ativa;
    }

    public Usuario getCriador() {
        return this.criador;
    }

    public void setCriador(Usuario usuario) {
        this.criador = usuario;
    }

    public Palavra criador(Usuario usuario) {
        this.setCriador(usuario);
        return this;
    }

    public Set<ListaPalavras> getListas() {
        return this.listas;
    }

    public void setListas(Set<ListaPalavras> listaPalavras) {
        if (this.listas != null) {
            this.listas.forEach(i -> i.removePalavras(this));
        }
        if (listaPalavras != null) {
            listaPalavras.forEach(i -> i.addPalavras(this));
        }
        this.listas = listaPalavras;
    }

    public Palavra listas(Set<ListaPalavras> listaPalavras) {
        this.setListas(listaPalavras);
        return this;
    }

    public Palavra addListas(ListaPalavras listaPalavras) {
        this.listas.add(listaPalavras);
        listaPalavras.getPalavras().add(this);
        return this;
    }

    public Palavra removeListas(ListaPalavras listaPalavras) {
        this.listas.remove(listaPalavras);
        listaPalavras.getPalavras().remove(this);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Palavra)) {
            return false;
        }
        return getId() != null && getId().equals(((Palavra) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Palavra{" +
            "id=" + getId() +
            ", texto='" + getTexto() + "'" +
            ", dificuldade='" + getDificuldade() + "'" +
            ", categoria='" + getCategoria() + "'" +
            ", idioma='" + getIdioma() + "'" +
            ", possuiAcento='" + getPossuiAcento() + "'" +
            ", ativa='" + getAtiva() + "'" +
            "}";
    }
}
