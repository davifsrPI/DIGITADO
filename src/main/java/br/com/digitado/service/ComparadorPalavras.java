package br.com.digitado.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Confere o que o jogador digitou contra a palavra certa: se bateu, que tipo de
 * erro foi, o subtipo e o que exatamente foi trocado, e o quanto da palavra ele
 * acertou.
 *
 * Vivia dentro do JogoSalaService. Saiu de lá quando o modo individual passou a
 * precisar da mesma régua: as duas telas classificando o erro de um jeito
 * diferente seria o histórico do jogador contando duas histórias.
 *
 * Espelha validarResposta.ts, que dá o retorno imediato na tela enquanto a
 * resposta do servidor não chega. Quem grava no histórico é este lado - mudou
 * aqui, muda lá.
 *
 * O ERRO EM TRÊS NÍVEIS (ver analisarErro):
 * - tipo: a família do erro, a que o aluno vê na tela ("Acentuação");
 * - subtipo: o jeito específico ("Faltou o acento", "Tecla vizinha");
 * - detalhe: o que foi trocado, no formato "certo→digitado" ("é→e", "ç→ss").
 *   Lado vazio quer dizer letra que faltou ("a→") ou sobrou ("→a").
 * Subtipo e detalhe só existem nos registros gravados a partir desta versão: o
 * histórico não guarda o que foi digitado, então os antigos ficam só com o tipo.
 */
public final class ComparadorPalavras {

    /** Tipo gravado no histórico quando o tempo acabou sem resposta nenhuma. */
    public static final String NAO_RESPONDEU = "NAO_RESPONDEU";

    // Os tipos, na ordem em que a classificação os testa
    public static final String ACENTUACAO = "ACENTUACAO";
    public static final String ESPACO_HIFEN = "ESPACO_HIFEN";
    public static final String ERRO_FONETICO = "ERRO_FONETICO";
    public static final String LETRAS_INVERTIDAS = "LETRAS_INVERTIDAS";
    public static final String TROCA_LETRA = "TROCA_LETRA";
    public static final String LETRA_FALTANDO = "LETRA_FALTANDO";
    public static final String LETRA_EXTRA = "LETRA_EXTRA";
    public static final String VARIOS_ERROS = "VARIOS_ERROS";
    public static final String OUTRA_PALAVRA = "OUTRA_PALAVRA";

    /**
     * O "erro genérico" de antes desta versão, hoje separado em VARIOS_ERROS e
     * OUTRA_PALAVRA. Não é mais gravado, mas continua no histórico antigo (e é o
     * que vale para erro sem tipo nenhum).
     */
    public static final String OUTRO = "OUTRO";

    /** Todos os tipos que podem aparecer no histórico. */
    public static final List<String> TIPOS = List.of(
        ACENTUACAO,
        ESPACO_HIFEN,
        ERRO_FONETICO,
        LETRAS_INVERTIDAS,
        TROCA_LETRA,
        LETRA_FALTANDO,
        LETRA_EXTRA,
        VARIOS_ERROS,
        OUTRA_PALAVRA,
        OUTRO,
        NAO_RESPONDEU
    );

    // Espaço fixo (U+00A0): vem em texto colado de documento e sobrevive ao trim,
    // que só apara caracteres de controle e o espaço comum
    private static final char ESPACO_FIXO = (char) 0x00A0;

    /**
     * Equivalências fonéticas do português, aplicadas em ordem: grafias distintas
     * que soam igual caem no mesmo texto. É a tabela que separa ERRO_FONETICO
     * ("xave" por "chave") de um erro qualquer de digitação.
     *
     * Aplicada sobre o texto AINDA acentuado, por causa da cedilha - ver som().
     * A ordem importa: "sc" e "xc" viram "s" antes de o "c" solto ser tratado, e
     * "ch", "lh" e "nh" saem antes de o "h" mudo do começo da palavra.
     */
    private static final String[][] REGRAS_FONETICAS = {
        { "sc(?=[eiéêí])", "s" }, // nascer / naser
        { "sç", "s" }, // desça / dessa
        { "xc(?=[eiéêí])", "s" }, // exceção / esseção
        { "x(?=[ptc])", "s" }, // texto / testo, explicar / esplicar
        { "ç", "s" },
        { "ss", "s" },
        { "rr", "r" },
        { "ph", "f" },
        { "ch", "x" },
        { "lh", "l" },
        { "nh", "n" },
        { "(^|\\s)h", "$1" }, // hora / ora: o h do começo não tem som
        { "g(?=[eiéêí])", "j" }, // gente / jente
        { "n(?=[pb])", "m" }, // campo / canpo
        { "m(?=[^pbaeiouáéíóúâêôãõàü]|$)", "n" }, // homem / homen, tanto / tamto
        { "l(?=[^aeiouáéíóúâêôãõàü]|$)", "u" }, // Brasil / Brasiu: l no fim da sílaba soa u
        { "[qk]", "c" },
        { "y", "i" },
        { "w", "v" },
        { "[sz]", "z" },
    };

    // Similaridade fixa dos dois erros "de quase": a pessoa sabia a palavra e
    // tropeçou num detalhe. Os mesmos valores da tela (validarResposta.ts).
    private static final int SIMILARIDADE_ACENTUACAO = 95;
    private static final int SIMILARIDADE_FONETICO = 85;

    /** Abaixo disso a resposta é outra palavra, e não a certa com vários erros. */
    private static final int SIMILARIDADE_MINIMA_VARIOS_ERROS = 50;

    /** Trecho maior que isso no detalhe deixa de ser "o que foi trocado" e vira a palavra inteira. */
    private static final int TAMANHO_MAXIMO_TRECHO = 8;

    private static final String VOGAIS = "aeiou";

    // Teclado ABNT2, sem acento: cada fileira fica meia tecla à direita da de cima
    private static final String[] FILEIRAS_TECLADO = { "qwertyuiop", "asdfghjklç", "zxcvbnm" };

    // Letras que são a mesma forma virada ou espelhada - confusão típica de quem está
    // aprendendo a escrever, diferente de uma troca qualquer
    private static final Set<String> PARES_ESPELHADOS = Set.of("bd", "db", "pq", "qp", "bp", "pb", "dq", "qd", "nu", "un", "mw", "wm");

    private ComparadorPalavras() {}

    /** O resultado da conferência, já com tudo o que o histórico guarda. */
    public record Conferencia(boolean correta, String tipoErro, int similaridade, String subtipoErro, String detalheErro) {
        /** Conferência sem subtipo nem detalhe (acerto, ou rodada sem resposta). */
        public Conferencia(boolean correta, String tipoErro, int similaridade) {
            this(correta, tipoErro, similaridade, null, null);
        }
    }

    /** O erro destrinchado: tipo, subtipo e o trecho "certo→digitado" (ver o comentário da classe). */
    public record AnaliseErro(String tipo, String subtipo, String detalhe) {}

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
        AnaliseErro erro = analisarErro(d, c);
        return new Conferencia(false, erro.tipo(), similaridade(d, c), erro.subtipo(), erro.detalhe());
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

    /** Só o tipo do erro (ver analisarErro). */
    public static String classificarErro(String digitadoCanonico, String corretoCanonico) {
        return analisarErro(digitadoCanonico, corretoCanonico).tipo();
    }

    /**
     * Classifica o erro do aluno, na ordem em que a tela explica para ele:
     * acentuação, espaço/hífen, som, uma letra fora do lugar e, por fim, os erros
     * de mais de uma letra.
     *
     * Recebe o texto CANÔNICO (minúsculo, mas ainda com acento e cedilha), porque
     * cada camada precisa de uma forma diferente: a acentuação compara sem acento,
     * o som precisa da cedilha, e a contagem de letras compara sem acento - quem
     * esqueceu uma letra E um acento conta como letra faltando.
     */
    public static AnaliseErro analisarErro(String digitadoCanonico, String corretoCanonico) {
        String d = digitadoCanonico;
        String c = corretoCanonico;
        if (d.isEmpty()) {
            return new AnaliseErro(NAO_RESPONDEU, null, null);
        }
        String dSem = semAcento(d);
        String cSem = semAcento(c);
        if (dSem.equals(cSem)) {
            return analisarAcentos(d, c);
        }
        if (semSeparador(dSem).equals(semSeparador(cSem))) {
            return analisarSeparadores(dSem, cSem);
        }
        // Escreveu com o som certo e a grafia errada ("xave" por "chave"): vem antes
        // da contagem de letras porque "ss" → "s" muda o tamanho e o erro cairia em
        // LETRA_FALTANDO, que não diz nada ao aluno sobre o que ele confundiu
        if (som(d).equals(som(c))) {
            String[] trecho = trechoDiferente(c, d);
            String[] dobrado = d.length() < c.length()
                ? trechoDobrado(c, prefixoComum(c, d), trecho[0], trecho[1])
                : trechoInvertido(trechoDobrado(d, prefixoComum(c, d), trecho[1], trecho[0]));
            String[] legivel = dobrado != null ? dobrado : trecho;
            return new AnaliseErro(ERRO_FONETICO, familiaDoSom(legivel[0], legivel[1]), detalhe(legivel));
        }
        // Distância com inversão: "pdera" por "pedra" é UM deslize (duas letras
        // trocadas de lugar), não duas letras erradas
        int dist = distanciaComInversao(dSem, cSem);
        if (dist == 1) {
            return analisarUmaLetra(dSem, cSem);
        }
        if (similaridade(d, c) >= SIMILARIDADE_MINIMA_VARIOS_ERROS) {
            String[] trecho = trechoDiferente(c, d);
            boolean curto = trecho[0].length() <= TAMANHO_MAXIMO_TRECHO && trecho[1].length() <= TAMANHO_MAXIMO_TRECHO;
            return new AnaliseErro(VARIOS_ERROS, dist == 2 ? "DUAS_DIFERENCAS" : "TRES_OU_MAIS", curto ? detalhe(trecho) : null);
        }
        return new AnaliseErro(OUTRA_PALAVRA, null, null);
    }

    // ===== As camadas =====

    /** Só o acento (ou a cedilha) mudou: quais letras e de que jeito. */
    private static AnaliseErro analisarAcentos(String d, String c) {
        if (d.length() != c.length()) {
            // Combinação de acentos que o NFC não junta: sabe-se o tipo, não a letra
            return new AnaliseErro(ACENTUACAO, null, null);
        }
        String jeito = null;
        List<String> pares = new ArrayList<>();
        for (int i = 0; i < c.length(); i++) {
            char certo = c.charAt(i);
            char digitado = d.charAt(i);
            if (certo == digitado) {
                continue;
            }
            boolean certoMarcado = temMarca(certo);
            boolean digitadoMarcado = temMarca(digitado);
            String esteJeito;
            if (certoMarcado && !digitadoMarcado) {
                esteJeito = certo == 'ç' ? "CEDILHA_FALTANDO" : "ACENTO_FALTANDO";
            } else if (!certoMarcado && digitadoMarcado) {
                esteJeito = digitado == 'ç' ? "CEDILHA_SOBRANDO" : "ACENTO_SOBRANDO";
            } else {
                esteJeito = "ACENTO_TROCADO";
            }
            jeito = jeito == null || jeito.equals(esteJeito) ? esteJeito : "ACENTOS_VARIOS";
            if (pares.size() < 3) {
                pares.add(certo + "→" + digitado);
            }
        }
        return new AnaliseErro(ACENTUACAO, jeito, pares.isEmpty() ? null : String.join(", ", pares));
    }

    /** As letras estão todas certas; o que mudou foram os espaços e hífens. */
    private static AnaliseErro analisarSeparadores(String dSem, String cSem) {
        String[] trecho = trechoDiferente(cSem, dSem);
        String certo = trecho[0];
        String digitado = trecho[1];
        String jeito;
        if (certo.equals(" ") && digitado.isEmpty()) {
            jeito = "ESPACO_FALTANDO";
        } else if (certo.isEmpty() && digitado.equals(" ")) {
            jeito = "ESPACO_SOBRANDO";
        } else if (certo.equals("-") && digitado.isEmpty()) {
            jeito = "HIFEN_FALTANDO";
        } else if (certo.isEmpty() && digitado.equals("-")) {
            jeito = "HIFEN_SOBRANDO";
        } else if (certo.equals("-") && digitado.equals(" ")) {
            jeito = "HIFEN_POR_ESPACO";
        } else if (certo.equals(" ") && digitado.equals("-")) {
            jeito = "ESPACO_POR_HIFEN";
        } else {
            jeito = "SEPARADORES_VARIOS";
        }
        // Um espaço sozinho não diz nada: o detalhe leva uma letra de cada lado ("a-c→ac")
        return new AnaliseErro(ESPACO_HIFEN, jeito, detalhe(trechoComContexto(cSem, dSem)));
    }

    /** Exatamente uma letra fora: faltou, sobrou, trocou ou inverteu com a vizinha. */
    private static AnaliseErro analisarUmaLetra(String dSem, String cSem) {
        String[] trecho = trechoDiferente(cSem, dSem);
        String certo = trecho[0];
        String digitado = trecho[1];
        int prefixo = prefixoComum(cSem, dSem);

        if (
            certo.length() == 2 && digitado.length() == 2 && certo.charAt(0) == digitado.charAt(1) && certo.charAt(1) == digitado.charAt(0)
        ) {
            boolean v1 = vogal(certo.charAt(0));
            boolean v2 = vogal(certo.charAt(1));
            String jeito = v1 && v2 ? "INVERTEU_VOGAIS" : !v1 && !v2 ? "INVERTEU_CONSOANTES" : "INVERTEU_VOGAL_CONSOANTE";
            return new AnaliseErro(LETRAS_INVERTIDAS, jeito, detalhe(trecho));
        }

        if (dSem.length() < cSem.length()) {
            // A letra que faltou é igual à vizinha? Então era uma letra dobrada ("pizza")
            char faltou = certo.isEmpty() ? ' ' : certo.charAt(0);
            String[] dobrado = trechoDobrado(cSem, prefixo, certo, digitado);
            if (dobrado != null) {
                return new AnaliseErro(LETRA_FALTANDO, "FALTOU_LETRA_DOBRADA", detalhe(dobrado));
            }
            return new AnaliseErro(LETRA_FALTANDO, vogal(faltou) ? "FALTOU_VOGAL" : "FALTOU_CONSOANTE", detalhe(trecho));
        }

        if (dSem.length() > cSem.length()) {
            char sobrou = digitado.isEmpty() ? ' ' : digitado.charAt(0);
            String[] repetido = trechoInvertido(trechoDobrado(dSem, prefixo, digitado, certo));
            if (repetido != null) {
                return new AnaliseErro(LETRA_EXTRA, "LETRA_REPETIDA", detalhe(repetido));
            }
            char antes = prefixo > 0 ? dSem.charAt(prefixo - 1) : 0;
            char depois = prefixo + 1 < dSem.length() ? dSem.charAt(prefixo + 1) : 0;
            boolean vizinha = teclasVizinhas(sobrou, antes) || teclasVizinhas(sobrou, depois);
            String jeito = vizinha ? "TECLA_VIZINHA_JUNTO" : vogal(sobrou) ? "SOBROU_VOGAL" : "SOBROU_CONSOANTE";
            return new AnaliseErro(LETRA_EXTRA, jeito, detalhe(trecho));
        }

        // Mesmo tamanho, uma letra diferente
        char letraCerta = certo.isEmpty() ? ' ' : certo.charAt(0);
        char letraDigitada = digitado.isEmpty() ? ' ' : digitado.charAt(0);
        String jeito;
        if (PARES_ESPELHADOS.contains("" + letraCerta + letraDigitada)) {
            jeito = "LETRA_ESPELHADA";
        } else if (teclasVizinhas(letraCerta, letraDigitada)) {
            jeito = "TECLA_VIZINHA";
        } else {
            jeito = vogal(letraCerta) ? "TROCOU_VOGAL" : "TROCOU_CONSOANTE";
        }
        return new AnaliseErro(TROCA_LETRA, jeito, detalhe(trecho));
    }

    /**
     * A família do som confundido, pelas letras do trecho trocado: "ç" e "ss" são
     * som de S; "ch" e "x", som de X; e assim por diante.
     */
    static String familiaDoSom(String certo, String digitado) {
        String letras = (certo + digitado).replaceAll("\\s", "");
        if (letras.isEmpty() || certo.length() > TAMANHO_MAXIMO_TRECHO || digitado.length() > TAMANHO_MAXIMO_TRECHO) {
            return "SOM_VARIOS";
        }
        if ((certo.equals("h") && digitado.isEmpty()) || (certo.isEmpty() && digitado.equals("h"))) {
            return "H_MUDO";
        }
        if (letras.contains("x") && letras.contains("h") && soCom(letras, "chx")) return "SOM_CH_X";
        if (soCom(letras, "gj")) return "SOM_G_J";
        if (soCom(letras, "sçczx")) return "SOM_S_Z";
        if (soCom(letras, "r")) return "SOM_R_RR";
        if (soCom(letras, "lu")) return "SOM_L_U";
        if (soCom(letras, "mn")) return "SOM_M_N";
        if (soCom(letras, "lnhi")) return "SOM_LH_NH";
        if (soCom(letras, "qucks")) return "SOM_C_QU";
        if (soCom(letras, "phfyiwv")) return "SOM_OUTRO";
        return "SOM_VARIOS";
    }

    // ===== Trechos =====

    /**
     * O pedaço que muda entre as duas palavras: tira o começo e o fim que elas têm
     * em comum. "caça" x "cassa" -> ["ç", "ss"]. Devolve [trechoDoPrimeiro, trechoDoSegundo].
     */
    static String[] trechoDiferente(String a, String b) {
        int prefixo = prefixoComum(a, b);
        int sufixo = 0;
        int limite = Math.min(a.length(), b.length()) - prefixo;
        while (sufixo < limite && a.charAt(a.length() - 1 - sufixo) == b.charAt(b.length() - 1 - sufixo)) {
            sufixo++;
        }
        return new String[] { a.substring(prefixo, a.length() - sufixo), b.substring(prefixo, b.length() - sufixo) };
    }

    // O mesmo trecho com uma letra de cada lado - para o que não se lê sozinho (espaço, hífen)
    private static String[] trechoComContexto(String a, String b) {
        String[] trecho = trechoDiferente(a, b);
        int prefixo = prefixoComum(a, b);
        int sufixo = a.length() - prefixo - trecho[0].length();
        int ini = Math.max(0, prefixo - 1);
        int fimA = Math.min(a.length(), a.length() - sufixo + 1);
        int fimB = Math.min(b.length(), b.length() - sufixo + 1);
        return new String[] { a.substring(ini, fimA), b.substring(ini, fimB) };
    }

    /**
     * Quando a letra que falta (ou sobra) é igual à vizinha, o trecho certo é o par:
     * "pizza" x "piza" -> ["zz", "z"] em vez de ["z", ""]. Devolve null quando não é o caso.
     */
    private static String[] trechoDobrado(String maior, int prefixo, String trechoMaior, String trechoMenor) {
        if (trechoMaior.length() != 1 || !trechoMenor.isEmpty()) {
            return null;
        }
        char letra = trechoMaior.charAt(0);
        int pos = prefixo;
        boolean igualAntes = pos > 0 && maior.charAt(pos - 1) == letra;
        boolean igualDepois = pos + 1 < maior.length() && maior.charAt(pos + 1) == letra;
        if (!igualAntes && !igualDepois) {
            return null;
        }
        return new String[] { "" + letra + letra, "" + letra };
    }

    // trechoDobrado devolve [palavra com a letra a mais, a outra]; quando a mais é a
    // digitada, o detalhe "certo→digitado" precisa da ordem ao contrário
    private static String[] trechoInvertido(String[] trecho) {
        return trecho == null ? null : new String[] { trecho[1], trecho[0] };
    }

    private static int prefixoComum(String a, String b) {
        int i = 0;
        int limite = Math.min(a.length(), b.length());
        while (i < limite && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static String detalhe(String[] trecho) {
        return trecho[0] + "→" + trecho[1];
    }

    // ===== Utilitários =====

    // Remove acentos para comparação sem diferenciar versões acentuadas
    static String semAcento(String s) {
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll(
            "\\p{InCombiningDiacriticalMarks}+",
            ""
        );
    }

    private static String semSeparador(String s) {
        return s.replaceAll("[\\s-]", "");
    }

    private static boolean temMarca(char ch) {
        return !semAcento(String.valueOf(ch)).equals(String.valueOf(ch));
    }

    private static boolean vogal(char ch) {
        return VOGAIS.indexOf(ch) >= 0;
    }

    private static boolean soCom(String letras, String permitidas) {
        return letras.chars().allMatch(ch -> permitidas.indexOf(ch) >= 0);
    }

    /** As duas letras são teclas encostadas no teclado ABNT2? */
    static boolean teclasVizinhas(char a, char b) {
        if (a == b || a == 0 || b == 0) {
            return false;
        }
        int[] pa = posicaoNoTeclado(a);
        int[] pb = posicaoNoTeclado(b);
        if (pa == null || pb == null) {
            return false;
        }
        int linhas = pb[0] - pa[0];
        if (linhas == 0) {
            return Math.abs(pb[1] - pa[1]) == 1;
        }
        if (Math.abs(linhas) != 1) {
            return false;
        }
        // A fileira de baixo começa meia tecla à direita: a tecla i de baixo encosta
        // nas teclas i e i+1 de cima
        int[] cima = linhas > 0 ? pa : pb;
        int[] baixo = linhas > 0 ? pb : pa;
        return baixo[1] == cima[1] || baixo[1] == cima[1] - 1;
    }

    private static int[] posicaoNoTeclado(char ch) {
        for (int linha = 0; linha < FILEIRAS_TECLADO.length; linha++) {
            int coluna = FILEIRAS_TECLADO[linha].indexOf(ch);
            if (coluna >= 0) {
                return new int[] { linha, coluna };
            }
        }
        return null;
    }

    /**
     * Reduz a palavra ao "som" dela, para separar quem errou a GRAFIA de quem
     * errou a palavra. Grafias diferentes que soam igual caem no mesmo texto:
     * "caça" e "cassa" viram "caza"; "xave" e "chave" viram "xave".
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

    /**
     * Levenshtein que conta a inversão de duas letras vizinhas como UMA edição
     * (distância de Damerau restrita). Só a classificação usa: a similaridade
     * continua com o Levenshtein de sempre, que é o que a equação do modo
     * individual conhece.
     */
    static int distanciaComInversao(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j - 1] + custo, Math.min(dp[i - 1][j], dp[i][j - 1]) + 1);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
                }
            }
        }
        return dp[m][n];
    }
}
