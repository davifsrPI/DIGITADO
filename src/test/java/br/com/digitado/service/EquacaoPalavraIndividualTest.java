package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.service.EquacaoPalavraIndividual.Candidata;
import br.com.digitado.service.HistoricoRespostaService.EstatisticaPalavraJogador;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A equação que escolhe a palavra do modo individual. Cada uma das quatro medidas
 * pedidas (tentativas, acertos, tempo gasto e similaridade) precisa mexer no
 * resultado na direção certa - é isso que estes testes amarram.
 */
class EquacaoPalavraIndividualTest {

    private static Candidata palavra(long id, Dificuldade dificuldade) {
        return new Candidata(id, dificuldade, 0, 0, 30);
    }

    private static EstatisticaPalavraJogador hist(long tentativas, long acertos, Double tempoMs, Double similaridade) {
        return new EstatisticaPalavraJogador(tentativas, acertos, tempoMs, similaridade);
    }

    @Test
    @DisplayName("sem histórico, vale a dificuldade cadastrada")
    void semHistoricoValeACuradoria() {
        double facil = EquacaoPalavraIndividual.dificuldadeParaJogador(palavra(1, Dificuldade.FACIL), null);
        double media = EquacaoPalavraIndividual.dificuldadeParaJogador(palavra(2, Dificuldade.MEDIO), null);
        double dificil = EquacaoPalavraIndividual.dificuldadeParaJogador(palavra(3, Dificuldade.DIFICIL), null);
        assertThat(facil).isCloseTo(0.20, within(0.001));
        assertThat(media).isCloseTo(0.45, within(0.001));
        assertThat(dificil).isCloseTo(0.70, within(0.001));
    }

    @Test
    @DisplayName("mais ACERTOS deixam a palavra mais fácil para o jogador")
    void acertosBaixamADificuldade() {
        Candidata c = palavra(1, Dificuldade.MEDIO);
        double errouTudo = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(6, 0, 10_000.0, 40.0));
        double acertouTudo = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(6, 6, 10_000.0, 40.0));
        assertThat(acertouTudo).isLessThan(errouTudo);
    }

    @Test
    @DisplayName("mais TEMPO gasto deixa a palavra mais difícil")
    void tempoSobeADificuldade() {
        Candidata c = palavra(1, Dificuldade.MEDIO);
        double rapido = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(4, 2, 3_000.0, 80.0));
        double lento = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(4, 2, 27_000.0, 80.0));
        assertThat(lento).isGreaterThan(rapido);
    }

    @Test
    @DisplayName("maior SIMILARIDADE (o 'quase') deixa a palavra mais fácil")
    void similaridadeBaixaADificuldade() {
        Candidata c = palavra(1, Dificuldade.MEDIO);
        double quase = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(4, 1, 8_000.0, 90.0));
        double longe = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(4, 1, 8_000.0, 10.0));
        assertThat(quase).isLessThan(longe);
    }

    @Test
    @DisplayName("mais TENTATIVAS dão mais peso ao histórico pessoal que ao do acervo")
    void tentativasAumentamAConfianca() {
        // Palavra que o acervo acha difícil, mas que o jogador acerta sempre e rápido
        Candidata c = palavra(1, Dificuldade.DIFICIL);
        double umaVez = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(1, 1, 2_000.0, 100.0));
        double dezVezes = EquacaoPalavraIndividual.dificuldadeParaJogador(c, hist(10, 10, 2_000.0, 100.0));
        assertThat(dezVezes).isLessThan(umaVez);
    }

    @Test
    void oAlvoSobeACadaAcertoEParaNoTeto() {
        assertThat(EquacaoPalavraIndividual.alvo(0)).isCloseTo(0.20, within(1e-9));
        assertThat(EquacaoPalavraIndividual.alvo(5)).isGreaterThan(EquacaoPalavraIndividual.alvo(4));
        assertThat(EquacaoPalavraIndividual.alvo(1000)).isEqualTo(0.85);
    }

    @Test
    @DisplayName("no começo vem palavra fácil; depois de muitos acertos, a difícil")
    void escolhePelaDistanciaAoAlvo() {
        List<Candidata> acervo = List.of(palavra(1, Dificuldade.FACIL), palavra(2, Dificuldade.MEDIO), palavra(3, Dificuldade.DIFICIL));
        Random sem = new Random(42);
        assertThat(EquacaoPalavraIndividual.escolher(acervo, Map.of(), Set.of(), 0, sem)).map(Candidata::palavraId).contains(1L);
        assertThat(EquacaoPalavraIndividual.escolher(acervo, Map.of(), Set.of(), 15, sem)).map(Candidata::palavraId).contains(3L);
    }

    @Test
    void naoRepetePalavraJaDitadaNemInventaQuandoAcaba() {
        List<Candidata> acervo = List.of(palavra(1, Dificuldade.FACIL), palavra(2, Dificuldade.FACIL));
        Random sem = new Random(1);
        assertThat(EquacaoPalavraIndividual.escolher(acervo, Map.of(), Set.of(1L), 0, sem)).map(Candidata::palavraId).contains(2L);
        assertThat(EquacaoPalavraIndividual.escolher(acervo, Map.of(), Set.of(1L, 2L), 0, sem)).isEmpty();
    }

    @Test
    @DisplayName("palavra já errada ganha prioridade de revisão quando está perto do alvo")
    void revisaoDePalavraErrada() {
        // Duas palavras com a MESMA dificuldade para o jogador (domínio 0,65 nas duas),
        // só que a 2 ele já errou: sem o bônus seria cara ou coroa
        List<Candidata> acervo = List.of(palavra(1, Dificuldade.FACIL), palavra(2, Dificuldade.FACIL));
        Map<Long, EstatisticaPalavraJogador> historico = new HashMap<>();
        historico.put(1L, hist(2, 2, 30_000.0, 50.0));
        historico.put(2L, hist(2, 1, 15_000.0, 100.0));
        assertThat(EquacaoPalavraIndividual.dificuldadeParaJogador(acervo.get(0), historico.get(1L))).isCloseTo(
            EquacaoPalavraIndividual.dificuldadeParaJogador(acervo.get(1), historico.get(2L)),
            within(1e-9)
        );
        int vezesQueARevisaoVenceu = 0;
        Random aleatorio = new Random(7);
        for (int i = 0; i < 50; i++) {
            if (EquacaoPalavraIndividual.escolher(acervo, historico, Set.of(), 3, aleatorio).orElseThrow().palavraId() == 2L) {
                vezesQueARevisaoVenceu++;
            }
        }
        assertThat(vezesQueARevisaoVenceu).isEqualTo(50);
    }
}
