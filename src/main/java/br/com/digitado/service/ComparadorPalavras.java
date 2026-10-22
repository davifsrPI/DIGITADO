package br.com.digitado.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Confere o que o jogador digitou contra a palavra certa: se bateu, que tipo de
 * erro foi e o quanto da palavra ele acertou.
 *
 * Vivia dentro do JogoSalaService. Saiu de lá quando o modo individual passou a
 * precisar da mesma régua: as duas telas classificando o erro de um jeito
 * diferente seria o histórico do jogador contando duas histórias.
 *
 * Espelha validarResposta.ts, que dá o retorno imediato na tela enquanto a
 * resposta do servidor não chega. Quem grava no histórico é este lado - mudou
 * aqui, muda lá.
 */
public final class ComparadorPalavras {

    /** Tipo gravado no histórico quando o tempo acabou sem resposta nenhuma. */
    public static final String NAO_RESPONDEU = "NAO_RESPONDEU";

    // Espaço fixo (U+00A0): vem em texto colado de documento e sobrevive ao trim,
    // que só apara caracteres de controle e o espaço comum
    private static final char ESPACO_FIXO = (char) 0x00A0;

    /**
     * Equivalências fonéticas do português, aplicadas em ordem: grafias distintas
     * que soam igual caem no mesmo texto. É a tabela que separa ERRO_FONETICO
     * ("xave" por "chave") de um erro qualquer de digitação.
     *
     * Aplicada sobre o texto AINDA acentuado, por causa da cedilha - ver som().
     */
    private static final String[][] REGRAS_FONETICAS = {
        { "ç", "s" },
        { "ss", "s" },
        { "rr", "r" },
        { "ph", "f" },
        { "ch", "x" },
        { "lh", "l" },
        { "nh", "n" },
        { "[qk]", "c" },
        { "y", "i" },
        { "w", "v" },
        { "[sz]", "z" },
    };

    // Similaridade fixa dos dois erros "de quase": a pessoa sabia a palavra e
    // tropeçou num detalhe. Os mesmos valores da tela (validarResposta.ts).
    private static final int SIMILARIDADE_ACENTUACAO = 95;
    private static final int SIMILARIDADE_FONETICO = 85;

    private ComparadorPalavras() {}

    /** O resultado da conferência, já com tudo o que o histórico guarda. */
    public record Conferencia(boolean correta, String tipoErro, int similaridade) {}

    /** Confere a resposta inteira de uma vez. Resposta vazia conta como não respondida. */
    public static Conferencia conferir(String digitado, String correto) {
        String d = canonico(digitado);
        String c = canonico(correto);
        if (d.isEmpty()) {
            return new Conferencia(false, NAO_RESPONDEU, 0);
        }
        if (d.equals(c)) {
            return new Conferencia(true, null, 100);
        }
        return new Conferencia(false, classificarErro(d, c), similaridade(d, c));
    }

    /**
     * Forma canônica usada para decidir se a resposta bate com a palavra.
     *
     * NFC: "ã" pode chegar como um caractere só ou como "a" + til combinante. Para
     * quem lê é a mesma letra, mas como texto são sequências diferentes - sem
     * normalizar, uma palavra cadastrada por cópia de um documento (é comum vir
     * decomposta) marcava como ERRADA a resposta digitada exatamente igual, e o aluno
     * terminava a rodada sem ponto jurando que tinha acertado.
     *
     * O espaço fixo (NBSP) também vem nessas colagens e sobrevive ao trim comum, por
     * isso vira espaço normal antes de aparar as pontas.
     */
    public static String canonico(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFC).replace(ESPACO_FIXO, ' ').trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Quanto da palavra a pessoa acertou, de 0 a 100.
     *
     * Acento e som têm valor fixo (sabia a palavra, errou o detalhe); fora disso é
     * a distância de edição sobre o texto sem acento, dividida pelo tamanho da maior
     * das duas palavras - "casa" por "caza" fica em 75.
     */
    public static int similaridade(String digitadoCanonico, String corretoCanonico) {
        if (digitadoCanonico.equals(corretoCanonico)) return 100;
        if (digitadoCanonico.isEmpty()) return 0;
        String dSem = semAcento(digitadoCanonico);
        String cSem = semAcento(corretoCanonico);
        if (dSem.equals(cSem)) return SIMILARIDADE_ACENTUACAO;
        if (som(digitadoCanonico).equals(som(corretoCanonico))) return SIMILARIDADE_FONETICO;
        int maior = Math.max(digitadoCanonico.length(), corretoCanonico.length());
        if (maior == 0) return 0;
        double fracao = 1.0 - (double) levenshtein(dSem, cSem) / maior;
        return (int) Math.round(Math.max(0, fracao) * 100);
    }

    /**
     * Classifica o erro do aluno, na ordem em que a tela explica para ele:
     * acentuação, som, uma letra fora de lugar, e por fim o erro genérico.
     *
     * Recebe o texto CANÔNICO (minúsculo, mas ainda com acento e cedilha), porque
     * cada camada precisa de uma forma diferente: a acentuação compara sem acento,
     * o som precisa da cedilha, e a contagem de letras compara sem acento.
     */
    public static String classificarErro(String digitadoCanonico, String corretoCanonico) {
        String digitadoSemAcento = semAcento(digitadoCanonico);
        String corretoSemAcento = semAcento(corretoCanonico);
        if (digitadoSemAcento.equals(corretoSemAcento)) return "ACENTUACAO";
        // Escreveu com o som certo e a grafia errada ("xave" por "chave"): vem antes
        // da contagem de letras porque "ss" → "s" muda o tamanho e o erro cairia em
        // LETRA_FALTANDO, que não diz nada ao aluno sobre o que ele confundiu
        if (som(digitadoCanonico).equals(som(corretoCanonico))) return "ERRO_FONETICO";
        int dist = levenshtein(digitadoSemAcento, corretoSemAcento);
        if (dist == 1) {
            if (digitadoSemAcento.length() < corretoSemAcento.length()) return "LETRA_FALTANDO";
            if (digitadoSemAcento.length() > corretoSemAcento.length()) return "LETRA_EXTRA";
            return "TROCA_LETRA";
        }
        return "OUTRO";
    }

    // Remove acentos para comparação sem diferenciar versões acentuadas
    static String semAcento(String s) {
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll(
            "\\p{InCombiningDiacriticalMarks}+",
            ""
        );
    }

    /**
     * Reduz a palavra ao "som" dela, para separar quem errou a GRAFIA de quem
     * errou a palavra. Grafias diferentes que soam igual caem no mesmo texto:
     * "caça" e "cassa" viram "casa"; "xave" e "chave" viram "xave".
     *
     * A ordem importa: as regras rodam ANTES de tirar os acentos, porque o NFD
     * decompõe "ç" em "c" + cedilha e a cedilha é um diacrítico como qualquer
     * outro - tirada primeiro, "caça" virava "caca" e a regra ç → s nunca chegava
     * a valer.
     */
    static String som(String canonico) {
        String r = canonico;
        for (String[] regra : REGRAS_FONETICAS) {
            r = r.replaceAll(regra[0], regra[1]);
        }
        return semAcento(r);
    }

    // Algoritmo de Levenshtein: o número mínimo de edições (inserção, remoção,
    // substituição) para transformar uma string na outra
    static int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                    ? dp[i - 1][j - 1]
                    : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[m][n];
    }
}
