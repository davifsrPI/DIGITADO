package br.com.digitado.domain;

import static br.com.digitado.domain.ErroOrtograficoTestSamples.*;
import static br.com.digitado.domain.RespostaTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ErroOrtograficoTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ErroOrtografico.class);
        ErroOrtografico erroOrtografico1 = getErroOrtograficoSample1();
        ErroOrtografico erroOrtografico2 = new ErroOrtografico();
        assertThat(erroOrtografico1).isNotEqualTo(erroOrtografico2);

        erroOrtografico2.setId(erroOrtografico1.getId());
        assertThat(erroOrtografico1).isEqualTo(erroOrtografico2);

        erroOrtografico2 = getErroOrtograficoSample2();
        assertThat(erroOrtografico1).isNotEqualTo(erroOrtografico2);
    }

    @Test
    void respostaTest() {
        ErroOrtografico erroOrtografico = getErroOrtograficoRandomSampleGenerator();
        Resposta respostaBack = getRespostaRandomSampleGenerator();

        erroOrtografico.setResposta(respostaBack);
        assertThat(erroOrtografico.getResposta()).isEqualTo(respostaBack);

        erroOrtografico.resposta(null);
        assertThat(erroOrtografico.getResposta()).isNull();
    }
}
