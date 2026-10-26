package br.com.digitado.web.rest;

import static org.hamcrest.Matchers.contains;
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
        // um erro já com a classificação detalhada e um gravado antes dela (só o tipo)
        registrar(palavra, "aluno-a", false, "ACENTUACAO", "ACENTO_FALTANDO", "é→e");
        registrar("aluno-b", false, "ACENTUACAO");
        registrar("aluno-b", false, "NAO_RESPONDEU");
        historicoRespostaRepository.flush();
    }

    private void registrar(String login, boolean correta, String tipoErro) {
        registrar(palavra, login, correta, tipoErro, null, null);
    }

    private void registrar(Palavra p, String login, boolean correta, String tipoErro, String subtipo, String detalhe) {
        HistoricoResposta h = new HistoricoResposta();
        h.setLogin(login);
        h.setPalavraId(p.getId());
        h.setCorreta(correta);
        h.setTipoErro(tipoErro);
        h.setSubtipoErro(subtipo);
        h.setDetalheErro(detalhe);
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
    void abreOTipoNosSubtiposEContaOsRegistrosAntigosAParte() throws Exception {
        mockMvc
            .perform(get("/api/admin/desempenho").param("tipoErro", "ACENTUACAO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filtro.subtipos.length()").value(2))
            .andExpect(jsonPath("$.filtro.subtipos[?(@.subtipo == 'ACENTO_FALTANDO')].percentualDoTipo").value(contains(50)))
            // o registro antigo aparece como subtipo nulo, não some da conta
            .andExpect(jsonPath("$.filtro.subtipos[?(@.subtipo == null)].total").value(contains(1)))
            .andExpect(jsonPath("$.filtro.confusoes[0].detalhe").value("é→e"))
            .andExpect(jsonPath("$.filtro.confusoes[0].exemplos[0]").value("café-it"));
    }

    /**
     * Os trechos mais trocados e o erro mais comum de cada palavra. A palavra tem 7
     * respostas (o ranking só considera palavras com 5 ou mais) e 6 erros: 3 vezes
     * "ss→s", 1 "ss→ç", 1 "ss→c" e uma letra faltando.
     */
    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void mostraOQueATurmaMaisTrocaEOErroMaisComumDaPalavra() throws Exception {
        Palavra passaro = palavraRepository.saveAndFlush(new Palavra().texto("pássaro-it").ativa(true));
        registrar(passaro, "aluno-a", true, null, null, null);
        registrar(passaro, "aluno-a", false, "ERRO_FONETICO", "SOM_S_Z", "ss→s");
        registrar(passaro, "aluno-b", false, "ERRO_FONETICO", "SOM_S_Z", "ss→s");
        registrar(passaro, "aluno-c", false, "ERRO_FONETICO", "SOM_S_Z", "ss→s");
        registrar(passaro, "aluno-a", false, "ERRO_FONETICO", "SOM_S_Z", "ss→ç");
        registrar(passaro, "aluno-b", false, "ERRO_FONETICO", "SOM_S_Z", "ss→c");
        registrar(passaro, "aluno-c", false, "LETRA_FALTANDO", "FALTOU_VOGAL", "a→");
        historicoRespostaRepository.flush();

        String porPalavra = "$.erroMaisComumPorPalavra['" + passaro.getId() + "']";
        mockMvc
            .perform(get("/api/admin/desempenho"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confusoes[0].detalhe").value("ss→s"))
            .andExpect(jsonPath("$.confusoes[0].ocorrencias").value(3))
            .andExpect(jsonPath("$.confusoes[0].alunos").value(3))
            .andExpect(jsonPath("$.confusoes[0].exemplos[0]").value("pássaro-it"))
            // comparação binária: "ç" e "c" são trocas diferentes, não a mesma sem acento
            .andExpect(jsonPath("$.confusoes[?(@.detalhe == 'ss→ç')].ocorrencias").value(contains(1)))
            .andExpect(jsonPath("$.confusoes[?(@.detalhe == 'ss→c')].ocorrencias").value(contains(1)))
            .andExpect(jsonPath("$.subtiposErro[?(@.subtipo == 'SOM_S_Z')].total").value(contains(5)))
            .andExpect(jsonPath(porPalavra + ".tipo").value("ERRO_FONETICO"))
            .andExpect(jsonPath(porPalavra + ".detalhe").value("ss→s"))
            .andExpect(jsonPath(porPalavra + ".percentualDosErros").value(50));

        mockMvc
            .perform(get("/api/admin/desempenho").param("tipoErro", "LETRA_FALTANDO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath(porPalavra.replace("$.", "$.filtro.") + ".detalhe").value("a→"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void aceitaOsTiposNovos() throws Exception {
        mockMvc.perform(get("/api/admin/desempenho").param("tipoErro", "LETRAS_INVERTIDAS")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/desempenho").param("tipoErro", "OUTRO")).andExpect(status().isOk());
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
