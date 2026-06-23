package br.com.digitado.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PalavraTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Palavra getPalavraSample1() {
        return new Palavra().id(1L).texto("texto1").categoria("categoria1").idioma("idioma1");
    }

    public static Palavra getPalavraSample2() {
        return new Palavra().id(2L).texto("texto2").categoria("categoria2").idioma("idioma2");
    }

    public static Palavra getPalavraRandomSampleGenerator() {
        return new Palavra()
            .id(longCount.incrementAndGet())
            .texto(UUID.randomUUID().toString())
            .categoria(UUID.randomUUID().toString())
            .idioma(UUID.randomUUID().toString());
    }
}
