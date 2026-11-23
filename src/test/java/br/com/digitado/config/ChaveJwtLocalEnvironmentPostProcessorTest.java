package br.com.digitado.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * A chave JWT do perfil dev nunca pode ser a pública do repositório: com ela
 * qualquer um forja um token de admin.
 */
class ChaveJwtLocalEnvironmentPostProcessorTest {

    private final ChaveJwtLocalEnvironmentPostProcessor processador = new ChaveJwtLocalEnvironmentPostProcessor(new DeferredLogs());

    @Test
    void geraUmaChaveForteEAReaproveitaNaSubidaSeguinte(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("sub/jwt-dev.key");
        String primeira = ChaveJwtLocalEnvironmentPostProcessor.lerOuGerar(arquivo);
        assertThat(Base64.getDecoder().decode(primeira)).hasSize(64);
        assertThat(primeira).isNotEqualTo(ChaveJwtLocalEnvironmentPostProcessor.CHAVE_PUBLICA);
        // Reiniciar o servidor não troca a chave (ninguém é deslogado)
        assertThat(ChaveJwtLocalEnvironmentPostProcessor.lerOuGerar(arquivo)).isEqualTo(primeira);
    }

    @Test
    void arquivoComAChavePublicaOuLixoEhSubstituido(@TempDir Path pasta) throws Exception {
        Path arquivo = pasta.resolve("jwt-dev.key");
        Files.writeString(arquivo, ChaveJwtLocalEnvironmentPostProcessor.CHAVE_PUBLICA);
        assertThat(ChaveJwtLocalEnvironmentPostProcessor.lerOuGerar(arquivo)).isNotEqualTo(
            ChaveJwtLocalEnvironmentPostProcessor.CHAVE_PUBLICA
        );
        Files.writeString(arquivo, "curta");
        assertThat(ChaveJwtLocalEnvironmentPostProcessor.chaveValida(Files.readString(arquivo))).isFalse();
    }

    @Test
    void foraDoPerfilDevNaoMexeEmNada() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("prod");
        processador.postProcessEnvironment(ambiente, null);
        assertThat(ambiente.getProperty(ChaveJwtLocalEnvironmentPostProcessor.PROPRIEDADE)).isNull();
    }

    @Test
    void chaveConfiguradaDeVerdadeContinuaValendo() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("dev");
        String propria = ChaveJwtLocalEnvironmentPostProcessor.gerar();
        ambiente
            .getPropertySources()
            .addFirst(new MapPropertySource("teste", Map.of(ChaveJwtLocalEnvironmentPostProcessor.PROPRIEDADE, propria)));
        processador.postProcessEnvironment(ambiente, null);
        assertThat(ambiente.getProperty(ChaveJwtLocalEnvironmentPostProcessor.PROPRIEDADE)).isEqualTo(propria);
    }
}
