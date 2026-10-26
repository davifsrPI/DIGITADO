package br.com.digitado.management;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
 * "Requisições por API" de Administração > Métricas: só ADMIN, e um erro de verdade
 * (a requisição passa pelos filtros do Spring) aparece na lista de erros recentes
 * com a exceção que o causou.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = { "management.endpoints.web.base-path=/management", "management.endpoints.web.exposure.include=desempenho,requisicoes" }
)
class MonitorRequisicoesEndpointIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminVeAsRotasEOErroComAExcecao() throws Exception {
        // 400: o tipo de erro não existe (BadRequestAlertException no DesempenhoResource)
        mockMvc.perform(get("/api/admin/desempenho").param("tipoErro", "NAO_EXISTE")).andExpect(status().isBadRequest());

        mockMvc
            .perform(get(MonitorRequisicoesEndpoint.URI_DO_PAINEL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instante").isNumber())
            .andExpect(jsonPath("$.rotas").isArray())
            .andExpect(jsonPath("$.errosRegistrados").isNumber())
            .andExpect(jsonPath("$.errosRecentes[?(@.caminho == '/api/admin/desempenho')].status").value(hasItem(400)))
            .andExpect(
                jsonPath("$.errosRecentes[?(@.caminho == '/api/admin/desempenho')].excecao").value(
                    hasItem(containsString("BadRequestAlertException"))
                )
            )
            .andExpect(
                jsonPath("$.errosRecentes[?(@.caminho == '/api/admin/desempenho')].mensagem").value(hasItem(containsString("NAO_EXISTE")))
            );
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void alunoNaoVe() throws Exception {
        mockMvc.perform(get(MonitorRequisicoesEndpoint.URI_DO_PAINEL)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonimoNaoVe() throws Exception {
        mockMvc.perform(get(MonitorRequisicoesEndpoint.URI_DO_PAINEL)).andExpect(status().isUnauthorized());
    }
}
