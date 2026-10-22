package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A régua que decide acerto, tipo de erro e similaridade - a mesma das salas, do
 * duelo e do modo individual. Os exemplos são os que o painel do admin mostra para
 * explicar cada tipo: se um deles mudar de classificação, o texto do painel mente.
 */
class ComparadorPalavrasTest {

    @ParameterizedTest(name = "\"{0}\" por \"{1}\" = {2}")
    @CsvSource(
        {
            "cafe, café, ACENTUACAO",
            "xave, chave, ERRO_FONETICO",
            "cassa, caça, ERRO_FONETICO",
            "menimo, menino, TROCA_LETRA",
            "cavlo, cavalo, LETRA_FALTANDO",
            "carrro, carro, LETRA_EXTRA",
            "caza, chácara, OUTRO",
        }
    )
    void classificaOsExemplosDoPainel(String digitado, String correto, String tipo) {
        ComparadorPalavras.Conferencia c = ComparadorPalavras.conferir(digitado, correto);
        assertThat(c.correta()).isFalse();
        assertThat(c.tipoErro()).isEqualTo(tipo);
    }

    @Test
    void acertoValeCemPorCento() {
        ComparadorPalavras.Conferencia c = ComparadorPalavras.conferir("  Café ", "café");
        assertThat(c.correta()).isTrue();
        assertThat(c.tipoErro()).isNull();
        assertThat(c.similaridade()).isEqualTo(100);
    }

    @Test
    @DisplayName("acento decomposto (a + til combinante) conta como a mesma letra")
    void normalizaUnicode() {
        String decomposto = "mão"; // "mão" com o til separado
        assertThat(ComparadorPalavras.conferir(decomposto, "mão").correta()).isTrue();
    }

    @Test
    void respostaVaziaEhNaoRespondeu() {
        ComparadorPalavras.Conferencia c = ComparadorPalavras.conferir("   ", "casa");
        assertThat(c.correta()).isFalse();
        assertThat(c.tipoErro()).isEqualTo(ComparadorPalavras.NAO_RESPONDEU);
        assertThat(c.similaridade()).isZero();

        assertThat(ComparadorPalavras.conferir(null, "casa").tipoErro()).isEqualTo(ComparadorPalavras.NAO_RESPONDEU);
    }

    @Test
    @DisplayName("similaridade: acento e som valem fixo, o resto é a distância de edição")
    void similaridade() {
        assertThat(ComparadorPalavras.conferir("cafe", "café").similaridade()).isEqualTo(95);
        assertThat(ComparadorPalavras.conferir("xave", "chave").similaridade()).isEqualTo(85);
        // uma letra de quatro: 75%
        assertThat(ComparadorPalavras.conferir("casu", "casa").similaridade()).isEqualTo(75);
        assertThat(ComparadorPalavras.conferir("xyz", "mão").similaridade()).isZero();
    }
}
