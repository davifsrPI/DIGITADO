package br.com.digitado.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class SalaTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Sala getSalaSample1() {
        return new Sala().id(1L).nome("nome1").codigo("codigo1");
    }

    public static Sala getSalaSample2() {
        return new Sala().id(2L).nome("nome2").codigo("codigo2");
    }

    public static Sala getSalaRandomSampleGenerator() {
        return new Sala().id(longCount.incrementAndGet()).nome(UUID.randomUUID().toString()).codigo(UUID.randomUUID().toString());
    }
}
