package br.com.digitado.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RespostaTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static Resposta getRespostaSample1() {
        return new Resposta().id(1L).respostaDigitada("respostaDigitada1").tempoResposta(1).pontuacao(1);
    }

    public static Resposta getRespostaSample2() {
        return new Resposta().id(2L).respostaDigitada("respostaDigitada2").tempoResposta(2).pontuacao(2);
    }

    public static Resposta getRespostaRandomSampleGenerator() {
        return new Resposta()
            .id(longCount.incrementAndGet())
            .respostaDigitada(UUID.randomUUID().toString())
            .tempoResposta(intCount.incrementAndGet())
            .pontuacao(intCount.incrementAndGet());
    }
}
