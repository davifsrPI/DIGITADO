package br.com.digitado.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * As tabelas do banco (telas de Entidades e as de consulta do jogo) são SÓ do admin:
 * leitura e escrita. Antes só a escrita era barrada e qualquer conta logada lia
 * pela API as respostas, os rankings e os cadastros de todo mundo.
 *
 * Também confere o que o jogo precisa continuar lendo (buscar/sortear palavras,
 * as próprias conquistas) e a raspagem do Prometheus.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class SegurancaEntidadesIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @ParameterizedTest(name = "aluno não lê {0}")
    @ValueSource(
        strings = {
            "/api/atividades",
            "/api/respostas",
            "/api/rankings",
            "/api/lista-palavras",
            "/api/erro-ortograficos",
            "/api/usuarios",
            "/api/usuario-conquistas",
            "/api/conquistas",
            "/api/palavras",
            "/api/palavras/1",
            "/api/users",
            "/api/authorities",
            "/api/admin/tabelas",
            "/api/admin/tabelas/historico-respostas",
        }
    )
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void alunoNaoLeTabelasDoBanco(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    @DisplayName("o que o jogo usa continua aberto ao aluno")
    void jogoContinuaLendoOQuePrecisa() throws Exception {
        mockMvc.perform(get("/api/palavras/buscar").param("texto", "casa")).andExpect(status().isOk());
        mockMvc.perform(get("/api/palavras/sortear").param("dificuldade", "FACIL").param("quantidade", "1")).andExpect(status().isOk());
        mockMvc.perform(get("/api/conquistas/minhas")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminVeAsTabelasDoJogo() throws Exception {
        mockMvc
            .perform(get("/api/admin/tabelas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[0].nome").value("historico-respostas"));
        for (String tabela : new String[] {
            "historico-respostas",
            "estatisticas-partida",
            "participantes-sala",
            "palavra-do-dia-tentativas",
            "partidas-individuais",
        }) {
            mockMvc
                .perform(get("/api/admin/tabelas/" + tabela).param("busca", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colunas").isArray())
                .andExpect(jsonPath("$.linhas").isArray())
                .andExpect(jsonPath("$.pagina").value(0));
        }
        mockMvc.perform(get("/api/admin/tabelas/jhi_user")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Prometheus da própria máquina passa pela segurança sem login")
    void prometheusLocalPassaPelaSeguranca() throws Exception {
        // O perfil de teste não publica o endpoint (404), mas o que importa aqui é
        // a segurança deixar passar: sem a regra nova, o anônimo levava 401
        int status = mockMvc
            .perform(
                get("/management/prometheus").with(r -> {
                    r.setRemoteAddr("127.0.0.1");
                    return r;
                })
            )
            .andReturn()
            .getResponse()
            .getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("as métricas de memória da máquina estão registradas")
    void metricasDeMemoriaDaMaquina() {
        assertThat(meterRegistry.find("digitado.maquina.memoria.uso").gauge()).isNotNull();
        double uso = meterRegistry.get("digitado.maquina.memoria.uso").gauge().value();
        assertThat(uso).isBetween(0.0, 1.0);
        assertThat(meterRegistry.get("digitado.maquina.memoria.fisica").gauge().value()).isPositive();
        assertThat(meterRegistry.find("digitado.maquina.memoria.livre").gauge()).isNotNull();
        assertThat(meterRegistry.find("digitado.maquina.swap.tamanho").gauge()).isNotNull();
    }

    @Test
    @WithAnonymousUser
    void metricasNaoSaemParaARedeSemToken() throws Exception {
        mockMvc
            .perform(
                get("/management/prometheus")
                    .header("X-Token-Metricas", "chute")
                    .with(r -> {
                        r.setRemoteAddr("192.168.0.50");
                        return r;
                    })
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("a checagem pública 'este e-mail existe?' do cadastro não existe mais")
    void checagemPublicaDeEmailFoiRemovida() throws Exception {
        mockMvc.perform(get("/api/public/verificar-email").param("email", "admin@localhost")).andExpect(status().isNotFound());
    }
}
