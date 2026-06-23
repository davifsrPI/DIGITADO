package br.com.digitado.domain;

import br.com.digitado.domain.enumeration.TipoErro;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.io.Serializable;

/**
 * A ErroOrtografico.
 */
@Entity
@Table(name = "erro_ortografico")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ErroOrtografico implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_erro")
    private TipoErro tipoErro;

    @Lob
    @Column(name = "descricao")
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "atividade", "aluno", "palavra" }, allowSetters = true)
    private Resposta resposta;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ErroOrtografico id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TipoErro getTipoErro() {
        return this.tipoErro;
    }

    public ErroOrtografico tipoErro(TipoErro tipoErro) {
        this.setTipoErro(tipoErro);
        return this;
    }

    public void setTipoErro(TipoErro tipoErro) {
        this.tipoErro = tipoErro;
    }

    public String getDescricao() {
        return this.descricao;
    }

    public ErroOrtografico descricao(String descricao) {
        this.setDescricao(descricao);
        return this;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Resposta getResposta() {
        return this.resposta;
    }

    public void setResposta(Resposta resposta) {
        this.resposta = resposta;
    }

    public ErroOrtografico resposta(Resposta resposta) {
        this.setResposta(resposta);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ErroOrtografico)) {
            return false;
        }
        return getId() != null && getId().equals(((ErroOrtografico) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ErroOrtografico{" +
            "id=" + getId() +
            ", tipoErro='" + getTipoErro() + "'" +
            ", descricao='" + getDescricao() + "'" +
            "}";
    }
}
