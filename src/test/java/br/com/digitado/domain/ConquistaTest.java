package br.com.digitado.domain;

import static br.com.digitado.domain.ConquistaTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ConquistaTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Conquista.class);
        Conquista conquista1 = getConquistaSample1();
        Conquista conquista2 = new Conquista();
        assertThat(conquista1).isNotEqualTo(conquista2);

        conquista2.setId(conquista1.getId());
        assertThat(conquista1).isEqualTo(conquista2);

        conquista2 = getConquistaSample2();
        assertThat(conquista1).isNotEqualTo(conquista2);
    }
}
