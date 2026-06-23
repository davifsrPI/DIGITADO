package br.com.digitado.domain;

import static br.com.digitado.domain.ListaPalavrasTestSamples.*;
import static br.com.digitado.domain.PalavraTestSamples.*;
import static br.com.digitado.domain.UsuarioTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PalavraTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Palavra.class);
        Palavra palavra1 = getPalavraSample1();
        Palavra palavra2 = new Palavra();
        assertThat(palavra1).isNotEqualTo(palavra2);

        palavra2.setId(palavra1.getId());
        assertThat(palavra1).isEqualTo(palavra2);

        palavra2 = getPalavraSample2();
        assertThat(palavra1).isNotEqualTo(palavra2);
    }

    @Test
    void criadorTest() {
        Palavra palavra = getPalavraRandomSampleGenerator();
        Usuario usuarioBack = getUsuarioRandomSampleGenerator();

        palavra.setCriador(usuarioBack);
        assertThat(palavra.getCriador()).isEqualTo(usuarioBack);

        palavra.criador(null);
        assertThat(palavra.getCriador()).isNull();
    }

    @Test
    void listasTest() {
        Palavra palavra = getPalavraRandomSampleGenerator();
        ListaPalavras listaPalavrasBack = getListaPalavrasRandomSampleGenerator();

        palavra.addListas(listaPalavrasBack);
        assertThat(palavra.getListas()).containsOnly(listaPalavrasBack);
        assertThat(listaPalavrasBack.getPalavras()).containsOnly(palavra);

        palavra.removeListas(listaPalavrasBack);
        assertThat(palavra.getListas()).doesNotContain(listaPalavrasBack);
        assertThat(listaPalavrasBack.getPalavras()).doesNotContain(palavra);

        palavra.listas(new HashSet<>(Set.of(listaPalavrasBack)));
        assertThat(palavra.getListas()).containsOnly(listaPalavrasBack);
        assertThat(listaPalavrasBack.getPalavras()).containsOnly(palavra);

        palavra.setListas(new HashSet<>());
        assertThat(palavra.getListas()).doesNotContain(listaPalavrasBack);
        assertThat(listaPalavrasBack.getPalavras()).doesNotContain(palavra);
    }
}
