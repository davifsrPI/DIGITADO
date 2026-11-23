package br.com.digitado.domain.enumeration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdiomaTest {

    @Test
    void reconheceNomeEtiquetaEOPtBrDosSeedsAntigos() {
        assertThat(Idioma.de("en")).contains(Idioma.EN);
        assertThat(Idioma.de("es-ES")).contains(Idioma.ES);
        assertThat(Idioma.de("PT-BR")).contains(Idioma.PT);
        assertThat(Idioma.de("pt_br")).contains(Idioma.PT);
    }

    @Test
    void desconhecidoNaoViraIdioma() {
        assertThat(Idioma.de("FR")).isEmpty();
        assertThat(Idioma.de("xx' OR 1=1 --")).isEmpty();
        assertThat(Idioma.de(null)).isEmpty();
        // Palavra sem idioma é português
        assertThat(Idioma.daPalavra(null)).isEqualTo(Idioma.PT);
    }
}
