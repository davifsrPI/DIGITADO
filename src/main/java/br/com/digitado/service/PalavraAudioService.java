package br.com.digitado.service;

import br.com.digitado.domain.Palavra;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Gera no SERVIDOR o áudio da palavra do ditado, para o aluno ouvir sem receber
 * o texto dela.
 *
 * O problema que isto resolve: o estado do jogo é transmitido para o tópico da
 * sala, que TODO aluno assina, e nele ia o texto da palavra - o aparelho do aluno
 * precisava dele para a síntese de voz do navegador. Ou seja, a resposta chegava
 * ao aluno antes de ele responder, e bastava abrir o console para lê-la. Todo o
 * cuidado contra cola (bloquear colagem e corretor, medir o tempo por letra) não
 * valia nada diante disso.
 *
 * Com o áudio vindo daqui, o texto para de ser transmitido enquanto a rodada está
 * aberta (ver JogoSalaService.buildEstado) e o aluno recebe som, não resposta.
 *
 * COMO O ÁUDIO É FEITO: por um programa de síntese instalado na máquina,
 * escolhido nesta ordem:
 *   1. o comando configurado em digitado.audio-palavra.comando;
 *   2. espeak-ng, se estiver no PATH (é o que o Dockerfile instala);
 *   3. a voz do Windows (SAPI via PowerShell), útil no ambiente de
 *      desenvolvimento.
 * Sem nenhum deles o serviço fica DESLIGADO e o jogo volta ao comportamento
 * antigo: o texto é transmitido e o navegador do aluno sintetiza a voz. É uma
 * degradação consciente - melhor a turma ouvir a palavra do que a aula parar.
 *
 * SOBRE A QUALIDADE: a voz do navegador (Google português do Brasil) é bem melhor
 * que a do espeak. Num ditado de ortografia isso importa, então vale medir com a
 * turma; para trocar por um sintetizador melhor (Piper, por exemplo) basta
 * apontar digitado.audio-palavra.comando para ele.
 */
@Service
public class PalavraAudioService {

    private static final Logger LOG = LoggerFactory.getLogger(PalavraAudioService.class);

    /** Palavras com áudio guardado em memória. Um WAV curto tem poucas dezenas de KB. */
    private static final int MAX_CACHE = 300;

    /** Um sintetizador que demora mais que isto está travado - não segura a rodada. */
    private static final long TIMEOUT_SEGUNDOS = 10;

    /** Tipo devolvido pelo endpoint; WAV toca em qualquer navegador sem plugin. */
    public static final String TIPO_AUDIO = "audio/wav";

    /**
     * Comando de síntese, opcional. Recebe dois marcadores:
     * {arquivoTexto} - arquivo UTF-8 com a palavra; {arquivoAudio} - WAV a gerar.
     * Exemplo: "espeak-ng -v pt-br -s 130 -f {arquivoTexto} -w {arquivoAudio}".
     *
     * O texto vai por ARQUIVO, nunca embutido na linha de comando: o comando é
     * executado sem shell e sem interpolar texto, então não há como uma palavra do
     * acervo virar argumento ou comando.
     */
    private final String comandoConfigurado;

    /** false desliga a síntese no servidor e volta à voz do navegador do aluno. */
    private final boolean habilitado;

    // Resolvido uma vez, na primeira necessidade: null = nenhum sintetizador
    private volatile List<String> comando;
    private volatile boolean comandoResolvido = false;

    // login → áudio; LRU simples, descarta a palavra menos usada ao encher
    private final Map<Long, byte[]> cache = Collections.synchronizedMap(
        new LinkedHashMap<Long, byte[]>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
                return size() > MAX_CACHE;
            }
        }
    );

    public PalavraAudioService(
        @Value("${digitado.audio-palavra.comando:}") String comandoConfigurado,
        @Value("${digitado.audio-palavra.habilitado:true}") boolean habilitado
    ) {
        this.comandoConfigurado = comandoConfigurado;
        this.habilitado = habilitado;
    }

    /**
     * Já existe áudio pronto para esta palavra?
     *
     * É o que decide se o texto pode ser OMITIDO da transmissão: só escondemos a
     * palavra do aluno quando temos certeza de que ele vai conseguir ouvi-la. Se a
     * síntese falhou, o texto segue sendo transmitido e o navegador dele fala -
     * ficar sem áudio nenhum seria pior que o risco de alguém espiar o console.
     */
    public boolean temAudio(Long palavraId) {
        return palavraId != null && cache.containsKey(palavraId);
    }

    /** O áudio da palavra, se houver. */
    public Optional<byte[]> audio(Long palavraId) {
        return palavraId == null ? Optional.empty() : Optional.ofNullable(cache.get(palavraId));
    }

    /**
     * Gera e guarda o áudio da palavra, se ainda não houver.
     *
     * Chamado uma vez por rodada, quando a palavra entra em jogo, e não a cada
     * mensagem: sintetizar é trabalho de processo externo.
     */
    public void prepararAudio(Palavra palavra) {
        if (palavra == null || palavra.getId() == null || palavra.getTexto() == null || !habilitado) {
            return;
        }
        if (cache.containsKey(palavra.getId())) {
            return;
        }
        List<String> cmd = resolverComando();
        if (cmd == null) {
            return;
        }
        try {
            byte[] audio = sintetizar(cmd, palavra.getTexto());
            if (audio != null && audio.length > 0) {
                cache.put(palavra.getId(), audio);
            }
        } catch (Exception e) {
            // Sem áudio o jogo continua pelo caminho antigo. NUNCA registrar o texto
            // da palavra no log: ele é a resposta da rodada.
            LOG.warn("Não foi possível gerar o áudio da palavra {}: {}", palavra.getId(), e.getMessage());
        }
    }

    /** O servidor consegue gerar áudio? Usado pelo diagnóstico e pelos testes. */
    public boolean disponivel() {
        return habilitado && resolverComando() != null;
    }

    // ── síntese ──

    private byte[] sintetizar(List<String> modelo, String texto) throws IOException, InterruptedException {
        Path arquivoTexto = Files.createTempFile("digitado-palavra-", ".txt");
        Path arquivoAudio = Files.createTempFile("digitado-palavra-", ".wav");
        try {
            Files.writeString(arquivoTexto, texto, StandardCharsets.UTF_8);
            List<String> cmd = modelo
                .stream()
                .map(parte -> parte.replace("{arquivoTexto}", arquivoTexto.toString()).replace("{arquivoAudio}", arquivoAudio.toString()))
                .toList();
            // ProcessBuilder com LISTA de argumentos: sem shell, nada é reinterpretado
            Process processo = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!processo.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                throw new IOException("sintetizador não respondeu em " + TIMEOUT_SEGUNDOS + "s");
            }
            if (processo.exitValue() != 0) {
                throw new IOException("sintetizador terminou com código " + processo.exitValue());
            }
            return Files.readAllBytes(arquivoAudio);
        } finally {
            apagar(arquivoTexto);
            apagar(arquivoAudio);
        }
    }

    private static void apagar(Path caminho) {
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException e) {
            LOG.debug("Arquivo temporário de áudio não removido: {}", e.getMessage());
        }
    }

    // ── escolha do sintetizador ──

    private List<String> resolverComando() {
        if (comandoResolvido) {
            return comando;
        }
        synchronized (this) {
            if (comandoResolvido) {
                return comando;
            }
            comando = descobrirComando();
            comandoResolvido = true;
            if (comando == null) {
                LOG.info(
                    "Nenhum sintetizador de voz encontrado: o áudio das palavras continua sendo feito no navegador do aluno, " +
                    "o que faz o texto da palavra ser transmitido para ele. Instale espeak-ng ou configure " +
                    "digitado.audio-palavra.comando para o texto deixar de sair do servidor."
                );
            } else {
                LOG.info("Áudio das palavras gerado no servidor por: {}", comando.get(0));
            }
            return comando;
        }
    }

    private List<String> descobrirComando() {
        if (comandoConfigurado != null && !comandoConfigurado.isBlank()) {
            return List.of(comandoConfigurado.trim().split("\\s+"));
        }
        // espeak-ng: é o que o Dockerfile instala, então é o caminho de produção
        List<String> espeak = List.of("espeak-ng", "-v", "pt-br", "-s", "130", "-f", "{arquivoTexto}", "-w", "{arquivoAudio}");
        if (executavelExiste(espeak.get(0), "--version")) {
            return espeak;
        }
        // Voz do Windows (SAPI), pelo PowerShell: serve ao ambiente de desenvolvimento.
        // O script LÊ o texto do arquivo - nada de palavra embutida em linha de comando.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            List<String> sapi = List.of(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "Add-Type -AssemblyName System.Speech; " +
                "$f = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "try { $f.SelectVoiceByHints('Female', 'Adult', 0, [System.Globalization.CultureInfo]'pt-BR') } catch {}; " +
                "$f.Rate = -1; " +
                "$f.SetOutputToWaveFile('{arquivoAudio}'); " +
                "$f.Speak([IO.File]::ReadAllText('{arquivoTexto}', [Text.Encoding]::UTF8)); " +
                "$f.Dispose()"
            );
            if (executavelExiste("powershell", "-NoProfile", "-Command", "$PSVersionTable.PSVersion.Major")) {
                return sapi;
            }
        }
        return null;
    }

    private boolean executavelExiste(String... comandoTeste) {
        try {
            Process p = new ProcessBuilder(comandoTeste).redirectErrorStream(true).start();
            if (!p.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
