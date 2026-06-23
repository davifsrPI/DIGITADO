package br.com.digitado.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class ErroOrtograficoTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static ErroOrtografico getErroOrtograficoSample1() {
        return new ErroOrtografico().id(1L);
    }

    public static ErroOrtografico getErroOrtograficoSample2() {
        return new ErroOrtografico().id(2L);
    }

    public static ErroOrtografico getErroOrtograficoRandomSampleGenerator() {
        return new ErroOrtografico().id(longCount.incrementAndGet());
    }
}
