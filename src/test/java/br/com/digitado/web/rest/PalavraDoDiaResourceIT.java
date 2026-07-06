package br.com.digitado.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Palavra;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.service.PalavraDoDiaService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de regressão da Palavra do Dia (fluxo crítico e público):
 *
 * 1. O GET nunca expõe o texto da palavra — só o anagrama;
 * 2. A validação é do backend e as estatísticas são contabilizadas lá;
 * 3. Conta logada tem UMA chance por dia (banco);
 * 4. Anônimo é bloqueado pelo cookie httpOnly emitido pelo servidor.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PalavraDoDiaResourceIT {

    private static final String LOGIN = "pdd-user-it";

    @Autowired
    private PalavraRepository palavraRepository;

    @Autowired
    private MockMvc restMockMvc;

    private Palavra palavraDoDia;

    @BeforeEach
    void setup() {
        // Desativa todas as palavras e deixa só uma ativa — assim o sorteio
        // determinístico do dia cai sempre nela, independente dos dados do faker
        java.util.List<Palavra> todas = palavraRepository.findAll();
        todas.forEach(p -> p.setAtiva(false));
        palavraRepository.saveAllAndFlush(todas);
        palavraDoDia = palavraRepository.saveAndFlush(new Palavra().texto("escola").categoria("Educação").ativa(true));
    }

    @Test
    @WithAnonymousUser
    void getNaoExpoePalavraApenasAnagrama() throws Exception {
        restMockMvc
            .perform(get("/api/public/palavra-do-dia"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponivel").value(true))
            .andExpect(jsonPath("$.tamanho").value(6))
            .andExpect(jsonPath("$.jaTentou").value(false))
            .andExpect(jsonPath("$.resultado").isEmpty())
            // O anagrama existe mas não pode ser a palavra em ordem original
            .andExpect(jsonPath("$.letrasEmbaralhadas").isNotEmpty())
            .andExpect(jsonPath("$.letrasEmbaralhadas").value(org.hamcrest.Matchers.not("ESCOLA")));
    }

    @Test
    @WithMockUser(value = LOGIN, authorities = "ROLE_USER")
    void logadoTemUmaChancePorDiaValidadaNoBanco() throws Exception {
        // Primeira tentativa: acerto validado no backend e estatística contada
        restMockMvc
            .perform(post("/api/public/palavra-do-dia/tentar").contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\"ESCOLA\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acertou").value(true))
            .andExpect(jsonPath("$.palavraCorreta").value("escola"))
            .andExpect(jsonPath("$.totalTentativas").value(1))
            .andExpect(jsonPath("$.totalAcertos").value(1))
            .andExpect(cookie().httpOnly("pddTentativa", true));

        // Segunda tentativa da MESMA conta: bloqueada pelo banco, mesmo sem cookie
        restMockMvc
            .perform(post("/api/public/palavra-do-dia/tentar").contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\"escola\"}"))
            .andExpect(status().isBadRequest());

        // GET passa a devolver o resultado registrado
        restMockMvc
            .perform(get("/api/public/palavra-do-dia"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jaTentou").value(true))
            .andExpect(jsonPath("$.resultado.acertou").value(true));
    }

    @Test
    @WithAnonymousUser
    void anonimoErraEstatisticaContaEDepoisEBloqueadoPeloCookie() throws Exception {
        // Erro de anônimo: conta em total_tentativas mas não em total_acertos
        restMockMvc
            .perform(post("/api/public/palavra-do-dia/tentar").contentType(MediaType.APPLICATION_JSON).content("{\"resposta\":\"escoua\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acertou").value(false))
            .andExpect(jsonPath("$.totalTentativas").value(1))
            .andExpect(jsonPath("$.totalAcertos").value(0));

        // Confere direto nas colunas total_tentativas/total_acertos da tabela palavra
        Object[] estatistica = palavraRepository.buscarEstatistica(palavraDoDia.getId()).get(0);
        assertThat(((Number) estatistica[0]).longValue()).isEqualTo(1);
        assertThat(((Number) estatistica[1]).longValue()).isZero();

        // Com o cookie do dia, nova tentativa anônima é recusada
        Cookie cookieDeHoje = new Cookie("pddTentativa", LocalDate.now(PalavraDoDiaService.FUSO) + "_0");
        restMockMvc
            .perform(
                post("/api/public/palavra-do-dia/tentar")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"resposta\":\"escola\"}")
                    .cookie(cookieDeHoje)
            )
            .andExpect(status().isBadRequest());

        // E o GET com o cookie mostra que a chance já foi usada
        restMockMvc
            .perform(get("/api/public/palavra-do-dia").cookie(cookieDeHoje))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jaTentou").value(true))
            .andExpect(jsonPath("$.resultado.acertou").value(false));
    }
}
