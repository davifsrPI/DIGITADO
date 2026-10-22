package br.com.digitado.service;

import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.service.HistoricoRespostaService.EstatisticaPalavraJogador;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * A equação que escolhe a próxima palavra do modo individual.
 *
 * O modo individual não tem fim marcado: o jogador vai até onde conseguir chegar.
 * Para isso a palavra seguinte não pode ser sorteada às cegas - ela precisa ficar
 * um pouco mais difícil a cada acerto, e "difícil" depende de QUEM está jogando: a
 * palavra que a turma erra pode ser a que este aluno já domina, e vice-versa.
 *
 * As quatro medidas do jogador naquela palavra entram na conta (vêm do histórico de
 * respostas dele):
 *
 *   t  = nº de tentativas       a = nº de acertos
 *   T  = tempo médio gasto (ms) S = similaridade média com a palavra certa (0 a 1)
 *
 * 1) DOMÍNIO PESSOAL da palavra, de 0 (não sabe) a 1 (domina):
 *
 *      precisão   p = a / t
 *      rapidez    r = 1 - min(1, T / tempo da rodada)      (sem tempo medido: 0,5)
 *      Dp = 0,5·p + 0,3·S + 0,2·r                          (sem similaridade: S = p)
 *
 *    A precisão pesa mais porque é o que o ditado cobra; a similaridade separa o
 *    "quase" ("caza" por "casa") do "nem perto"; a rapidez separa quem sabe de quem
 *    acertou pensando muito.
 *
 * 2) CONFIANÇA: poucas tentativas dizem pouco. O domínio pessoal é misturado com a
 *    taxa de acerto de TODOS os jogadores naquela palavra (g), e o peso do pessoal
 *    cresce com as tentativas:
 *
 *      c = t / (t + 3)          D = c·Dp + (1 - c)·g
 *
 *    g também é suavizada: g = (acertos globais + 5·p0) / (tentativas globais + 5),
 *    com p0 = 0,80 (fácil), 0,55 (média) ou 0,30 (difícil), a dificuldade cadastrada
 *    - palavra que ninguém jogou ainda vale pelo que a curadoria disse dela.
 *
 * 3) DIFICULDADE PARA O JOGADOR:  d = 1 - D
 *
 * 4) ESCOLHA: o alvo começa fácil e sobe a cada acerto da partida,
 *
 *      alvo = min(0,85; 0,20 + 0,04·acertos)
 *
 *    e vence a palavra com menor  |d - alvo| - 0,08·[já errou esta palavra] + ruído,
 *    ruído entre 0 e 0,05. O bônus de revisão traz de volta, no momento certo, a
 *    palavra que o jogador errou antes; o ruído evita que duas partidas sejam iguais.
 *    Palavra já ditada na partida não volta.
 */
public final class EquacaoPalavraIndividual {

    static final double PESO_PRECISAO = 0.5;
    static final double PESO_SIMILARIDADE = 0.3;
    static final double PESO_RAPIDEZ = 0.2;
    static final double RAPIDEZ_DESCONHECIDA = 0.5;
    static final int CONFIANCA_K = 3;
    static final int PESO_PRIOR_GLOBAL = 5;
    static final double ALVO_INICIAL = 0.20;
    static final double ALVO_POR_ACERTO = 0.04;
    static final double ALVO_MAXIMO = 0.85;
    static final double BONUS_REVISAO = 0.08;
    static final double RUIDO = 0.05;

    private EquacaoPalavraIndividual() {}

    /**
     * Uma palavra que pode ser ditada: o que se sabe dela no acervo inteiro e quanto
     * tempo a rodada dela dura.
     */
    public record Candidata(
        long palavraId,
        Dificuldade dificuldade,
        long tentativasGlobais,
        long acertosGlobais,
        int tempoLimiteSegundos
    ) {}

    /** Taxa de acerto do acervo (todos os jogadores), suavizada pela dificuldade cadastrada. */
    static double taxaGlobal(Candidata c) {
        double p0 =
            switch (c.dificuldade() == null ? Dificuldade.MEDIO : c.dificuldade()) {
                case FACIL -> 0.80;
                case DIFICIL -> 0.30;
                default -> 0.55;
            };
        long tentativas = Math.max(0, c.tentativasGlobais());
        long acertos = Math.max(0, Math.min(c.acertosGlobais(), tentativas));
        return (acertos + PESO_PRIOR_GLOBAL * p0) / (tentativas + PESO_PRIOR_GLOBAL);
    }

    /** Domínio pessoal (0 a 1) do jogador na palavra, só com o que ELE fez nela. */
    static double dominioPessoal(EstatisticaPalavraJogador h, int tempoLimiteSegundos) {
        double precisao = (double) h.acertos() / h.tentativas();
        double similaridade = h.similaridadeMedia() != null ? limitar(h.similaridadeMedia() / 100.0) : precisao;
        double rapidez = RAPIDEZ_DESCONHECIDA;
        if (h.tempoMedioMs() != null && tempoLimiteSegundos > 0) {
            rapidez = 1 - limitar(h.tempoMedioMs() / (tempoLimiteSegundos * 1000.0));
        }
        return PESO_PRECISAO * precisao + PESO_SIMILARIDADE * similaridade + PESO_RAPIDEZ * rapidez;
    }

    /** d = 1 - D: quão difícil a palavra é PARA este jogador, de 0 (domina) a 1. */
    public static double dificuldadeParaJogador(Candidata c, EstatisticaPalavraJogador h) {
        double global = taxaGlobal(c);
        if (h == null || h.tentativas() <= 0) {
            return 1 - global;
        }
        double confianca = (double) h.tentativas() / (h.tentativas() + CONFIANCA_K);
        double dominio = confianca * dominioPessoal(h, c.tempoLimiteSegundos()) + (1 - confianca) * global;
        return limitar(1 - dominio);
    }

    /** O alvo de dificuldade da rodada: começa fácil e sobe a cada acerto. */
    public static double alvo(int acertosNaPartida) {
        return Math.min(ALVO_MAXIMO, ALVO_INICIAL + ALVO_POR_ACERTO * Math.max(0, acertosNaPartida));
    }

    /** A próxima palavra, ou vazio quando o acervo inteiro já foi ditado nesta partida. */
    public static Optional<Candidata> escolher(
        Collection<Candidata> candidatas,
        Map<Long, EstatisticaPalavraJogador> historico,
        Set<Long> jaDitadas,
        int acertosNaPartida,
        Random aleatorio
    ) {
        double alvo = alvo(acertosNaPartida);
        Candidata melhor = null;
        double melhorNota = Double.MAX_VALUE;
        for (Candidata c : candidatas) {
            if (jaDitadas.contains(c.palavraId())) {
                continue;
            }
            EstatisticaPalavraJogador h = historico.get(c.palavraId());
            double nota = Math.abs(dificuldadeParaJogador(c, h) - alvo) + RUIDO * aleatorio.nextDouble();
            if (h != null && h.acertos() < h.tentativas()) {
                nota -= BONUS_REVISAO;
            }
            if (nota < melhorNota) {
                melhorNota = nota;
                melhor = c;
            }
        }
        return Optional.ofNullable(melhor);
    }

    private static double limitar(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
