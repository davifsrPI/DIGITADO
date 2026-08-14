package br.com.digitado.domain;

import br.com.digitado.domain.converter.DificuldadeConverter;
import br.com.digitado.domain.enumeration.Dificuldade;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Column(name = "categoria")
    private String categoria;

    @Column(name = "idioma")
    private String idioma;

    // Dica exibida ao jogador na Palavra do Dia (ex: "fruta amarela") - opcional
    @Column(name = "dica")
    private String dica;

    @Column(name = "possui_acento")
    private Boolean possuiAcento;

    @Column(name = "ativa")
    private Boolean ativa;

    /**
     * Dificuldade CADASTRADA (opcional, definida na curadoria da palavra).
     * Só vale enquanto a palavra tem pouca amostra: com menos de
     * {@link #MIN_TENTATIVAS_PARA_METRICA} tentativas, getDificuldade() usa este
     * valor; a partir daí, a dificuldade passa a ser a métrica calculada pela
     * taxa de acerto e este campo deixa de influenciar.
     *
     * Conversor próprio no lugar de @Enumerated(STRING): palavras importadas
     * direto no banco podem trazer 'facil'/'media'/'dificil' - o conversor tolera
     * essas variações em vez de estourar IllegalArgumentException na leitura.
     */
    @Convert(converter = DificuldadeConverter.class)
    @Column(name = "dificuldade")
    private Dificuldade dificuldadeCadastrada;

    /**
     * Estatísticas da palavra: total de pessoas que a fizeram (acertando ou não)
     * e total de acertos - somando palavra do dia e partidas.
     *
     * - @JsonIgnore: os contadores nunca saem nas respostas da API que serializam
     *   Palavra (o backend só expõe quando quer, via VM da palavra do dia);
     * - insertable/updatable = false: o JPA nunca escreve nessas colunas - o
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

    /**
     * A dificuldade só passa a ser CALCULADA pela taxa de acerto quando a palavra
     * acumula esta quantidade mínima de tentativas - abaixo disso a amostra é
     * pequena demais e vale a dificuldade cadastrada. O mesmo limiar está fixo no
     * SQL de PalavraRepository.findRandomByDificuldade - mantenha os dois iguais.
     */
    public static final int MIN_TENTATIVAS_PARA_METRICA = 15;

    /**
     * Dificuldade efetiva da palavra:
     *
     * Com pelo menos {@link #MIN_TENTATIVAS_PARA_METRICA} tentativas, é CALCULADA
     * pela taxa de acerto:
     *
     *   percentual = total_acertos / total_tentativas
     *   0-35%  -> DIFICIL   (poucas pessoas acertam)
     *   36-65% -> MEDIO
     *   66%+   -> FACIL     (maioria acerta)
     *
     * Com menos tentativas que isso, vale a dificuldade CADASTRADA na coluna
     * dificuldade; se também não houver cadastro, a palavra entra "aleatoriamente"
     * numa das três faixas - de forma determinística pelo id (id % 3), porque a
     * classificação precisa ser estável: o tempo da rodada é recalculado a partir
     * dela durante o jogo, e o sorteio em SQL precisa classificar exatamente igual.
     * A mesma regra existe em SQL no PalavraRepository.findRandomByDificuldade -
     * mantenha as duas em sincronia.
     *
     * Sem @Transient: a entidade usa acesso por campo, então o Hibernate já ignora
     * esse getter, e o @Transient acabaria tirando a propriedade do JSON.
     */
    /**
     * Indica se a palavra já tem estatística registrada (alguém já a jogou).
     * Usado pelo frontend para não exibir a dificuldade provisória (id % 3)
     * como se fosse real - sem registros, a UI mostra "Sem registros".
     */
    @JsonProperty("temRegistros")
    public boolean getTemRegistros() {
        return getTotalTentativas() > 0;
    }

    @JsonProperty("dificuldade")
    public Dificuldade getDificuldade() {
        long tentativas = getTotalTentativas();
        if (tentativas < MIN_TENTATIVAS_PARA_METRICA) {
            if (dificuldadeCadastrada != null) {
                return dificuldadeCadastrada;
            }
            if (id == null) {
                return Dificuldade.MEDIO;
            }
            return switch ((int) (id % 3)) {
                case 0 -> Dificuldade.FACIL;
                case 1 -> Dificuldade.MEDIO;
                default -> Dificuldade.DIFICIL;
            };
        }
        double percentual = (getTotalAcertos() * 100.0) / tentativas;
        if (percentual <= 35) {
            return Dificuldade.DIFICIL;
        }
        if (percentual <= 65) {
            return Dificuldade.MEDIO;
        }
        return Dificuldade.FACIL;
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

    public String getDica() {
        return this.dica;
    }

    public Palavra dica(String dica) {
        this.setDica(dica);
        return this;
    }

    public void setDica(String dica) {
        this.dica = dica;
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

    public Dificuldade getDificuldadeCadastrada() {
        return this.dificuldadeCadastrada;
    }

    public Palavra dificuldadeCadastrada(Dificuldade dificuldadeCadastrada) {
        this.setDificuldadeCadastrada(dificuldadeCadastrada);
        return this;
    }

    public void setDificuldadeCadastrada(Dificuldade dificuldadeCadastrada) {
        this.dificuldadeCadastrada = dificuldadeCadastrada;
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
