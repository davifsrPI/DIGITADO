package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** O servidor executa o que este instalador baixa: hash, tamanho e zip são conferidos. */
class InstaladorVozPiperTest {

    private static String sha256(byte[] dados) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
    }

    @Test
    void aceitaOArquivoComOHashEsperado() throws Exception {
        byte[] dados = "voz".getBytes(StandardCharsets.UTF_8);
        VozPiper.Arquivo arquivo = new VozPiper.Arquivo("https://x", sha256(dados), 1024, "voz.onnx");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InstaladorVozPiper.copiarConferindo(new ByteArrayInputStream(dados), out, arquivo);
        assertThat(out.toByteArray()).isEqualTo(dados);
    }

    @Test
    void recusaArquivoAdulterado() throws Exception {
        VozPiper.Arquivo arquivo = new VozPiper.Arquivo(
            "https://x",
            sha256("original".getBytes(StandardCharsets.UTF_8)),
            1024,
            "piper.zip"
        );
        assertThatThrownBy(() ->
            InstaladorVozPiper.copiarConferindo(
                new ByteArrayInputStream("trocado".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(),
                arquivo
            )
        )
            .isInstanceOf(IOException.class)
            .hasMessageContaining("SHA-256");
    }

    @Test
    void recusaArquivoMaiorQueOTeto() {
        VozPiper.Arquivo arquivo = new VozPiper.Arquivo("https://x", "00", 4, "grande.bin");
        assertThatThrownBy(() ->
            InstaladorVozPiper.copiarConferindo(new ByteArrayInputStream(new byte[10]), new ByteArrayOutputStream(), arquivo)
        )
            .isInstanceOf(IOException.class)
            .hasMessageContaining("maior");
    }

    @Test
    void zipNaoEscreveForaDaPasta(@TempDir Path pasta) throws Exception {
        Path zip = pasta.resolve("malicioso.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../../fora.txt"));
            out.write(1);
            out.closeEntry();
        }
        Path destino = Files.createDirectories(pasta.resolve("destino"));
        assertThatThrownBy(() -> InstaladorVozPiper.extrairZip(zip, destino)).isInstanceOf(IOException.class);
        assertThat(pasta.getParent().resolve("fora.txt")).doesNotExist();
    }

    @Test
    void zipNormalEhExtraido(@TempDir Path pasta) throws Exception {
        Path zip = pasta.resolve("piper.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("piper/piper.exe"));
            out.write(new byte[] { 1, 2, 3 });
            out.closeEntry();
        }
        Path destino = Files.createDirectories(pasta.resolve("destino"));
        InstaladorVozPiper.extrairZip(zip, destino);
        assertThat(destino.resolve("piper/piper.exe")).exists();
    }

    @Test
    void oCatalogoSoBaixaPorHttpsEComHash() {
        for (VozPiper.Arquivo a : new VozPiper.Arquivo[] {
            VozPiper.PIPER_WINDOWS,
            VozPiper.PIPER_LINUX,
            VozPiper.VOZES.get(br.com.digitado.domain.enumeration.Idioma.ES).modelo(),
            VozPiper.VOZES.get(br.com.digitado.domain.enumeration.Idioma.ES).configuracao(),
        }) {
            assertThat(a.url()).startsWith("https://");
            assertThat(a.sha256()).hasSize(64);
        }
    }
}
