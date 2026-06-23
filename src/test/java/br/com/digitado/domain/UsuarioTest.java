package br.com.digitado.domain;

import static br.com.digitado.domain.ListaPalavrasTestSamples.*;
import static br.com.digitado.domain.PalavraTestSamples.*;
import static br.com.digitado.domain.SalaTestSamples.*;
import static br.com.digitado.domain.UsuarioTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Usuario.class);
        Usuario usuario1 = getUsuarioSample1();
        Usuario usuario2 = new Usuario();
        assertThat(usuario1).isNotEqualTo(usuario2);

        usuario2.setId(usuario1.getId());
        assertThat(usuario1).isEqualTo(usuario2);

        usuario2 = getUsuarioSample2();
        assertThat(usuario1).isNotEqualTo(usuario2);
    }

    @Test
    void salasTest() {
        Usuario usuario = getUsuarioRandomSampleGenerator();
        Sala salaBack = getSalaRandomSampleGenerator();

        usuario.addSalas(salaBack);
        assertThat(usuario.getSalas()).containsOnly(salaBack);
        assertThat(salaBack.getProfessor()).isEqualTo(usuario);

        usuario.removeSalas(salaBack);
        assertThat(usuario.getSalas()).doesNotContain(salaBack);
        assertThat(salaBack.getProfessor()).isNull();

        usuario.salas(new HashSet<>(Set.of(salaBack)));
        assertThat(usuario.getSalas()).containsOnly(salaBack);
        assertThat(salaBack.getProfessor()).isEqualTo(usuario);

        usuario.setSalas(new HashSet<>());
        assertThat(usuario.getSalas()).doesNotContain(salaBack);
        assertThat(salaBack.getProfessor()).isNull();
    }

    @Test
    void listasPalavrasTest() {
        Usuario usuario = getUsuarioRandomSampleGenerator();
        ListaPalavras listaPalavrasBack = getListaPalavrasRandomSampleGenerator();

        usuario.addListasPalavras(listaPalavrasBack);
        assertThat(usuario.getListasPalavras()).containsOnly(listaPalavrasBack);
        assertThat(listaPalavrasBack.getProfessor()).isEqualTo(usuario);

        usuario.removeListasPalavras(listaPalavrasBack);
        assertThat(usuario.getListasPalavras()).doesNotContain(listaPalavrasBack);
        assertThat(listaPalavrasBack.getProfessor()).isNull();

        usuario.listasPalavras(new HashSet<>(Set.of(listaPalavrasBack)));
        assertThat(usuario.getListasPalavras()).containsOnly(listaPalavrasBack);
        assertThat(listaPalavrasBack.getProfessor()).isEqualTo(usuario);

        usuario.setListasPalavras(new HashSet<>());
        assertThat(usuario.getListasPalavras()).doesNotContain(listaPalavrasBack);
        assertThat(listaPalavrasBack.getProfessor()).isNull();
    }

    @Test
    void palavrasCriadasTest() {
        Usuario usuario = getUsuarioRandomSampleGenerator();
        Palavra palavraBack = getPalavraRandomSampleGenerator();

        usuario.addPalavrasCriadas(palavraBack);
        assertThat(usuario.getPalavrasCriadas()).containsOnly(palavraBack);
        assertThat(palavraBack.getCriador()).isEqualTo(usuario);

        usuario.removePalavrasCriadas(palavraBack);
        assertThat(usuario.getPalavrasCriadas()).doesNotContain(palavraBack);
        assertThat(palavraBack.getCriador()).isNull();

        usuario.palavrasCriadas(new HashSet<>(Set.of(palavraBack)));
        assertThat(usuario.getPalavrasCriadas()).containsOnly(palavraBack);
        assertThat(palavraBack.getCriador()).isEqualTo(usuario);

        usuario.setPalavrasCriadas(new HashSet<>());
        assertThat(usuario.getPalavrasCriadas()).doesNotContain(palavraBack);
        assertThat(palavraBack.getCriador()).isNull();
    }

    @Test
    void salasAlunoTest() {
        Usuario usuario = getUsuarioRandomSampleGenerator();
        Sala salaBack = getSalaRandomSampleGenerator();

        usuario.addSalasAluno(salaBack);
        assertThat(usuario.getSalasAlunos()).containsOnly(salaBack);

        usuario.removeSalasAluno(salaBack);
        assertThat(usuario.getSalasAlunos()).doesNotContain(salaBack);

        usuario.salasAlunos(new HashSet<>(Set.of(salaBack)));
        assertThat(usuario.getSalasAlunos()).containsOnly(salaBack);

        usuario.setSalasAlunos(new HashSet<>());
        assertThat(usuario.getSalasAlunos()).doesNotContain(salaBack);
    }
}
