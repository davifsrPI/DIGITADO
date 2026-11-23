package br.com.digitado.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Chave que assina os tokens de login no perfil DEV, gerada nesta máquina.
 *
 * O perfil dev é o que roda na sala de aula: o setup-ambiente.ps1 sobe o
 * "./mvnw" e libera a porta 8080 na rede local. Ele assinava os tokens com a
 * chave padrão do JHipster, escrita no application-dev.yml de um repositório
 * PÚBLICO. Com ela qualquer pessoa na rede monta, no próprio computador, um token
 * com "auth: ROLE_ADMIN" que o servidor aceita como legítimo: vira admin sem senha
 * nenhuma, lê e apaga o banco inteiro.
 *
 * Agora, no perfil dev, quando a chave não foi configurada (ou é a pública), uma
 * chave aleatória de 512 bits é gerada na primeira subida e guardada FORA do
 * projeto, em ~/.digitado/jwt-dev.key - não vai para o git nem para o pendrive.
 * As subidas seguintes reutilizam a mesma chave, então reiniciar o servidor não
 * desloga ninguém. A variável JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET,
 * quando definida, continua valendo.
 *
 * O perfil prod não passa por aqui: lá a chave vem obrigatoriamente do ambiente e
 * a aplicação nem sobe sem ela. Os testes usam o próprio application.yml.
 */
public class ChaveJwtLocalEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPRIEDADE = "jhipster.security.authentication.jwt.base64-secret";

    /**
     * A chave padrão do JHipster que esteve no application-dev.yml (e no .yo-rc.json)
     * deste repositório público. Quem a tiver configurado ainda é tratado como se não
     * tivesse chave nenhuma.
     */
    static final String CHAVE_PUBLICA =
        "MTg0YTZkOTAwYzEzZGI4ZmE3YjQxMTZiZDk1NWMxOWExM2E4OGU3ZDVmZmRlZGNlZTlmNWQyYzIzMTlkNDYwMzYwM2RhN2RlOGIyN2M1OTVlNjk2NWJiMzI4YzdlNzU2NDgwZTk2ZjgyOWY3ZjA4MGZlZjEyZGNiMGE3OWI3N2U=";

    private static final int BYTES_DA_CHAVE = 64;

    private final Log log;

    public ChaveJwtLocalEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(ChaveJwtLocalEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            return;
        }
        String atual = environment.getProperty(PROPRIEDADE);
        if (atual != null && !atual.isBlank() && !CHAVE_PUBLICA.equals(atual.trim())) {
            return; // chave própria configurada: vale ela
        }
        Path arquivo = arquivoDaChave();
        try {
            String chave = lerOuGerar(arquivo);
            environment.getPropertySources().addFirst(new MapPropertySource("chave-jwt-local", Map.of(PROPRIEDADE, chave)));
            log.info("Tokens de login assinados com a chave local desta máquina (" + arquivo + ").");
        } catch (IOException e) {
            // Sem conseguir gravar a chave, gera uma só para esta execução: reiniciar
            // desloga todo mundo, mas a chave pública nunca é usada
            String temporaria = gerar();
            environment.getPropertySources().addFirst(new MapPropertySource("chave-jwt-local", Map.of(PROPRIEDADE, temporaria)));
            log.warn(
                "Não foi possível guardar a chave JWT em " + arquivo + " (" + e.getMessage() + "): usando uma chave só desta execução."
            );
        }
    }

    static Path arquivoDaChave() {
        String personalizado = System.getenv("DIGITADO_JWT_DEV_ARQUIVO");
        if (personalizado != null && !personalizado.isBlank()) {
            return Path.of(personalizado);
        }
        return Path.of(System.getProperty("user.home"), ".digitado", "jwt-dev.key");
    }

    static String lerOuGerar(Path arquivo) throws IOException {
        if (Files.isRegularFile(arquivo)) {
            String lida = Files.readString(arquivo, StandardCharsets.US_ASCII).trim();
            if (chaveValida(lida)) {
                return lida;
            }
        }
        String nova = gerar();
        Path pasta = arquivo.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
        Files.writeString(arquivo, nova, StandardCharsets.US_ASCII);
        restringirAoDono(arquivo);
        return nova;
    }

    static String gerar() {
        byte[] bytes = new byte[BYTES_DA_CHAVE];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Base64 válido, com pelo menos 256 bits, e que não seja a chave pública
    static boolean chaveValida(String chave) {
        if (chave == null || chave.isBlank() || CHAVE_PUBLICA.equals(chave)) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(chave).length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // No Linux/macOS só o dono lê o arquivo; no Windows a pasta do usuário já é dele
    private static void restringirAoDono(Path arquivo) {
        try {
            Files.setPosixFilePermissions(arquivo, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // sistema de arquivos sem permissões POSIX (Windows): segue
        }
    }

    // Depois da leitura dos application*.yml, que é quando os perfis ativos são conhecidos
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
