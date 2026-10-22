package br.com.digitado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/**
 * Uma partida ENCERRADA do modo individual: até onde o jogador chegou sozinho.
 *
 * A partida em andamento vive na memória do PartidaIndividualService, como as
 * salas no JogoSalaService. O que fica aqui é o resultado, para o recorde pessoal
 * e a lista das últimas partidas; cada resposta, palavra por palavra, já está no
 * histórico de respostas (origem INDIVIDUAL).
 *
 * Chaveada pelo LOGIN, como o histórico, e apagada junto com a conta.
 */
@Entity
@Table(name = "partida_individual")
public class PartidaIndividual implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Como a partida terminou. */
    public enum Encerramento {
        // Perdeu as três vidas
        SEM_VIDAS,
        // Parou por conta própria
        DESISTIU,
        // Fechou a tela e não voltou (encerrada pelo servidor por inatividade)
        ABANDONOU,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "login", length = 100, nullable = false)
    private String login;

    @NotNull
    @Column(name = "data_inicio", nullable = false)
    private Instant dataInicio;

    @NotNull
    @Column(name = "data_fim", nullable = false)
    private Instant dataFim;

    // Palavras ditadas na partida, certas ou não
    @NotNull
    @Column(name = "palavras", nullable = false)
    private Integer palavras;

    @NotNull
    @Column(name = "acertos", nullable = false)
    private Integer acertos;

    @NotNull
    @Column(name = "melhor_sequencia", nullable = false)
    private Integer melhorSequencia;

    @NotNull
    @Column(name = "xp_ganho", nullable = false)
    private Integer xpGanho;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "encerramento", length = 20, nullable = false)
    private Encerramento encerramento;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public Instant getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(Instant dataInicio) {
        this.dataInicio = dataInicio;
    }

    public Instant getDataFim() {
        return dataFim;
    }

    public void setDataFim(Instant dataFim) {
        this.dataFim = dataFim;
    }

    public Integer getPalavras() {
        return palavras;
    }

    public void setPalavras(Integer palavras) {
        this.palavras = palavras;
    }

    public Integer getAcertos() {
        return acertos;
    }

    public void setAcertos(Integer acertos) {
        this.acertos = acertos;
    }

    public Integer getMelhorSequencia() {
        return melhorSequencia;
    }

    public void setMelhorSequencia(Integer melhorSequencia) {
        this.melhorSequencia = melhorSequencia;
    }

    public Integer getXpGanho() {
        return xpGanho;
    }

    public void setXpGanho(Integer xpGanho) {
        this.xpGanho = xpGanho;
    }

    public Encerramento getEncerramento() {
        return encerramento;
    }

    public void setEncerramento(Encerramento encerramento) {
        this.encerramento = encerramento;
    }

    @Override
    public String toString() {
        return "PartidaIndividual{id=" + id + ", login='" + login + "', palavras=" + palavras + ", acertos=" + acertos + "}";
    }
}
