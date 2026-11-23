package br.com.digitado.web.rest;

import static br.com.digitado.domain.SalaAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Sala;
import br.com.digitado.domain.enumeration.TipoSala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.service.CodigoSalaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link SalaResource} REST controller.
 * A sala é identificada pelo código de acesso (chave primária) - não há id numérico.
 */
@IntegrationTest
@AutoConfigureMockMvc
// Executa como ADMIN: o SalaResource aplica regras de dono (professor) que o
// usuário mock genérico não satisfaz - admin enxerga e edita qualquer sala
@WithMockUser(authorities = { "ROLE_ADMIN", "ROLE_USER" })
class SalaResourceIT {

    private static final String DEFAULT_NOME = "AAAAAAAAAA";
    private static final String UPDATED_NOME = "BBBBBBBBBB";

    private static final String DEFAULT_CODIGO = "TST234";

    // A descricao é uma coluna JSON: {"descricao": texto, "modo": "1v1"|"normal"}.
    // Os valores salvos direto pelo repositório precisam ser JSON válido.
    private static final String DEFAULT_DESCRICAO_TEXTO = "AAAAAAAAAA";
    private static final String DEFAULT_DESCRICAO = "{\"descricao\":\"AAAAAAAAAA\",\"modo\":\"normal\"}";
    private static final String UPDATED_DESCRICAO = "{\"descricao\":\"BBBBBBBBBB\",\"modo\":\"normal\"}";

    private static final Boolean DEFAULT_ATIVO = false;
    private static final Boolean UPDATED_ATIVO = true;

    private static final String ENTITY_API_URL = "/api/salas";
    private static final String ENTITY_API_URL_CODIGO = ENTITY_API_URL + "/{codigo}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private SalaRepository salaRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restSalaMockMvc;

    private Sala sala;

    private Sala insertedSala;

    // Gera um código aleatório, no formato do servidor, para não colidir com salas existentes
    private static String randomCodigo() {
        return new CodigoSalaService(null).sortear();
    }

    @Autowired
    private UsuarioRepository usuarioRepository;

    // O jogador "user" (conta padrão) como dono da sala: PATCH e as rotas da partida
    // são do dono, e a conta admin não joga mais
    private void doUser(Sala s) {
        s.setProfessor(usuarioRepository.findByEmail("user@localhost").orElseThrow());
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Sala createEntity() {
        return new Sala().codigo(DEFAULT_CODIGO).nome(DEFAULT_NOME).descricao(DEFAULT_DESCRICAO).ativo(DEFAULT_ATIVO);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Sala createUpdatedEntity() {
        return new Sala().codigo(randomCodigo()).nome(UPDATED_NOME).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);
    }

    @BeforeEach
    void initTest() {
        sala = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedSala != null) {
            salaRepository.delete(insertedSala);
            insertedSala = null;
        }
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void createSala() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Sala
        var returnedSala = om.readValue(
            restSalaMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Sala.class
        );

        // Validate the Sala in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertSalaUpdatableFieldsEquals(returnedSala, getPersistedSala(returnedSala));

        insertedSala = returnedSala;
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void createSalaWithExistingCodigo() throws Exception {
        // Initialize the database with a sala using the same codigo
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // A sala with an existing codigo cannot be created, so this API call must fail
        restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(createEntity())))
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void checkNomeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        sala.setNome(null);

        // Create the Sala, which fails.

        restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void checkCodigoIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        sala.setCodigo(null);

        // Create the Sala, which fails.

        restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllSalas() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        // Get all the salaList
        restSalaMockMvc
            .perform(get(ENTITY_API_URL + "?sort=codigo,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].codigo").value(hasItem(DEFAULT_CODIGO)))
            .andExpect(jsonPath("$.[*].nome").value(hasItem(DEFAULT_NOME)))
            .andExpect(jsonPath("$.[*].descricao.descricao").value(hasItem(DEFAULT_DESCRICAO_TEXTO)))
            .andExpect(jsonPath("$.[*].descricao.modo").value(hasItem("normal")))
            .andExpect(jsonPath("$.[*].ativo").value(hasItem(DEFAULT_ATIVO)));
    }

    @Test
    @Transactional
    void getSala() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        // Get the sala
        restSalaMockMvc
            .perform(get(ENTITY_API_URL_CODIGO, sala.getCodigo()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.codigo").value(DEFAULT_CODIGO))
            .andExpect(jsonPath("$.nome").value(DEFAULT_NOME))
            .andExpect(jsonPath("$.descricao.descricao").value(DEFAULT_DESCRICAO_TEXTO))
            .andExpect(jsonPath("$.descricao.modo").value("normal"))
            .andExpect(jsonPath("$.ativo").value(DEFAULT_ATIVO));
    }

    @Test
    @Transactional
    void getNonExistingSala() throws Exception {
        // Get the sala
        restSalaMockMvc.perform(get(ENTITY_API_URL_CODIGO, randomCodigo())).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingSala() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the sala - o codigo (PK) permanece o mesmo
        Sala updatedSala = salaRepository.findById(sala.getCodigo()).orElseThrow();
        // Disconnect from session so that the updates on updatedSala are not directly saved in db
        em.detach(updatedSala);
        updatedSala.nome(UPDATED_NOME).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);

        restSalaMockMvc
            .perform(
                put(ENTITY_API_URL_CODIGO, updatedSala.getCodigo())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedSala))
            )
            .andExpect(status().isOk());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedSalaToMatchAllProperties(updatedSala);
    }

    @Test
    @Transactional
    void putNonExistingSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setCodigo(randomCodigo());

        // If the sala doesn't exist in the database, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(
                put(ENTITY_API_URL_CODIGO, sala.getCodigo()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala))
            )
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithCodigoMismatchSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setCodigo(randomCodigo());

        // If url codigo doesn't match entity codigo, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(put(ENTITY_API_URL_CODIGO, randomCodigo()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingCodigoPathParamSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setCodigo(randomCodigo());

        // If url codigo is missing, it will throw MethodNotAllowed
        restSalaMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void partialUpdateSalaWithPatch() throws Exception {
        // Initialize the database
        doUser(sala);
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the sala using partial update
        Sala partialUpdatedSala = new Sala();
        partialUpdatedSala.setCodigo(sala.getCodigo());

        partialUpdatedSala.ativo(UPDATED_ATIVO);

        restSalaMockMvc
            .perform(
                patch(ENTITY_API_URL_CODIGO, partialUpdatedSala.getCodigo())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSala))
            )
            .andExpect(status().isOk());

        // Validate the Sala in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSalaUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedSala, sala), getPersistedSala(sala));
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void fullUpdateSalaWithPatch() throws Exception {
        // Initialize the database
        doUser(sala);
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the sala using partial update - o codigo (PK) não muda
        Sala partialUpdatedSala = new Sala();
        partialUpdatedSala.setCodigo(sala.getCodigo());

        partialUpdatedSala.nome(UPDATED_NOME).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);

        restSalaMockMvc
            .perform(
                patch(ENTITY_API_URL_CODIGO, partialUpdatedSala.getCodigo())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSala))
            )
            .andExpect(status().isOk());

        // Validate the Sala in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSalaUpdatableFieldsEquals(partialUpdatedSala, getPersistedSala(partialUpdatedSala));
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void patchNonExistingSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setCodigo(randomCodigo());

        // If the sala doesn't exist in the database, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(
                patch(ENTITY_API_URL_CODIGO, sala.getCodigo())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(sala))
            )
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void patchWithCodigoMismatchSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setCodigo(randomCodigo());

        // If url codigo doesn't match entity codigo, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(
                patch(ENTITY_API_URL_CODIGO, randomCodigo()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(sala))
            )
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void patchWithMissingCodigoPathParamSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setCodigo(randomCodigo());

        // If url codigo is missing, it will throw MethodNotAllowed
        restSalaMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(sala)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteSala() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the sala
        restSalaMockMvc
            .perform(delete(ENTITY_API_URL_CODIGO, sala.getCodigo()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return salaRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Sala getPersistedSala(Sala sala) {
        return salaRepository.findById(sala.getCodigo()).orElseThrow();
    }

    protected void assertPersistedSalaToMatchAllProperties(Sala expectedSala) {
        assertSalaAllPropertiesEquals(expectedSala, getPersistedSala(expectedSala));
    }

    protected void assertPersistedSalaToMatchUpdatableProperties(Sala expectedSala) {
        assertSalaAllUpdatablePropertiesEquals(expectedSala, getPersistedSala(expectedSala));
    }

    // A conta de administração (QUALQUER uma com ROLE_ADMIN - a classe roda com um
    // mock sem login "admin") só joga DUELO: é o "desenvolvedor" da conquista
    // "Vença de um Desenvolvedor".
    @Test
    @Transactional
    @WithMockUser(value = "outro-admin", authorities = "ROLE_ADMIN")
    void aContaAdminNaoCriaSalaDeTurma() throws Exception {
        long antes = getRepositoryCount();
        sala.setCodigo(randomCodigo());
        restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.forbidden"));
        assertSameRepositoryCount(antes);
    }

    @Test
    @Transactional
    @WithMockUser(value = "outro-admin", authorities = "ROLE_ADMIN")
    void aContaAdminCriaDuelo() throws Exception {
        restSalaMockMvc.perform(get(ENTITY_API_URL + "/codigo/novo")).andExpect(status().isOk());
        restSalaMockMvc.perform(get(ENTITY_API_URL + "/1v1/publicas")).andExpect(status().isOk());
        sala.setCodigo(randomCodigo());
        sala.setTipo(TipoSala.UM_V_UM);
        String resposta = restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tipo").value("UM_V_UM"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        insertedSala = salaRepository.findById(om.readTree(resposta).get("codigo").asText()).orElseThrow();
    }

    @Test
    @Transactional
    @WithMockUser(value = "outro-admin", authorities = "ROLE_ADMIN")
    void aContaAdminNaoUsaAsRotasDeSalaDeTurma() throws Exception {
        doUser(sala);
        insertedSala = salaRepository.saveAndFlush(sala);
        // Rotas que o duelo usa: abertas ao admin, mas o SalaResource recusa sala de turma
        for (String rota : new String[] { "/configuracao", "/sou-professor", "/audio", "/meu-resumo" }) {
            restSalaMockMvc
                .perform(get(ENTITY_API_URL_CODIGO + rota, sala.getCodigo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.forbidden"));
        }
        // Rotas só de turma: fechadas ao admin já na SecurityConfiguration
        for (String rota : new String[] { "/palavras", "/palavra-atual", "/relatorio", "/participantes", "/estatisticas" }) {
            restSalaMockMvc.perform(get(ENTITY_API_URL_CODIGO + rota, sala.getCodigo())).andExpect(status().isForbidden());
        }
        // As telas de Entidades continuam: ver a sala
        restSalaMockMvc.perform(get(ENTITY_API_URL_CODIGO, sala.getCodigo())).andExpect(status().isOk());
    }

    @Test
    @Transactional
    @WithMockUser(value = "outro-admin", authorities = "ROLE_ADMIN")
    void noDueloDeOutraPessoaOAdminEhSoOOponente() throws Exception {
        // Duelo criado por "user": o admin entra como oponente, e NÃO como dono -
        // antes o admin era "dono" de toda sala e leria as palavras da partida
        doUser(sala);
        sala.setTipo(TipoSala.UM_V_UM);
        sala.setConfiguracao("{\"qtdFacil\":1,\"palavrasExtrasIds\":[1]}");
        insertedSala = salaRepository.saveAndFlush(sala);
        restSalaMockMvc
            .perform(get(ENTITY_API_URL_CODIGO + "/sou-professor", sala.getCodigo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.souProfessor").value(false));
        restSalaMockMvc
            .perform(get(ENTITY_API_URL_CODIGO + "/configuracao", sala.getCodigo()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.forbidden"));
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void codigoForaDoFormatoDoServidorEhRecusado() throws Exception {
        for (String codigo : new String[] { "abc", "AAAAAAAAAA", "ABC/12", "OIOI10", "ABC 23" }) {
            sala.setCodigo(codigo);
            restSalaMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.codigoinvalido"));
        }
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void nomeEDescricaoTemTeto() throws Exception {
        sala.setCodigo(randomCodigo());
        sala.setNome("x".repeat(81));
        restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.nomelongo"));
        sala.setNome("   ");
        restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void oCorpoNaoEscolheOProfessorDaSala() throws Exception {
        // Um cliente adulterado manda o professor no corpo: vale quem está logado
        String corpo =
            "{\"codigo\":\"" +
            randomCodigo() +
            "\",\"nome\":\"Turma\",\"professor\":{\"id\":" +
            usuarioRepository.findByEmail("admin@localhost").orElseThrow().getId() +
            "}}";
        String resposta = restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Sala criada = salaRepository.findById(om.readTree(resposta).get("codigo").asText()).orElseThrow();
        assertThat(criada.getProfessor().getEmail()).isEqualTo("user@localhost");
        insertedSala = criada;
    }

    // Duplicar = abrir a criação preenchida com /configuracao e /palavras da sala de
    // origem. As duas são as RESPOSTAS do ditado: só o dono lê.
    @Test
    @Transactional
    @WithMockUser(value = "intruso", authorities = "ROLE_USER")
    void soODonoLeAsPalavrasDaSalaParaDuplicar() throws Exception {
        doUser(sala);
        sala.setConfiguracao("{\"qtdFacil\":3,\"palavrasIds\":[1,2,3]}");
        insertedSala = salaRepository.saveAndFlush(sala);
        restSalaMockMvc
            .perform(get(ENTITY_API_URL_CODIGO + "/palavras", sala.getCodigo()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.forbidden"));
        restSalaMockMvc
            .perform(get(ENTITY_API_URL_CODIGO + "/configuracao", sala.getCodigo()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.forbidden"));
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void oDonoLeAsPalavrasDaSalaParaDuplicar() throws Exception {
        doUser(sala);
        sala.setConfiguracao("{\"qtdFacil\":3,\"palavrasIds\":[]}");
        insertedSala = salaRepository.saveAndFlush(sala);
        restSalaMockMvc.perform(get(ENTITY_API_URL_CODIGO + "/palavras", sala.getCodigo())).andExpect(status().isOk());
        restSalaMockMvc
            .perform(get(ENTITY_API_URL_CODIGO + "/configuracao", sala.getCodigo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qtdFacil").value(3));
    }

    @Test
    @Transactional
    @WithMockUser(value = "user", authorities = "ROLE_USER")
    void oEndpointAntigoDeDuplicarNaoExisteMais() throws Exception {
        doUser(sala);
        insertedSala = salaRepository.saveAndFlush(sala);
        long antes = getRepositoryCount();
        int status = restSalaMockMvc
            .perform(post(ENTITY_API_URL_CODIGO + "/duplicar", sala.getCodigo()))
            .andReturn()
            .getResponse()
            .getStatus();
        assertThat(status).isIn(404, 405);
        assertSameRepositoryCount(antes);
    }
}
