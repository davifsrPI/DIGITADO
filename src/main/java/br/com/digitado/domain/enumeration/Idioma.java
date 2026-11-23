package br.com.digitado.domain.enumeration;

import java.util.Locale;
import java.util.Optional;

/**
 * Idioma de uma palavra do acervo - gravado como o NOME da constante (PT, EN, ES)
 * na coluna palavra.idioma.
 *
 * Português é o idioma do ditado: salas, duelos, palavra do dia e o modo
 * individual sorteiam só palavras PT. Inglês e espanhol existem para o treino de
 * idiomas (modo individual com ?idioma=EN|ES), com a voz do ditado no idioma da
 * palavra.
 *
 * tag: a etiqueta BCP-47 que o navegador usa na voz (speechSynthesis) e o Windows
 * usa para escolher a voz da SAPI. vozEspeak: o nome da voz no espeak-ng.
 */
public enum Idioma {
    PT("pt-BR", "pt-br", "Português"),
    EN("en-US", "en-us", "Inglês"),
    ES("es-ES", "es", "Espanhol");

    private final String tag;
    private final String vozEspeak;
    private final String nome;

    Idioma(String tag, String vozEspeak, String nome) {
        this.tag = tag;
        this.vozEspeak = vozEspeak;
        this.nome = nome;
    }

    public String getTag() {
        return tag;
    }

    public String getVozEspeak() {
        return vozEspeak;
    }

    public String getNome() {
        return nome;
    }

    /**
     * O idioma de um texto vindo do banco ou de um pedido. Aceita o nome ("en"),
     * a etiqueta ("en-US") e o "PT-BR" que os primeiros seeds gravaram. Vazio para
     * o que não for um idioma conhecido - quem chama decide se isso é erro.
     */
    public static Optional<Idioma> de(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String v = valor.trim().toUpperCase(Locale.ROOT).replace('_', '-');
        int hifen = v.indexOf('-');
        String base = hifen > 0 ? v.substring(0, hifen) : v;
        for (Idioma idioma : values()) {
            if (idioma.name().equals(base)) {
                return Optional.of(idioma);
            }
        }
        return Optional.empty();
    }

    /** Idioma da palavra, com o português como padrão para o que não estiver preenchido. */
    public static Idioma daPalavra(String valor) {
        return de(valor).orElse(PT);
    }
}
