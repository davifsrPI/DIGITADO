package br.com.digitado.domain;

import static br.com.digitado.domain.RankingTestSamples.*;
import static br.com.digitado.domain.SalaTestSamples.*;
import static br.com.digitado.domain.UsuarioTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class RankingTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Ranking.class);
        Ranking ranking1 = getRankingSample1();
        Ranking ranking2 = new Ranking();
        assertThat(ranking1).isNotEqualTo(ranking2);

        ranking2.setId(ranking1.getId());
        assertThat(ranking1).isEqualTo(ranking2);

        ranking2 = getRankingSample2();
        assertThat(ranking1).isNotEqualTo(ranking2);
    }

    @Test
    void salaTest() {
        Ranking ranking = getRankingRandomSampleGenerator();
        Sala salaBack = getSalaRandomSampleGenerator();

        ranking.setSala(salaBack);
        assertThat(ranking.getSala()).isEqualTo(salaBack);

        ranking.sala(null);
        assertThat(ranking.getSala()).isNull();
    }

    @Test
    void alunoTest() {
        Ranking ranking = getRankingRandomSampleGenerator();
        Usuario usuarioBack = getUsuarioRandomSampleGenerator();

        ranking.setAluno(usuarioBack);
        assertThat(ranking.getAluno()).isEqualTo(usuarioBack);

        ranking.aluno(null);
        assertThat(ranking.getAluno()).isNull();
    }
}
