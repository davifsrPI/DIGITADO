package br.com.digitado.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RankingTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static Ranking getRankingSample1() {
        return new Ranking().id(1L).posicao(1).pontuacaoTotal(1);
    }

    public static Ranking getRankingSample2() {
        return new Ranking().id(2L).posicao(2).pontuacaoTotal(2);
    }

    public static Ranking getRankingRandomSampleGenerator() {
        return new Ranking().id(longCount.incrementAndGet()).posicao(intCount.incrementAndGet()).pontuacaoTotal(intCount.incrementAndGet());
    }
}
