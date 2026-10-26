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
            "guardachuva, guarda-chuva, ESPACO_HIFEN",
            "xave, chave, ERRO_FONETICO",
            "cassa, caça, ERRO_FONETICO",
            "pdera, pedra, LETRAS_INVERTIDAS",
            "menimo, menino, TROCA_LETRA",
            "cavlo, cavalo, LETRA_FALTANDO",
            "carrro, carro, LETRA_EXTRA",
            "trabisseiro, travesseiro, VARIOS_ERROS",
            "caza, chácara, OUTRA_PALAVRA",
        }
    )
    void classificaOsExemplosDoPainel(String digitado, String correto, String tipo) {
        ComparadorPalavras.Conferencia c = ComparadorPalavras.conferir(digitado, correto);
        assertThat(c.correta()).isFalse();
        assertThat(c.tipoErro()).isEqualTo(tipo);
    }

    /**
     * Subtipo e detalhe ("certo→digitado"; lado vazio = letra que faltou ou sobrou).
     * A coluna vazia do CSV é null: OUTRA_PALAVRA não tem trecho que valha mostrar.
     */
    @ParameterizedTest(name = "\"{0}\" por \"{1}\" = {2} / {3} / {4}")
    @CsvSource(
        delimiter = '|',
        value = {
            // acento: qual letra e de que jeito
            "cafe        | café         | ACENTUACAO        | ACENTO_FALTANDO          | é→e",
            "cafê        | café         | ACENTUACAO        | ACENTO_TROCADO           | é→ê",
            "cáfe        | café         | ACENTUACAO        | ACENTOS_VARIOS           | a→á, é→e",
            "caca        | caça         | ACENTUACAO        | CEDILHA_FALTANDO         | ç→c",
            "fáca        | faca         | ACENTUACAO        | ACENTO_SOBRANDO          | a→á",
            // espaço e hífen, com uma letra de contexto de cada lado
            "guardachuva | guarda-chuva | ESPACO_HIFEN      | HIFEN_FALTANDO           | a-c→ac",
            "arco iris   | arco-íris    | ESPACO_HIFEN      | HIFEN_POR_ESPACO         | o-i→o i",
            "derepente   | de repente   | ESPACO_HIFEN      | ESPACO_FALTANDO          | e r→er",
            // som: a família do som e o trecho confundido
            "xave        | chave        | ERRO_FONETICO     | SOM_CH_X                 | ch→x",
            "cassa       | caça         | ERRO_FONETICO     | SOM_S_Z                  | ç→ss",
            "esseção     | exceção      | ERRO_FONETICO     | SOM_S_Z                  | xc→ss",
            "jente       | gente        | ERRO_FONETICO     | SOM_G_J                  | g→j",
            "ora         | hora         | ERRO_FONETICO     | H_MUDO                   | h→",
            "canpo       | campo        | ERRO_FONETICO     | SOM_M_N                  | m→n",
            "homen       | homem        | ERRO_FONETICO     | SOM_M_N                  | m→n",
            "brasiu      | brasil       | ERRO_FONETICO     | SOM_L_U                  | l→u",
            "caro        | carro        | ERRO_FONETICO     | SOM_R_RR                 | rr→r",
            "testo       | texto        | ERRO_FONETICO     | SOM_S_Z                  | x→s",
            // uma letra fora do lugar
            "pdera       | pedra        | LETRAS_INVERTIDAS | INVERTEU_VOGAL_CONSOANTE | ed→de",
            "menimo      | menino       | TROCA_LETRA       | TECLA_VIZINHA            | n→m",
            "dola        | bola         | TROCA_LETRA       | LETRA_ESPELHADA          | b→d",
            "masa        | mesa         | TROCA_LETRA       | TROCOU_VOGAL             | e→a",
            "pato        | gato         | TROCA_LETRA       | TROCOU_CONSOANTE         | g→p",
            "cavlo       | cavalo       | LETRA_FALTANDO    | FALTOU_VOGAL             | a→",
            "piza        | pizza        | LETRA_FALTANDO    | FALTOU_LETRA_DOBRADA     | zz→z",
            "carrro      | carro        | LETRA_EXTRA       | LETRA_REPETIDA           | r→rr",
            "casda       | casa         | LETRA_EXTRA       | TECLA_VIZINHA_JUNTO      | →d",
            "casae       | casa         | LETRA_EXTRA       | SOBROU_VOGAL             | →e",
            // mais de uma letra
            "trabisseiro | travesseiro  | VARIOS_ERROS      | DUAS_DIFERENCAS          | ve→bi",
            "caza        | chácara      | OUTRA_PALAVRA     |                          |",
        }
    )
    void destrinchaOErro(String digitado, String correto, String tipo, String subtipo, String detalhe) {
        ComparadorPalavras.Conferencia c = ComparadorPalavras.conferir(digitado, correto);
        assertThat(c.tipoErro()).isEqualTo(tipo);
        assertThat(c.subtipoErro()).isEqualTo(subtipo);
        assertThat(c.detalheErro()).isEqualTo(detalhe);
    }

    @Test
    void acertoValeCemPorCento() {
        ComparadorPalavras.Conferencia c = ComparadorPalavras.conferir("  Café ", "café");
        assertThat(c.correta()).isTrue();
        assertThat(c.tipoErro()).isNull();
        assertThat(c.subtipoErro()).isNull();
        assertThat(c.detalheErro()).isNull();
        assertThat(c.similaridade()).isEqualTo(100);
    }

    @Test
    @DisplayName("acento decomposto (a + til combinante) conta como a mesma letra")
    void normalizaUnicode() {
        String decomposto = "mão"; // "mão" com o til separado
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
        // a inversão é um deslize só na classificação; a similaridade segue o Levenshtein
        assertThat(ComparadorPalavras.conferir("pdera", "pedra").similaridade()).isEqualTo(60);
    }

    @Test
    @DisplayName("teclas vizinhas no ABNT2, contando a fileira de baixo meia tecla à direita")
    void teclasVizinhas() {
        assertThat(ComparadorPalavras.teclasVizinhas('a', 's')).isTrue();
        assertThat(ComparadorPalavras.teclasVizinhas('a', 'q')).isTrue();
        assertThat(ComparadorPalavras.teclasVizinhas('a', 'w')).isTrue();
        assertThat(ComparadorPalavras.teclasVizinhas('z', 's')).isTrue();
        assertThat(ComparadorPalavras.teclasVizinhas('b', 'h')).isTrue();
        assertThat(ComparadorPalavras.teclasVizinhas('a', 'e')).isFalse();
        assertThat(ComparadorPalavras.teclasVizinhas('z', 'd')).isFalse();
        assertThat(ComparadorPalavras.teclasVizinhas('a', 'a')).isFalse();
    }

    @Test
    void todoTipoGravadoEstaNaLista() {
        assertThat(ComparadorPalavras.TIPOS).contains(
            ComparadorPalavras.OUTRO,
            ComparadorPalavras.NAO_RESPONDEU,
            ComparadorPalavras.conferir("pdera", "pedra").tipoErro(),
            ComparadorPalavras.conferir("guardachuva", "guarda-chuva").tipoErro()
        );
    }
}
