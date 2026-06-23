package br.com.digitado.domain;

import static br.com.digitado.domain.ListaPalavrasTestSamples.*;
import static br.com.digitado.domain.PalavraTestSamples.*;
import static br.com.digitado.domain.UsuarioTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ListaPalavrasTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ListaPalavras.class);
        ListaPalavras listaPalavras1 = getListaPalavrasSample1();
        ListaPalavras listaPalavras2 = new ListaPalavras();
        assertThat(listaPalavras1).isNotEqualTo(listaPalavras2);

        listaPalavras2.setId(listaPalavras1.getId());
        assertThat(listaPalavras1).isEqualTo(listaPalavras2);

        listaPalavras2 = getListaPalavrasSample2();
        assertThat(listaPalavras1).isNotEqualTo(listaPalavras2);
    }

    @Test
    void palavrasTest() {
        ListaPalavras listaPalavras = getListaPalavrasRandomSampleGenerator();
        Palavra palavraBack = getPalavraRandomSampleGenerator();

        listaPalavras.addPalavras(palavraBack);
        assertThat(listaPalavras.getPalavras()).containsOnly(palavraBack);

        listaPalavras.removePalavras(palavraBack);
        assertThat(listaPalavras.getPalavras()).doesNotContain(palavraBack);

        listaPalavras.palavras(new HashSet<>(Set.of(palavraBack)));
        assertThat(listaPalavras.getPalavras()).containsOnly(palavraBack);

        listaPalavras.setPalavras(new HashSet<>());
        assertThat(listaPalavras.getPalavras()).doesNotContain(palavraBack);
    }

    @Test
    void professorTest() {
        ListaPalavras listaPalavras = getListaPalavrasRandomSampleGenerator();
        Usuario usuarioBack = getUsuarioRandomSampleGenerator();

        listaPalavras.setProfessor(usuarioBack);
        assertThat(listaPalavras.getProfessor()).isEqualTo(usuarioBack);

        listaPalavras.professor(null);
        assertThat(listaPalavras.getProfessor()).isNull();
    }
}
