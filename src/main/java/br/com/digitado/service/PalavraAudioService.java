package br.com.digitado.service;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Idioma;
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

    /**
     * Comando PRÓPRIO de um idioma, opcional (digitado.audio-palavra.comando-en,
     * comando-es). Vence o comando geral para as palavras daquele idioma.
     *
     * Existe para o treino de idiomas: o Windows só fala espanhol com a voz de
     * espanhol instalada, e um sintetizador portátil (Piper, por exemplo) precisa de
     * um arquivo de voz por idioma. Mesmos marcadores e mesma execução sem shell do
     * comando geral.
     */
    private final Map<Idioma, List<String>> comandosPorIdioma = new java.util.EnumMap<>(Idioma.class);

    /**
     * Pasta do Piper (ver VozPiper e InstaladorVozPiper). Quando o Piper e a voz de
     * um idioma estão nela, é ele que fala as palavras daquele idioma - instalado
     * sozinho na primeira subida, sem depender das vozes do Windows.
     */
    private final Path pastaPiper;

    // Culturas que o sintetizador GERAL fala (null = qualquer uma, ex.: espeak-ng).
    // Só a SAPI do Windows tem lista fechada: as vozes instaladas na máquina.
    private volatile java.util.Set<String> culturasDoGeral;

    public PalavraAudioService(String comandoConfigurado, boolean habilitado) {
        this(comandoConfigurado, habilitado, "", "", null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PalavraAudioService(
        @Value("${digitado.audio-palavra.comando:}") String comandoConfigurado,
        @Value("${digitado.audio-palavra.habilitado:true}") boolean habilitado,
        @Value("${digitado.audio-palavra.comando-en:}") String comandoIngles,
        @Value("${digitado.audio-palavra.comando-es:}") String comandoEspanhol,
        @Value("${digitado.audio-palavra.piper.pasta:${user.home}/.digitado/piper}") String pastaPiper
    ) {
        this.comandoConfigurado = comandoConfigurado;
        this.habilitado = habilitado;
        this.pastaPiper = pastaPiper == null || pastaPiper.isBlank() ? null : Path.of(pastaPiper).toAbsolutePath().normalize();
        if (comandoIngles != null && !comandoIngles.isBlank()) {
            comandosPorIdioma.put(Idioma.EN, List.of(comandoIngles.trim().split("\\s+")));
        }
        if (comandoEspanhol != null && !comandoEspanhol.isBlank()) {
            comandosPorIdioma.put(Idioma.ES, List.of(comandoEspanhol.trim().split("\\s+")));
        }
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
        Idioma idioma = Idioma.daPalavra(palavra.getIdioma());
        List<String> cmd = comandoPara(idioma);
        if (cmd == null) {
            return;
        }
        try {
            byte[] audio = sintetizar(cmd, palavra.getTexto(), idioma);
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

    /**
     * O servidor tem voz para este idioma? O treino de idiomas só começa com ela:
     * ali o texto da palavra NUNCA vai para o aparelho do aluno, só o som.
     */
    public boolean vozDisponivel(Idioma idioma) {
        if (!habilitado || idioma == null) {
            return false;
        }
        if (comandosPorIdioma.containsKey(idioma) || piperPronto(idioma)) {
            return true;
        }
        if (resolverComando() == null) {
            return false;
        }
        java.util.Set<String> culturas = culturasDoGeral;
        return culturas == null || culturas.contains(idioma.getTag().toLowerCase(Locale.ROOT));
    }

    /**
     * Quem fala as palavras deste idioma: o comando próprio do idioma, se
     * configurado; o Piper, se ele e a voz do idioma estão instalados; e, por fim,
     * o sintetizador geral (espeak-ng ou a voz do Windows).
     */
    List<String> comandoPara(Idioma idioma) {
        if (comandosPorIdioma.containsKey(idioma)) {
            return comandosPorIdioma.get(idioma);
        }
        if (piperPronto(idioma)) {
            // Sem {arquivoTexto}: o Piper lê a palavra da entrada padrão (ver sintetizar)
            return List.of(
                VozPiper.executavel(pastaPiper).toString(),
                "--model",
                VozPiper.modelo(pastaPiper, idioma).orElseThrow().toString(),
                "--output_file",
                "{arquivoAudio}",
                "-q"
            );
        }
        return resolverComando();
    }

    private boolean piperPronto(Idioma idioma) {
        return pastaPiper != null && VozPiper.pronto(pastaPiper, idioma);
    }

    // ── síntese ──

    /**
     * Marcadores substituídos no comando: além dos arquivos, a voz do idioma da
     * palavra ({voz}: pt-br, en-us, es - nomes do espeak-ng) e a cultura ({cultura}:
     * pt-BR, en-US, es-ES - o que a SAPI do Windows usa). Os valores vêm do enum
     * Idioma, nunca do pedido de quem joga.
     */
    private byte[] sintetizar(List<String> modelo, String texto, Idioma idioma) throws IOException, InterruptedException {
        Path arquivoTexto = Files.createTempFile("digitado-palavra-", ".txt");
        Path arquivoAudio = Files.createTempFile("digitado-palavra-", ".wav");
        try {
            Files.writeString(arquivoTexto, texto, StandardCharsets.UTF_8);
            List<String> cmd = modelo
                .stream()
                .map(parte ->
                    parte
                        .replace("{arquivoTexto}", arquivoTexto.toString())
                        .replace("{arquivoAudio}", arquivoAudio.toString())
                        .replace("{voz}", idioma.getVozEspeak())
                        .replace("{cultura}", idioma.getTag())
                )
                .toList();
            // ProcessBuilder com LISTA de argumentos: sem shell, nada é reinterpretado.
            // A saída do programa é descartada (não fica um buffer cheio travando ele).
            ProcessBuilder construtor = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            // Comando que não lê o arquivo de texto (Piper) recebe a palavra pela
            // entrada padrão - do mesmo arquivo, sem passar por linha de comando
            if (modelo.stream().noneMatch(parte -> parte.contains("{arquivoTexto}"))) {
                construtor.redirectInput(arquivoTexto.toFile());
            }
            Process processo = construtor.start();
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
        List<String> espeak = List.of("espeak-ng", "-v", "{voz}", "-s", "130", "-f", "{arquivoTexto}", "-w", "{arquivoAudio}");
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
                // Voz instalada da cultura da palavra. Sem nenhuma (Windows sem o pacote de
                // inglês, por exemplo), sai com erro: melhor a voz do navegador do aluno do
                // que uma palavra em inglês lida com pronúncia de português
                "$v = $f.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq '{cultura}' } | Select-Object -First 1; " +
                "if (-not $v) { exit 3 }; " +
                "$f.SelectVoice($v.VoiceInfo.Name); " +
                "$f.Rate = -1; " +
                "$f.SetOutputToWaveFile('{arquivoAudio}'); " +
                "$f.Speak([IO.File]::ReadAllText('{arquivoTexto}', [Text.Encoding]::UTF8)); " +
                "$f.Dispose()"
            );
            if (executavelExiste("powershell", "-NoProfile", "-Command", "$PSVersionTable.PSVersion.Major")) {
                culturasDoGeral = culturasSapi();
                return sapi;
            }
        }
        return null;
    }

    // As culturas das vozes do Windows instaladas (ex.: pt-br, en-us), em minúsculas
    private java.util.Set<String> culturasSapi() {
        try {
            Process p = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | " +
                "Where-Object { $_.Enabled } | ForEach-Object { $_.VoiceInfo.Culture.Name }"
            )
                .redirectErrorStream(true)
                .start();
            String saida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            java.util.Set<String> culturas = new java.util.HashSet<>();
            for (String linha : saida.split("\\R")) {
                if (!linha.isBlank()) {
                    culturas.add(linha.trim().toLowerCase(Locale.ROOT));
                }
            }
            return culturas;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return java.util.Set.of();
        }
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
