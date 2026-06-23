package br.com.digitado.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class UsuarioConquistaTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static UsuarioConquista getUsuarioConquistaSample1() {
        return new UsuarioConquista().id(1L).progresso(1);
    }

    public static UsuarioConquista getUsuarioConquistaSample2() {
        return new UsuarioConquista().id(2L).progresso(2);
    }

    public static UsuarioConquista getUsuarioConquistaRandomSampleGenerator() {
        return new UsuarioConquista().id(longCount.incrementAndGet()).progresso(intCount.incrementAndGet());
    }
}
