package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.service.XpService.ResultadoDuelo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** As fórmulas de XP de cada modo de jogo (documentadas no topo do XpService). */
class XpServiceTest {

    @Test
    @DisplayName("sala: mais gente na sala vale mais, até o teto de 2x")
    void multiplicadorDaSala() {
        assertThat(XpService.multiplicadorSala(1)).isEqualTo(1.0);
        assertThat(XpService.multiplicadorSala(11)).isEqualTo(1.5);
        assertThat(XpService.multiplicadorSala(21)).isEqualTo(2.0);
        assertThat(XpService.multiplicadorSala(40)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("sala: acertos, palavras jogadas, tamanho da turma e quem ficou para trás")
    void xpDaSala() {
        // (10*8 + 2*10) * 1.5 + 5*7 = 150 + 35
        assertThat(XpService.xpPartidaSala(8, 10, 11, 7)).isEqualTo(185);
        // a mesma partida, sozinho na sala
        assertThat(XpService.xpPartidaSala(8, 10, 1, 0)).isEqualTo(100);
        // mais palavras na partida rendem mais, com os mesmos acertos
        assertThat(XpService.xpPartidaSala(5, 20, 5, 0)).isGreaterThan(XpService.xpPartidaSala(5, 10, 5, 0));
        // mais colegas rendem mais, com o mesmo desempenho
        assertThat(XpService.xpPartidaSala(5, 10, 30, 0)).isGreaterThan(XpService.xpPartidaSala(5, 10, 3, 0));
    }

    @Test
    void xpDoDuelo() {
        assertThat(XpService.xpDuelo(6, 10, ResultadoDuelo.VITORIA)).isEqualTo(60 + 20 + 50);
        assertThat(XpService.xpDuelo(6, 10, ResultadoDuelo.EMPATE)).isEqualTo(60 + 20 + 20);
        assertThat(XpService.xpDuelo(6, 10, ResultadoDuelo.DERROTA)).isEqualTo(60 + 20);
    }

    @Test
    @DisplayName("individual: 5 a 15 por acerto, pela dificuldade para o jogador, +20 a cada 10 acertos")
    void xpDoIndividual() {
        assertThat(XpService.xpAcertoIndividual(0.0, 1)).isEqualTo(5);
        assertThat(XpService.xpAcertoIndividual(1.0, 1)).isEqualTo(15);
        assertThat(XpService.xpAcertoIndividual(0.5, 9)).isEqualTo(10);
        assertThat(XpService.xpAcertoIndividual(0.5, 10)).isEqualTo(30);
        // valores fora da faixa são travados
        assertThat(XpService.xpAcertoIndividual(7.0, 1)).isEqualTo(15);
    }
}
