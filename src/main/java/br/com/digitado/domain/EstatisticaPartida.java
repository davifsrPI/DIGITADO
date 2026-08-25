package br.com.digitado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/**
 * Snapshot das estatísticas da ÚLTIMA partida de uma sala.
 *
 * O jogo em si vive só na memória do JogoSalaService: fechar a sala descarta o
 * estado e o desempenho da turma ia junto. Ao encerrar a partida o consolidado
 * (ranking + relatório por palavra) é gravado aqui, e a tela "Ver estatísticas"
 * do professor lê deste registro - independe da sala estar aberta ou fechada.
 *
 * Uma linha por sala: a partida seguinte substitui o snapshot anterior.
 */
@Entity
@Table(name = "estatistica_partida")
public class EstatisticaPartida implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Código da sala (PK da Sala) - a coluna tem constraint única
    @NotNull
    @Column(name = "sala_codigo", nullable = false, unique = true)
    private String salaCodigo;

    @NotNull
    @Column(name = "data_encerramento", nullable = false)
    private Instant dataEncerramento;

    @Column(name = "total_palavras", nullable = false)
    private int totalPalavras;

    /**
     * Ranking e relatório por palavra serializados em JSON pelo
     * EstatisticaPartidaService. No Java é a String crua; quem lê desserializa
     * de volta para os records do relatório.
     */
    @Column(name = "dados", nullable = false, columnDefinition = "json")
    private String dados;

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

    public Instant getDataEncerramento() {
        return dataEncerramento;
    }

    public void setDataEncerramento(Instant dataEncerramento) {
        this.dataEncerramento = dataEncerramento;
    }

    public int getTotalPalavras() {
        return totalPalavras;
    }

    public void setTotalPalavras(int totalPalavras) {
        this.totalPalavras = totalPalavras;
    }

    public String getDados() {
        return dados;
    }

    public void setDados(String dados) {
        this.dados = dados;
    }

    @Override
    public String toString() {
        return (
            "EstatisticaPartida{id=" +
            id +
            ", salaCodigo='" +
            salaCodigo +
            "', dataEncerramento=" +
            dataEncerramento +
            ", totalPalavras=" +
            totalPalavras +
            "}"
        );
    }
}
