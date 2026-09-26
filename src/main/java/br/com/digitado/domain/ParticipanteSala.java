package br.com.digitado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/**
 * Como um aluno se identificou em UMA sala: o nome de verdade, a turma dele e
 * se ele quer aparecer para os colegas pelo apelido.
 *
 * O aluno preenche isto na tela de entrada, logo depois de digitar o código, e
 * a identificação é por SALA: o mesmo aluno pode entrar em salas de turmas
 * diferentes sem que uma sobrescreva a outra.
 *
 * Quem escolhe o apelido só esconde o nome dos COLEGAS: o professor da sala vê
 * sempre o nome verdadeiro (com o apelido entre parênteses). Por isso o nome
 * real nunca vai para o placar - ver ParticipanteSalaService.nomePublico.
 */
@Entity
@Table(
    name = "participante_sala",
    uniqueConstraints = @UniqueConstraint(name = "ux_participante_sala__sala_login", columnNames = { "sala_codigo", "login" })
)
public class ParticipanteSala implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Código da sala (PK da Sala) - a FK tem ON DELETE CASCADE
    @NotNull
    @Column(name = "sala_codigo", nullable = false)
    private String salaCodigo;

    // Login do usuário autenticado - é por ele que o placar e o relatório ligam
    // a resposta a esta identificação
    @NotNull
    @Column(name = "login", nullable = false, length = 50)
    private String login;

    @NotNull
    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @NotNull
    @Column(name = "turma", nullable = false, length = 30)
    private String turma;

    // true = colegas veem o apelido; false = colegas veem o nome
    @Column(name = "usar_apelido", nullable = false)
    private boolean usarApelido;

    // Só preenchido quando usarApelido = true
    @Column(name = "apelido", length = 30)
    private String apelido;

    @Column(name = "data_entrada", nullable = false)
    private Instant dataEntrada = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSalaCodigo() {
        return salaCodigo;
    }

    public void setSalaCodigo(String salaCodigo) {
        this.salaCodigo = salaCodigo;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getTurma() {
        return turma;
    }

    public void setTurma(String turma) {
        this.turma = turma;
    }

    public boolean isUsarApelido() {
        return usarApelido;
    }

    public void setUsarApelido(boolean usarApelido) {
        this.usarApelido = usarApelido;
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
    }

    public Instant getDataEntrada() {
        return dataEntrada;
    }

    public void setDataEntrada(Instant dataEntrada) {
        this.dataEntrada = dataEntrada;
    }

    /**
     * Nome que os COLEGAS veem (placar, ranking, pódio): o apelido quando o aluno
     * escolheu se esconder atrás dele, senão o nome que ele digitou.
     */
    public String nomePublico() {
        if (usarApelido && apelido != null && !apelido.isBlank()) {
            return apelido.trim();
        }
        return nome;
    }

    /**
     * Nome que o PROFESSOR vê: o nome verdadeiro e, quando o aluno joga de
     * apelido, o apelido entre parênteses - assim ele sabe quem é quem no placar
     * que a turma está vendo.
     */
    public String nomeParaProfessor() {
        if (usarApelido && apelido != null && !apelido.isBlank()) {
            return nome + " (" + apelido.trim() + ")";
        }
        return nome;
    }

    @Override
    public String toString() {
        return "ParticipanteSala{id=" + id + ", salaCodigo='" + salaCodigo + "', login='" + login + "', turma='" + turma + "'}";
    }
}
