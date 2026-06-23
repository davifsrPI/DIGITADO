package br.com.digitado.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ConquistaTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static Conquista getConquistaSample1() {
        return new Conquista().id(1L).nome("nome1").xpRecompensa(1);
    }

    public static Conquista getConquistaSample2() {
        return new Conquista().id(2L).nome("nome2").xpRecompensa(2);
    }

    public static Conquista getConquistaRandomSampleGenerator() {
        return new Conquista().id(longCount.incrementAndGet()).nome(UUID.randomUUID().toString()).xpRecompensa(intCount.incrementAndGet());
    }
}
