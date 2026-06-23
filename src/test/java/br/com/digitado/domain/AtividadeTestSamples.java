package br.com.digitado.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AtividadeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static Atividade getAtividadeSample1() {
        return new Atividade().id(1L).titulo("titulo1").tempoLimite(1);
    }

    public static Atividade getAtividadeSample2() {
        return new Atividade().id(2L).titulo("titulo2").tempoLimite(2);
    }

    public static Atividade getAtividadeRandomSampleGenerator() {
        return new Atividade().id(longCount.incrementAndGet()).titulo(UUID.randomUUID().toString()).tempoLimite(intCount.incrementAndGet());
    }
}
