package br.com.digitado.domain;

import static br.com.digitado.domain.AtividadeTestSamples.*;
import static br.com.digitado.domain.ListaPalavrasTestSamples.*;
import static br.com.digitado.domain.SalaTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AtividadeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Atividade.class);
        Atividade atividade1 = getAtividadeSample1();
        Atividade atividade2 = new Atividade();
        assertThat(atividade1).isNotEqualTo(atividade2);

        atividade2.setId(atividade1.getId());
        assertThat(atividade1).isEqualTo(atividade2);

        atividade2 = getAtividadeSample2();
        assertThat(atividade1).isNotEqualTo(atividade2);
    }

    @Test
    void salaTest() {
        Atividade atividade = getAtividadeRandomSampleGenerator();
        Sala salaBack = getSalaRandomSampleGenerator();

        atividade.setSala(salaBack);
        assertThat(atividade.getSala()).isEqualTo(salaBack);

        atividade.sala(null);
        assertThat(atividade.getSala()).isNull();
    }

    @Test
    void listaTest() {
        Atividade atividade = getAtividadeRandomSampleGenerator();
        ListaPalavras listaPalavrasBack = getListaPalavrasRandomSampleGenerator();

        atividade.setLista(listaPalavrasBack);
        assertThat(atividade.getLista()).isEqualTo(listaPalavrasBack);

        atividade.lista(null);
        assertThat(atividade.getLista()).isNull();
    }
}
