package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Dificuldade;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Audio das palavras gerado no servidor - e o que permite parar de transmitir o
 * texto da palavra para o aparelho do aluno.
 *
 * O teste da sintese de verdade e CONDICIONAL: ele depende de um sintetizador
 * instalado na maquina (espeak-ng, ou a voz do Windows). Onde nao houver, o teste
 * e pulado em vez de falhar - a ausencia do programa nao e defeito do codigo, e o
 * proprio servico trata esse caso voltando a transmitir o texto.
 */
class PalavraAudioServiceTest {

    private static Palavra palavra(long id, String texto) {
        Palavra p = new Palavra();
        p.setId(id);
        p.setTexto(texto);
        p.setDificuldadeCadastrada(Dificuldade.FACIL);
        return p;
    }

    private PalavraAudioService servicoLigado() {
        return new PalavraAudioService("", true);
    }

    @Test
    @DisplayName("desligado por configuracao nao gera audio nenhum")
    void desligadoNaoGeraAudio() {
        PalavraAudioService servico = new PalavraAudioService("", false);

        servico.prepararAudio(palavra(1L, "casa"));

        assertThat(servico.disponivel()).isFalse();
        assertThat(servico.temAudio(1L)).isFalse();
        assertThat(servico.audio(1L)).isEmpty();
    }

    @Test
    @DisplayName("comando inexistente nao derruba nada, so fica sem audio")
    void comandoInexistenteNaoDerruba() {
        PalavraAudioService servico = new PalavraAudioService("programa-que-nao-existe-digitado {arquivoTexto} {arquivoAudio}", true);

        // Sem excecao: ficar sem audio faz o jogo voltar a transmitir o texto,
        // enquanto uma excecao aqui derrubaria a rodada
        servico.prepararAudio(palavra(1L, "casa"));

        assertThat(servico.temAudio(1L)).isFalse();
    }

    @Test
    @DisplayName("palavra sem id ou sem texto e ignorada")
    void palavraIncompletaEIgnorada() {
        PalavraAudioService servico = servicoLigado();

        servico.prepararAudio(null);
        servico.prepararAudio(palavra(1L, null));
        Palavra semId = new Palavra();
        semId.setTexto("casa");
        servico.prepararAudio(semId);

        assertThat(servico.temAudio(1L)).isFalse();
    }

    @Test
    @DisplayName("com sintetizador na maquina, gera um WAV de verdade e o guarda")
    void geraWavDeVerdade() {
        PalavraAudioService servico = servicoLigado();
        Assumptions.assumeTrue(servico.disponivel(), "sem sintetizador de voz nesta maquina (espeak-ng ou SAPI do Windows)");

        servico.prepararAudio(palavra(7L, "exceção"));

        assertThat(servico.temAudio(7L)).isTrue();
        byte[] audio = servico.audio(7L).orElseThrow();
        // Cabecalho RIFF/WAVE: confere que saiu audio, e nao um arquivo vazio
        assertThat(audio.length).isGreaterThan(1000);
        assertThat(new String(audio, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(audio, 8, 4)).isEqualTo("WAVE");
    }

    @Test
    @DisplayName("a segunda chamada aproveita o audio guardado")
    void reaproveitaOAudioGuardado() {
        PalavraAudioService servico = servicoLigado();
        Assumptions.assumeTrue(servico.disponivel(), "sem sintetizador de voz nesta maquina");

        servico.prepararAudio(palavra(7L, "casa"));
        byte[] primeiro = servico.audio(7L).orElseThrow();
        servico.prepararAudio(palavra(7L, "casa"));
        byte[] segundo = servico.audio(7L).orElseThrow();

        // Mesmo array: nao sintetizou de novo (sintetizar e processo externo)
        assertThat(segundo).isSameAs(primeiro);
    }

    @Test
    @DisplayName("acentos e cedilha chegam inteiros ao sintetizador")
    void acentosECedilhaChegamInteiros() {
        PalavraAudioService servico = servicoLigado();
        Assumptions.assumeTrue(servico.disponivel(), "sem sintetizador de voz nesta maquina");

        // O texto vai por ARQUIVO UTF-8, e nao na linha de comando: e o que garante
        // que "coração" nao chegue destrocado (e que palavra nenhuma vire argumento)
        servico.prepararAudio(palavra(9L, "coração"));

        assertThat(servico.temAudio(9L)).isTrue();
    }
}
