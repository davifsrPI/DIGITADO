package br.com.digitado.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O painel "Desempenho" de Administração > Métricas: exposto no actuator e só para
 * ADMIN - mostra versão do sistema e da JVM, PID e o uso da máquina.
 *
 * O application.yml de teste não publica endpoint nenhum do actuator; aqui o de
 * desempenho é publicado no mesmo caminho da aplicação de verdade (/management).
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = { "management.endpoints.web.base-path=/management", "management.endpoints.web.exposure.include=desempenho" }
)
class MonitorDesempenhoEndpointIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminLeOPainel() throws Exception {
        mockMvc
            .perform(get(MonitorDesempenhoEndpoint.URI_DO_PAINEL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instante").isNumber())
            .andExpect(jsonPath("$.cpu.processadores").isNumber())
            .andExpect(jsonPath("$.memoria.total").isNumber())
            .andExpect(jsonPath("$.memoriaJvm.heapUsada").isNumber())
            .andExpect(jsonPath("$.memoriaJvm.areas").isArray())
            .andExpect(jsonPath("$.coletorDeLixo.coletores").isArray())
            .andExpect(jsonPath("$.threads.porEstado.RUNNABLE").isNumber())
            .andExpect(jsonPath("$.http.requisicoes").isNumber())
            .andExpect(jsonPath("$.sistema.pid").isNumber());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void alunoNaoLe() throws Exception {
        mockMvc.perform(get(MonitorDesempenhoEndpoint.URI_DO_PAINEL)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonimoNaoLe() throws Exception {
        mockMvc.perform(get(MonitorDesempenhoEndpoint.URI_DO_PAINEL)).andExpect(status().isUnauthorized());
    }
}
