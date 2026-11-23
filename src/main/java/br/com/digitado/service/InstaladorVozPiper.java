package br.com.digitado.service;

import br.com.digitado.domain.enumeration.Idioma;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Instala o Piper e as vozes do treino de idiomas na primeira subida desta versão,
 * e os mantém: nas subidas seguintes só confere que estão lá.
 *
 * Roda numa thread própria depois que o servidor sobe - baixar ~85 MB não pode
 * segurar a aplicação. Enquanto a voz não fica pronta, o treino daquele idioma
 * avisa que a voz está sendo preparada (o texto da palavra nunca é mandado no lugar
 * do som, ver PartidaIndividualService).
 *
 * SEGURANÇA, porque isto baixa um programa que o servidor vai executar:
 * - só HTTPS, só os endereços fixos de VozPiper, com teto de tamanho;
 * - o SHA-256 de cada arquivo é conferido ANTES de ele sair da pasta temporária -
 *   hash diferente, o arquivo é apagado e nada é instalado;
 * - o zip é extraído conferindo que cada entrada fica dentro da pasta de destino
 *   (uma entrada "../../algo" não escreve fora dela).
 *
 * No Docker o Piper e a voz já vêm na imagem (ver Dockerfile) e aqui só se confere.
 * Desligue com digitado.audio-palavra.piper.instalar=false.
 */
@Component
public class InstaladorVozPiper {

    private static final Logger LOG = LoggerFactory.getLogger(InstaladorVozPiper.class);

    private final Path pasta;
    private final boolean instalar;
    private final HttpClient http;

    public InstaladorVozPiper(
        @Value("${digitado.audio-palavra.piper.pasta:${user.home}/.digitado/piper}") String pasta,
        @Value("${digitado.audio-palavra.piper.instalar:true}") boolean instalar
    ) {
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
        this.instalar = instalar;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        if (!instalar) {
            return;
        }
        Thread t = new Thread(this::garantirInstalacao, "instalador-voz-piper");
        t.setDaemon(true);
        t.start();
    }

    /** Instala o que faltar. Seguro de chamar de novo: o que já está lá fica. */
    public synchronized void garantirInstalacao() {
        try {
            if (!Files.isRegularFile(VozPiper.executavel(pasta))) {
                if (VozPiper.windows() && x64()) {
                    instalarPiperWindows();
                } else {
                    LOG.info(
                        "Piper não encontrado em {}. Neste sistema ele não é instalado pelo servidor - no Docker ele vem na imagem.",
                        pasta
                    );
                    return;
                }
            }
            for (Map.Entry<Idioma, VozPiper.Voz> voz : VozPiper.VOZES.entrySet()) {
                Path destino = pasta.resolve("vozes");
                baixarSeFaltar(voz.getValue().configuracao(), destino);
                baixarSeFaltar(voz.getValue().modelo(), destino);
                LOG.info("Voz do {} pronta no servidor (Piper).", voz.getKey().getNome().toLowerCase(Locale.ROOT));
            }
        } catch (Exception e) {
            // Sem a voz o treino daquele idioma avisa e não começa; o resto do jogo segue
            LOG.warn("Não foi possível instalar a voz do Piper em {}: {}", pasta, e.getMessage());
        }
    }

    private static boolean x64() {
        String arq = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arq.equals("amd64") || arq.equals("x86_64");
    }

    private void instalarPiperWindows() throws IOException, InterruptedException {
        Files.createDirectories(pasta);
        Path zip = baixarVerificado(VozPiper.PIPER_WINDOWS, pasta);
        Path temporaria = Files.createTempDirectory(pasta, "piper-instalando-");
        try {
            extrairZip(zip, temporaria);
            Path extraida = temporaria.resolve("piper");
            if (!Files.isRegularFile(extraida.resolve("piper.exe"))) {
                throw new IOException("o pacote do Piper não trouxe o piper.exe");
            }
            Files.move(extraida, pasta.resolve("piper"), StandardCopyOption.ATOMIC_MOVE);
            LOG.info("Piper instalado em {}.", pasta.resolve("piper"));
        } finally {
            Files.deleteIfExists(zip);
            apagarArvore(temporaria);
        }
    }

    private void baixarSeFaltar(VozPiper.Arquivo arquivo, Path destino) throws IOException, InterruptedException {
        Path alvo = destino.resolve(arquivo.nome());
        if (Files.isRegularFile(alvo)) {
            return;
        }
        Files.createDirectories(destino);
        Path baixado = baixarVerificado(arquivo, destino);
        Files.move(baixado, alvo, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Baixa para um arquivo temporário na própria pasta de destino, conferindo o
     * SHA-256 e o tamanho. Só devolve o arquivo se o hash bater.
     */
    Path baixarVerificado(VozPiper.Arquivo arquivo, Path destino) throws IOException, InterruptedException {
        URI uri = URI.create(arquivo.url());
        if (!"https".equals(uri.getScheme())) {
            throw new IOException("download recusado: só HTTPS");
        }
        Path temp = Files.createTempFile(destino, "baixando-", ".parcial");
        try {
            HttpResponse<InputStream> resposta = http.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream()
            );
            if (resposta.statusCode() != 200) {
                resposta.body().close();
                throw new IOException("HTTP " + resposta.statusCode() + " ao baixar " + arquivo.nome());
            }
            try (InputStream in = resposta.body(); OutputStream out = Files.newOutputStream(temp)) {
                copiarConferindo(in, out, arquivo);
            }
            return temp;
        } catch (IOException | InterruptedException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    /** Copia calculando o SHA-256; estoura se passar do teto ou se o hash não bater. */
    static void copiarConferindo(InputStream in, OutputStream out, VozPiper.Arquivo arquivo) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int lidos;
        while ((lidos = in.read(buffer)) != -1) {
            total += lidos;
            if (total > arquivo.tamanhoMaximo()) {
                throw new IOException(arquivo.nome() + " maior que o esperado - download recusado");
            }
            sha.update(buffer, 0, lidos);
            out.write(buffer, 0, lidos);
        }
        String obtido = HexFormat.of().formatHex(sha.digest());
        if (!obtido.equalsIgnoreCase(arquivo.sha256())) {
            throw new IOException(arquivo.nome() + " com SHA-256 diferente do esperado - download recusado");
        }
    }

    /** Extrai o zip dentro de destino; entrada que apontaria para fora é recusada. */
    static void extrairZip(Path zip, Path destino) throws IOException {
        Path base = destino.toAbsolutePath().normalize();
        try (ZipInputStream entrada = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry item;
            while ((item = entrada.getNextEntry()) != null) {
                Path alvo = base.resolve(item.getName()).normalize();
                if (!alvo.startsWith(base)) {
                    throw new IOException("entrada do zip fora da pasta de destino: " + item.getName());
                }
                if (item.isDirectory()) {
                    Files.createDirectories(alvo);
                } else {
                    Files.createDirectories(alvo.getParent());
                    Files.copy(entrada, alvo, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void apagarArvore(Path raiz) {
        if (raiz == null || !Files.exists(raiz)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(raiz)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            LOG.debug("Pasta temporária do Piper não removida: {}", e.getMessage());
        }
    }
}
