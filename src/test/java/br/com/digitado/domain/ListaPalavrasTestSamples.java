package br.com.digitado.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ListaPalavrasTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static ListaPalavras getListaPalavrasSample1() {
        return new ListaPalavras().id(1L).nomeLista("nomeLista1");
    }

    public static ListaPalavras getListaPalavrasSample2() {
        return new ListaPalavras().id(2L).nomeLista("nomeLista2");
    }

    public static ListaPalavras getListaPalavrasRandomSampleGenerator() {
        return new ListaPalavras().id(longCount.incrementAndGet()).nomeLista(UUID.randomUUID().toString());
    }
}
