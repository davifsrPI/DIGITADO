package br.com.digitado.domain;

import static br.com.digitado.domain.ConquistaTestSamples.*;
import static br.com.digitado.domain.UsuarioConquistaTestSamples.*;
import static br.com.digitado.domain.UsuarioTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class UsuarioConquistaTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(UsuarioConquista.class);
        UsuarioConquista usuarioConquista1 = getUsuarioConquistaSample1();
        UsuarioConquista usuarioConquista2 = new UsuarioConquista();
        assertThat(usuarioConquista1).isNotEqualTo(usuarioConquista2);

        usuarioConquista2.setId(usuarioConquista1.getId());
        assertThat(usuarioConquista1).isEqualTo(usuarioConquista2);

        usuarioConquista2 = getUsuarioConquistaSample2();
        assertThat(usuarioConquista1).isNotEqualTo(usuarioConquista2);
    }

    @Test
    void alunoTest() {
        UsuarioConquista usuarioConquista = getUsuarioConquistaRandomSampleGenerator();
        Usuario usuarioBack = getUsuarioRandomSampleGenerator();

        usuarioConquista.setAluno(usuarioBack);
        assertThat(usuarioConquista.getAluno()).isEqualTo(usuarioBack);

        usuarioConquista.aluno(null);
        assertThat(usuarioConquista.getAluno()).isNull();
    }

    @Test
    void conquistaTest() {
        UsuarioConquista usuarioConquista = getUsuarioConquistaRandomSampleGenerator();
        Conquista conquistaBack = getConquistaRandomSampleGenerator();

        usuarioConquista.setConquista(conquistaBack);
        assertThat(usuarioConquista.getConquista()).isEqualTo(conquistaBack);

        usuarioConquista.conquista(null);
        assertThat(usuarioConquista.getConquista()).isNull();
    }
}
