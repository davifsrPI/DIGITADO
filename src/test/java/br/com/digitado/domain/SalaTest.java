package br.com.digitado.domain;

import static br.com.digitado.domain.SalaTestSamples.*;
import static br.com.digitado.domain.UsuarioTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SalaTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Sala.class);
        Sala sala1 = getSalaSample1();
        Sala sala2 = new Sala();
        assertThat(sala1).isNotEqualTo(sala2);

        sala2.setId(sala1.getId());
        assertThat(sala1).isEqualTo(sala2);

        sala2 = getSalaSample2();
        assertThat(sala1).isNotEqualTo(sala2);
    }

    @Test
    void professorTest() {
        Sala sala = getSalaRandomSampleGenerator();
        Usuario usuarioBack = getUsuarioRandomSampleGenerator();

        sala.setProfessor(usuarioBack);
        assertThat(sala.getProfessor()).isEqualTo(usuarioBack);

        sala.professor(null);
        assertThat(sala.getProfessor()).isNull();
    }

    @Test
    void alunosTest() {
        Sala sala = getSalaRandomSampleGenerator();
        Usuario usuarioBack = getUsuarioRandomSampleGenerator();

        sala.addAlunos(usuarioBack);
        assertThat(sala.getAlunos()).containsOnly(usuarioBack);
        assertThat(usuarioBack.getSalasAlunos()).containsOnly(sala);

        sala.removeAlunos(usuarioBack);
        assertThat(sala.getAlunos()).doesNotContain(usuarioBack);
        assertThat(usuarioBack.getSalasAlunos()).doesNotContain(sala);

        sala.alunos(new HashSet<>(Set.of(usuarioBack)));
        assertThat(sala.getAlunos()).containsOnly(usuarioBack);
        assertThat(usuarioBack.getSalasAlunos()).containsOnly(sala);

        sala.setAlunos(new HashSet<>());
        assertThat(sala.getAlunos()).doesNotContain(usuarioBack);
        assertThat(usuarioBack.getSalasAlunos()).doesNotContain(sala);
    }
}
