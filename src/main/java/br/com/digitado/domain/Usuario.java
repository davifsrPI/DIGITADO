package br.com.digitado.domain;

import br.com.digitado.domain.enumeration.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * A Usuario.
 */
@Entity
@Table(name = "usuario")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "nome", nullable = false)
    private String nome;

    // Sobrenome é opcional: a validação da conta pelo admin não deve forçar
    // preenchê-lo (registro público nem coleta esse campo)
    @Column(name = "sobrenome")
    private String sobrenome;

    /**
     * XP acumulado do usuário (palavra do dia, conquistas...) — alimenta o Ranking Mundial.
     * insertable/updatable = false: o JPA nunca escreve nesta coluna — o incremento é
     * feito exclusivamente por SQL atômico (UsuarioRepository.incrementarXp), então o
     * CRUD de usuários não zera nem adultera o valor.
     */
    @Column(name = "xp", insertable = false, updatable = false)
    private Long xp;

    public Long getXp() {
        return xp != null ? xp : 0L;
    }

    @NotNull
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    // WRITE_ONLY: a senha pode ser ENVIADA (ex.: criação de usuário, onde é
    // guardada com bcrypt) mas NUNCA é serializada nas respostas da API —
    // nenhum GET expõe o hash. @JsonIgnore bloquearia também a escrita, o que
    // tornaria a criação impossível (o campo é obrigatório no banco).
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "senha", nullable = false)
    private String senha;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario;

    @Column(name = "ativo")
    private Boolean ativo;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professor")
    @JsonIgnoreProperties(value = { "professor", "alunos" }, allowSetters = true)
    private Set<Sala> salas = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "professor")
    @JsonIgnoreProperties(value = { "palavras", "professor" }, allowSetters = true)
    private Set<ListaPalavras> listasPalavras = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "criador")
    @JsonIgnoreProperties(value = { "criador", "listas" }, allowSetters = true)
    private Set<Palavra> palavrasCriadas = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_usuario__salas_aluno",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "salas_aluno_codigo")
    )
    @JsonIgnoreProperties(value = { "professor", "alunos" }, allowSetters = true)
    private Set<Sala> salasAlunos = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public Usuario id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return this.nome;
    }

    public Usuario nome(String nome) {
        this.setNome(nome);
        return this;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSobrenome() {
        return this.sobrenome;
    }

    public Usuario sobrenome(String sobrenome) {
        this.setSobrenome(sobrenome);
        return this;
    }

    public void setSobrenome(String sobrenome) {
        this.sobrenome = sobrenome;
    }

    public String getEmail() {
        return this.email;
    }

    public Usuario email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return this.senha;
    }

    public Usuario senha(String senha) {
        this.setSenha(senha);
        return this;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public TipoUsuario getTipoUsuario() {
        return this.tipoUsuario;
    }

    public Usuario tipoUsuario(TipoUsuario tipoUsuario) {
        this.setTipoUsuario(tipoUsuario);
        return this;
    }

    public void setTipoUsuario(TipoUsuario tipoUsuario) {
        this.tipoUsuario = tipoUsuario;
    }

    public Boolean getAtivo() {
        return this.ativo;
    }

    public Usuario ativo(Boolean ativo) {
        this.setAtivo(ativo);
        return this;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public Set<Sala> getSalas() {
        return this.salas;
    }

    public void setSalas(Set<Sala> salas) {
        if (this.salas != null) {
            this.salas.forEach(i -> i.setProfessor(null));
        }
        if (salas != null) {
            salas.forEach(i -> i.setProfessor(this));
        }
        this.salas = salas;
    }

    public Usuario salas(Set<Sala> salas) {
        this.setSalas(salas);
        return this;
    }

    public Usuario addSalas(Sala sala) {
        this.salas.add(sala);
        sala.setProfessor(this);
        return this;
    }

    public Usuario removeSalas(Sala sala) {
        this.salas.remove(sala);
        sala.setProfessor(null);
        return this;
    }

    public Set<ListaPalavras> getListasPalavras() {
        return this.listasPalavras;
    }

    public void setListasPalavras(Set<ListaPalavras> listaPalavras) {
        if (this.listasPalavras != null) {
            this.listasPalavras.forEach(i -> i.setProfessor(null));
        }
        if (listaPalavras != null) {
            listaPalavras.forEach(i -> i.setProfessor(this));
        }
        this.listasPalavras = listaPalavras;
    }

    public Usuario listasPalavras(Set<ListaPalavras> listaPalavras) {
        this.setListasPalavras(listaPalavras);
        return this;
    }

    public Usuario addListasPalavras(ListaPalavras listaPalavras) {
        this.listasPalavras.add(listaPalavras);
        listaPalavras.setProfessor(this);
        return this;
    }

    public Usuario removeListasPalavras(ListaPalavras listaPalavras) {
        this.listasPalavras.remove(listaPalavras);
        listaPalavras.setProfessor(null);
        return this;
    }

    public Set<Palavra> getPalavrasCriadas() {
        return this.palavrasCriadas;
    }

    public void setPalavrasCriadas(Set<Palavra> palavras) {
        if (this.palavrasCriadas != null) {
            this.palavrasCriadas.forEach(i -> i.setCriador(null));
        }
        if (palavras != null) {
            palavras.forEach(i -> i.setCriador(this));
        }
        this.palavrasCriadas = palavras;
    }

    public Usuario palavrasCriadas(Set<Palavra> palavras) {
        this.setPalavrasCriadas(palavras);
        return this;
    }

    public Usuario addPalavrasCriadas(Palavra palavra) {
        this.palavrasCriadas.add(palavra);
        palavra.setCriador(this);
        return this;
    }

    public Usuario removePalavrasCriadas(Palavra palavra) {
        this.palavrasCriadas.remove(palavra);
        palavra.setCriador(null);
        return this;
    }

    public Set<Sala> getSalasAlunos() {
        return this.salasAlunos;
    }

    public void setSalasAlunos(Set<Sala> salas) {
        this.salasAlunos = salas;
    }

    public Usuario salasAlunos(Set<Sala> salas) {
        this.setSalasAlunos(salas);
        return this;
    }

    public Usuario addSalasAluno(Sala sala) {
        this.salasAlunos.add(sala);
        return this;
    }

    public Usuario removeSalasAluno(Sala sala) {
        this.salasAlunos.remove(sala);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Usuario)) {
            return false;
        }
        return getId() != null && getId().equals(((Usuario) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    // LGPD/segurança: a senha (mesmo sendo hash) NUNCA entra no toString —
    // qualquer log de Usuario vazaria o hash para arquivos de log
    @Override
    public String toString() {
        return "Usuario{" +
            "id=" + getId() +
            ", nome='" + getNome() + "'" +
            ", sobrenome='" + getSobrenome() + "'" +
            ", email='" + getEmail() + "'" +
            ", tipoUsuario='" + getTipoUsuario() + "'" +
            ", ativo='" + getAtivo() + "'" +
            "}";
    }
}
