package br.com.digitado.domain;

import static br.com.digitado.domain.AtividadeTestSamples.*;
import static br.com.digitado.domain.PalavraTestSamples.*;
import static br.com.digitado.domain.RespostaTestSamples.*;
import static br.com.digitado.domain.UsuarioTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class RespostaTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Resposta.class);
        Resposta resposta1 = getRespostaSample1();
        Resposta resposta2 = new Resposta();
        assertThat(resposta1).isNotEqualTo(resposta2);

        resposta2.setId(resposta1.getId());
        assertThat(resposta1).isEqualTo(resposta2);

        resposta2 = getRespostaSample2();
        assertThat(resposta1).isNotEqualTo(resposta2);
    }

    @Test
    void atividadeTest() {
        Resposta resposta = getRespostaRandomSampleGenerator();
        Atividade atividadeBack = getAtividadeRandomSampleGenerator();

        resposta.setAtividade(atividadeBack);
        assertThat(resposta.getAtividade()).isEqualTo(atividadeBack);

        resposta.atividade(null);
        assertThat(resposta.getAtividade()).isNull();
    }

    @Test
    void alunoTest() {
        Resposta resposta = getRespostaRandomSampleGenerator();
        Usuario usuarioBack = getUsuarioRandomSampleGenerator();

        resposta.setAluno(usuarioBack);
        assertThat(resposta.getAluno()).isEqualTo(usuarioBack);

        resposta.aluno(null);
        assertThat(resposta.getAluno()).isNull();
    }

    @Test
    void palavraTest() {
        Resposta resposta = getRespostaRandomSampleGenerator();
        Palavra palavraBack = getPalavraRandomSampleGenerator();

        resposta.setPalavra(palavraBack);
        assertThat(resposta.getPalavra()).isEqualTo(palavraBack);

        resposta.palavra(null);
        assertThat(resposta.getPalavra()).isNull();
    }
}
