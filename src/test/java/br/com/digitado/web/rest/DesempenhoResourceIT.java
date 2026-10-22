package br.com.digitado.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.HistoricoResposta;
import br.com.digitado.domain.Palavra;
import br.com.digitado.repository.HistoricoRespostaRepository;
import br.com.digitado.repository.PalavraRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Painel "Desempenho geral" do admin: a contagem de "não respondeu" e o filtro
 * por tipo de erro.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class DesempenhoResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HistoricoRespostaRepository historicoRespostaRepository;

    @Autowired
    private PalavraRepository palavraRepository;

    private Palavra palavra;

    @BeforeEach
    void historico() {
        historicoRespostaRepository.deleteAll();
        palavra = palavraRepository.saveAndFlush(new Palavra().texto("café-it").ativa(true));
        registrar("aluno-a", true, null);
        registrar("aluno-a", false, "ACENTUACAO");
        registrar("aluno-b", false, "ACENTUACAO");
        registrar("aluno-b", false, "NAO_RESPONDEU");
        historicoRespostaRepository.flush();
    }

    private void registrar(String login, boolean correta, String tipoErro) {
        HistoricoResposta h = new HistoricoResposta();
        h.setLogin(login);
        h.setPalavraId(palavra.getId());
        h.setCorreta(correta);
        h.setTipoErro(tipoErro);
        h.setDificuldade("MEDIO");
        h.setOrigem(HistoricoResposta.Origem.PARTIDA);
        h.setDataResposta(Instant.now());
        historicoRespostaRepository.save(h);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void contaNaoRespondeuAParte() throws Exception {
        mockMvc
            .perform(get("/api/admin/desempenho"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRespostas").value(4))
            .andExpect(jsonPath("$.totalAcertos").value(1))
            .andExpect(jsonPath("$.totalErros").value(3))
            .andExpect(jsonPath("$.totalNaoRespondeu").value(1))
            .andExpect(jsonPath("$.filtro").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void filtraPeloTipoDeErro() throws Exception {
        mockMvc
            .perform(get("/api/admin/desempenho").param("tipoErro", "ACENTUACAO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filtro.tipo").value("ACENTUACAO"))
            .andExpect(jsonPath("$.filtro.ocorrencias").value(2))
            .andExpect(jsonPath("$.filtro.alunosAfetados").value(2))
            .andExpect(jsonPath("$.filtro.palavrasAfetadas").value(1))
            // 2 dos 3 erros; 2 das 4 respostas
            .andExpect(jsonPath("$.filtro.percentualDosErros").value(67))
            .andExpect(jsonPath("$.filtro.percentualDasRespostas").value(50.0))
            .andExpect(jsonPath("$.filtro.palavras[0].texto").value("café-it"))
            .andExpect(jsonPath("$.filtro.palavras[0].ocorrencias").value(2));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void tipoDeErroDesconhecidoEhRecusado() throws Exception {
        mockMvc.perform(get("/api/admin/desempenho").param("tipoErro", "DROP TABLE")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void alunoNaoVeOPainelDoAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/desempenho").param("tipoErro", "ACENTUACAO")).andExpect(status().isForbidden());
    }
}
