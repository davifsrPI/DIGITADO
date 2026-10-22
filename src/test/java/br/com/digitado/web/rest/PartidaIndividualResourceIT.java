package br.com.digitado.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.HistoricoResposta;
import br.com.digitado.repository.HistoricoRespostaRepository;
import br.com.digitado.repository.PartidaIndividualRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * O modo individual de ponta a ponta, com o banco de verdade: começar, responder,
 * ser recusado fora de ordem, passar de palavra e encerrar - e o que fica gravado
 * (histórico com origem INDIVIDUAL e a partida encerrada).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PartidaIndividualResourceIT {

    private static final String LOGIN = "individual-it";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HistoricoRespostaRepository historicoRespostaRepository;

    @Autowired
    private PartidaIndividualRepository partidaIndividualRepository;

    @Test
    @WithMockUser(value = LOGIN, authorities = "ROLE_USER")
    void partidaCompleta() throws Exception {
        mockMvc
            .perform(get("/api/individual/recordes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recorde").value(0))
            .andExpect(jsonPath("$.emAndamento").value(false));

        mockMvc
            .perform(post("/api/individual/iniciar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rodada").value(1))
            .andExpect(jsonPath("$.vidas").value(3))
            .andExpect(jsonPath("$.respondida").value(false))
            .andExpect(jsonPath("$.palavra.id").isNumber());

        mockMvc.perform(get("/api/individual/estado")).andExpect(status().isOk()).andExpect(jsonPath("$.rodada").value(1));
        mockMvc.perform(get("/api/individual/recordes")).andExpect(jsonPath("$.emAndamento").value(true));

        // Pular antes de responder é recusado
        mockMvc.perform(post("/api/individual/proxima")).andExpect(status().isBadRequest());

        // Resposta vazia = não respondeu: perde uma vida, e a palavra certa aparece
        mockMvc
            .perform(post("/api/individual/responder").contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correta").value(false))
            .andExpect(jsonPath("$.tipoErro").value("NAO_RESPONDEU"))
            .andExpect(jsonPath("$.textoCorreto").isNotEmpty())
            .andExpect(jsonPath("$.estado.vidas").value(2))
            .andExpect(jsonPath("$.estado.palavra.texto").isNotEmpty());

        // Responder de novo a mesma palavra é recusado
        mockMvc
            .perform(post("/api/individual/responder").contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\"casa\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.rodadainvalida"));

        mockMvc.perform(post("/api/individual/proxima")).andExpect(status().isOk()).andExpect(jsonPath("$.rodada").value(2));

        mockMvc
            .perform(post("/api/individual/encerrar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.encerramento").value("DESISTIU"))
            .andExpect(jsonPath("$.palavras").value(1))
            .andExpect(jsonPath("$.naoRespondidas").value(1));

        mockMvc.perform(get("/api/individual/estado")).andExpect(status().isNotFound());

        assertThat(historicoRespostaRepository.findAll())
            .filteredOn(h -> LOGIN.equals(h.getLogin()))
            .singleElement()
            .satisfies(h -> {
                assertThat(h.getOrigem()).isEqualTo(HistoricoResposta.Origem.INDIVIDUAL);
                assertThat(h.getTipoErro()).isEqualTo("NAO_RESPONDEU");
                assertThat(h.getSimilaridade()).isZero();
            });
        assertThat(partidaIndividualRepository.findFirstByLoginOrderByAcertosDescDataFimAsc(LOGIN)).isPresent();
    }

    @Test
    @WithMockUser(value = "individual-sem-partida", authorities = "ROLE_USER")
    void semPartidaNaoHaEstadoNemResposta() throws Exception {
        mockMvc.perform(get("/api/individual/estado")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/individual/audio")).andExpect(status().isNotFound());
        mockMvc
            .perform(post("/api/individual/responder").contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\"casa\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/individual/encerrar")).andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void anonimoNaoJoga() throws Exception {
        mockMvc.perform(post("/api/individual/iniciar")).andExpect(status().isUnauthorized());
    }
}
