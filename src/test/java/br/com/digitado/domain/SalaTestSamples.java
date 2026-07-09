package br.com.digitado.domain;

import java.util.UUID;

public class SalaTestSamples {

    public static Sala getSalaSample1() {
        return new Sala().codigo("codigo1").nome("nome1");
    }

    public static Sala getSalaSample2() {
        return new Sala().codigo("codigo2").nome("nome2");
    }

    public static Sala getSalaRandomSampleGenerator() {
        return new Sala().codigo(UUID.randomUUID().toString()).nome(UUID.randomUUID().toString());
    }
}
