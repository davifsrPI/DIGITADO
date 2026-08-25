package br.com.digitado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/**
 * Uma resposta digitada por um jogador LOGADO, em qualquer modo do jogo.
 *
 * É a matéria-prima do painel "Meu Desempenho": sem ela não dá para dizer quais
 * palavras a pessoa mais erra nem como a taxa de acerto dela evoluiu - a partida
 * vive só na memória do JogoSalaService e os contadores da tabela palavra são do
 * acervo inteiro, não de quem respondeu.
 *
 * Identidade pelo LOGIN, não pelo Usuario: é o que o WebSocket da partida e a
 * palavra do dia têm em mãos no instante da resposta. Visitante anônimo não gera
 * linha nenhuma aqui.
 *
 * LGPD: o vínculo com a pessoa É a finalidade deste registro (o titular ver o
 * próprio histórico), então ele não entra na anonimização periódica - vive
 * enquanto a conta viver e é apagado com ela.
 */
@Entity
@Table(name = "historico_resposta")
public class HistoricoResposta implements Serializable {

    private static final long serialVersionUID = 1L;

    /** De onde veio a resposta - separa o treino solo da disputa em sala. */
    public enum Origem {
        PARTIDA,
        DUELO,
        PALAVRA_DO_DIA,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "login", length = 100, nullable = false)
    private String login;

    @NotNull
    @Column(name = "palavra_id", nullable = false)
    private Long palavraId;

    @NotNull
    @Column(name = "correta", nullable = false)
    private Boolean correta;

    /**
     * Dificuldade EFETIVA no instante da resposta. Palavra.getDificuldade() é
     * calculada pela taxa de acerto do acervo e muda com o tempo; congelar aqui
     * impede que o histórico se reescreva sozinho quando a palavra "fica fácil".
     */
    @Column(name = "dificuldade", length = 20)
    private String dificuldade;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", length = 20, nullable = false)
    private Origem origem;

    // Classificação do erro (ACENTUACAO, TROCA_LETRA...) - nulo quando acertou
    @Column(name = "tipo_erro", length = 30)
    private String tipoErro;

    // Quanto tempo levou para responder, em milissegundos (nulo na palavra do dia)
    @Column(name = "tempo_ms")
    private Integer tempoMs;

    @NotNull
    @Column(name = "data_resposta", nullable = false)
    private Instant dataResposta = Instant.now();

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

    public Long getPalavraId() {
        return palavraId;
    }

    public void setPalavraId(Long palavraId) {
        this.palavraId = palavraId;
    }

    public Boolean getCorreta() {
        return correta;
    }

    public void setCorreta(Boolean correta) {
        this.correta = correta;
    }

    public String getDificuldade() {
        return dificuldade;
    }

    public void setDificuldade(String dificuldade) {
        this.dificuldade = dificuldade;
    }

    public Origem getOrigem() {
        return origem;
    }

    public void setOrigem(Origem origem) {
        this.origem = origem;
    }

    public String getTipoErro() {
        return tipoErro;
    }

    public void setTipoErro(String tipoErro) {
        this.tipoErro = tipoErro;
    }

    public Integer getTempoMs() {
        return tempoMs;
    }

    public void setTempoMs(Integer tempoMs) {
        this.tempoMs = tempoMs;
    }

    public Instant getDataResposta() {
        return dataResposta;
    }

    public void setDataResposta(Instant dataResposta) {
        this.dataResposta = dataResposta;
    }

    @Override
    public String toString() {
        return "HistoricoResposta{id=" + id + ", login='" + login + "', palavraId=" + palavraId + ", correta=" + correta + "}";
    }
}
