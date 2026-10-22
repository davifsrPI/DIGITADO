package br.com.digitado.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** O nome da sala duplicada (SalaResource.nomeDaCopia). */
class SalaDuplicarTest {

    @Test
    void acrescentaCopiaUmaVezSo() {
        assertThat(SalaResource.nomeDaCopia("Turma 3A")).isEqualTo("Turma 3A (cópia)");
        // duplicar a cópia não empilha "(cópia) (cópia)"
        assertThat(SalaResource.nomeDaCopia("Turma 3A (cópia)")).isEqualTo("Turma 3A (cópia)");
    }

    @Test
    void respeitaOTetoDe80Caracteres() {
        String longo = "x".repeat(80);
        String copia = SalaResource.nomeDaCopia(longo);
        assertThat(copia).hasSizeLessThanOrEqualTo(80).endsWith(" (cópia)");
    }

    @Test
    void semNomeViraSala() {
        assertThat(SalaResource.nomeDaCopia(null)).isEqualTo("Sala (cópia)");
    }
}
