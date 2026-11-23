package br.com.digitado.service;

import br.com.digitado.domain.enumeration.Idioma;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * O Piper: sintetizador de voz portátil, usado no servidor para os idiomas do
 * treino que a máquina não fala (o Windows só fala espanhol com a voz de espanhol
 * instalada, e nem sempre dá para instalar).
 *
 * Tudo o que é baixado está aqui, com o endereço OFICIAL e o SHA-256 fixo. O
 * instalador (InstaladorVozPiper) recusa qualquer arquivo cujo hash não bata: um
 * download adulterado no caminho, ou um arquivo trocado na origem, nunca chega a
 * ser executado pelo servidor. Para atualizar uma versão, troque o endereço E o
 * hash juntos.
 *
 * Arrumação da pasta (digitado.audio-palavra.piper.pasta):
 *   piper/piper.exe (ou piper/piper no Linux) + as bibliotecas que vêm junto
 *   vozes/es_ES-davefx-medium.onnx e o .onnx.json
 */
public final class VozPiper {

    private VozPiper() {}

    /** Um arquivo baixado: de onde vem, o hash esperado e o teto de tamanho. */
    public record Arquivo(String url, String sha256, long tamanhoMaximo, String nome) {}

    /** Piper 2023.11.14-2 para Windows x64 (zip com o executável e as DLLs). */
    public static final Arquivo PIPER_WINDOWS = new Arquivo(
        "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip",
        "f3c58906402b24f3a96d92145f58acba6d86c9b5db896d207f78dc80811efcea",
        40L * 1024 * 1024,
        "piper_windows_amd64.zip"
    );

    /**
     * Piper para Linux x64 - instalado pelo Dockerfile, não pelo servidor (o Java não
     * abre .tar.gz sem dependência nova). Fica aqui para o hash morar num lugar só.
     */
    public static final Arquivo PIPER_LINUX = new Arquivo(
        "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz",
        "a50cb45f355b7af1f6d758c1b360717877ba0a398cc8cbe6d2a7a3a26e225992",
        40L * 1024 * 1024,
        "piper_linux_x86_64.tar.gz"
    );

    /** Voz de cada idioma: o modelo (.onnx) e a configuração dele (.onnx.json). */
    public record Voz(Arquivo modelo, Arquivo configuracao) {}

    private static final String BASE_VOZES = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/";

    public static final Map<Idioma, Voz> VOZES = Map.of(
        Idioma.ES,
        new Voz(
            new Arquivo(
                BASE_VOZES + "es/es_ES/davefx/medium/es_ES-davefx-medium.onnx",
                "6658b03b1a6c316ee4c265a9896abc1393353c2d9e1bca7d66c2c442e222a917",
                100L * 1024 * 1024,
                "es_ES-davefx-medium.onnx"
            ),
            new Arquivo(
                BASE_VOZES + "es/es_ES/davefx/medium/es_ES-davefx-medium.onnx.json",
                "0e0dda87c732f6f38771ff274a6380d9252f327dca77aa2963d5fbdf9ec54842",
                1024L * 1024,
                "es_ES-davefx-medium.onnx.json"
            )
        )
    );

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** O executável do Piper dentro da pasta (exista ou não). */
    public static Path executavel(Path pasta) {
        return pasta.resolve("piper").resolve(windows() ? "piper.exe" : "piper");
    }

    /** O modelo de voz do idioma dentro da pasta, se o idioma tiver voz no catálogo. */
    public static Optional<Path> modelo(Path pasta, Idioma idioma) {
        Voz voz = VOZES.get(idioma);
        return voz == null ? Optional.empty() : Optional.of(pasta.resolve("vozes").resolve(voz.modelo().nome()));
    }

    /** Piper e a voz do idioma já estão na pasta? */
    public static boolean pronto(Path pasta, Idioma idioma) {
        if (pasta == null || !Files.isRegularFile(executavel(pasta))) {
            return false;
        }
        Optional<Path> modelo = modelo(pasta, idioma);
        return (
            modelo.isPresent() &&
            Files.isRegularFile(modelo.orElseThrow()) &&
            Files.isRegularFile(modelo.orElseThrow().resolveSibling(VOZES.get(idioma).configuracao().nome()))
        );
    }
}
