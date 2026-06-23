package br.com.digitado.web.rest;

import static br.com.digitado.domain.AtividadeAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Atividade;
import br.com.digitado.domain.enumeration.ModoAtividade;
import br.com.digitado.domain.enumeration.StatusAtividade;
import br.com.digitado.repository.AtividadeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
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
 * Integration tests for the {@link AtividadeResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class AtividadeResourceIT {

    private static final String DEFAULT_TITULO = "AAAAAAAAAA";
    private static final String UPDATED_TITULO = "BBBBBBBBBB";

    private static final ModoAtividade DEFAULT_MODO = ModoAtividade.INDIVIDUAL;
    private static final ModoAtividade UPDATED_MODO = ModoAtividade.COLETIVO;

    private static final Instant DEFAULT_DATA_INICIO = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_DATA_INICIO = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_DATA_FIM = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_DATA_FIM = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Integer DEFAULT_TEMPO_LIMITE = 1;
    private static final Integer UPDATED_TEMPO_LIMITE = 2;

    private static final StatusAtividade DEFAULT_STATUS = StatusAtividade.PENDENTE;
    private static final StatusAtividade UPDATED_STATUS = StatusAtividade.EM_ANDAMENTO;

    private static final String ENTITY_API_URL = "/api/atividades";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AtividadeRepository atividadeRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restAtividadeMockMvc;

    private Atividade atividade;

    private Atividade insertedAtividade;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Atividade createEntity() {
        return new Atividade()
            .titulo(DEFAULT_TITULO)
            .modo(DEFAULT_MODO)
            .dataInicio(DEFAULT_DATA_INICIO)
            .dataFim(DEFAULT_DATA_FIM)
            .tempoLimite(DEFAULT_TEMPO_LIMITE)
            .status(DEFAULT_STATUS);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Atividade createUpdatedEntity() {
        return new Atividade()
            .titulo(UPDATED_TITULO)
            .modo(UPDATED_MODO)
            .dataInicio(UPDATED_DATA_INICIO)
            .dataFim(UPDATED_DATA_FIM)
            .tempoLimite(UPDATED_TEMPO_LIMITE)
            .status(UPDATED_STATUS);
    }

    @BeforeEach
    void initTest() {
        atividade = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedAtividade != null) {
            atividadeRepository.delete(insertedAtividade);
            insertedAtividade = null;
        }
    }

    @Test
    @Transactional
    void createAtividade() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Atividade
        var returnedAtividade = om.readValue(
            restAtividadeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(atividade)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Atividade.class
        );

        // Validate the Atividade in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertAtividadeUpdatableFieldsEquals(returnedAtividade, getPersistedAtividade(returnedAtividade));

        insertedAtividade = returnedAtividade;
    }

    @Test
    @Transactional
    void createAtividadeWithExistingId() throws Exception {
        // Create the Atividade with an existing ID
        atividade.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restAtividadeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(atividade)))
            .andExpect(status().isBadRequest());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkTituloIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        atividade.setTitulo(null);

        // Create the Atividade, which fails.

        restAtividadeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(atividade)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkModoIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        atividade.setModo(null);

        // Create the Atividade, which fails.

        restAtividadeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(atividade)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllAtividades() throws Exception {
        // Initialize the database
        insertedAtividade = atividadeRepository.saveAndFlush(atividade);

        // Get all the atividadeList
        restAtividadeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(atividade.getId().intValue())))
            .andExpect(jsonPath("$.[*].titulo").value(hasItem(DEFAULT_TITULO)))
            .andExpect(jsonPath("$.[*].modo").value(hasItem(DEFAULT_MODO.toString())))
            .andExpect(jsonPath("$.[*].dataInicio").value(hasItem(DEFAULT_DATA_INICIO.toString())))
            .andExpect(jsonPath("$.[*].dataFim").value(hasItem(DEFAULT_DATA_FIM.toString())))
            .andExpect(jsonPath("$.[*].tempoLimite").value(hasItem(DEFAULT_TEMPO_LIMITE)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())));
    }

    @Test
    @Transactional
    void getAtividade() throws Exception {
        // Initialize the database
        insertedAtividade = atividadeRepository.saveAndFlush(atividade);

        // Get the atividade
        restAtividadeMockMvc
            .perform(get(ENTITY_API_URL_ID, atividade.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(atividade.getId().intValue()))
            .andExpect(jsonPath("$.titulo").value(DEFAULT_TITULO))
            .andExpect(jsonPath("$.modo").value(DEFAULT_MODO.toString()))
            .andExpect(jsonPath("$.dataInicio").value(DEFAULT_DATA_INICIO.toString()))
            .andExpect(jsonPath("$.dataFim").value(DEFAULT_DATA_FIM.toString()))
            .andExpect(jsonPath("$.tempoLimite").value(DEFAULT_TEMPO_LIMITE))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()));
    }

    @Test
    @Transactional
    void getNonExistingAtividade() throws Exception {
        // Get the atividade
        restAtividadeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingAtividade() throws Exception {
        // Initialize the database
        insertedAtividade = atividadeRepository.saveAndFlush(atividade);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the atividade
        Atividade updatedAtividade = atividadeRepository.findById(atividade.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedAtividade are not directly saved in db
        em.detach(updatedAtividade);
        updatedAtividade
            .titulo(UPDATED_TITULO)
            .modo(UPDATED_MODO)
            .dataInicio(UPDATED_DATA_INICIO)
            .dataFim(UPDATED_DATA_FIM)
            .tempoLimite(UPDATED_TEMPO_LIMITE)
            .status(UPDATED_STATUS);

        restAtividadeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedAtividade.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedAtividade))
            )
            .andExpect(status().isOk());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedAtividadeToMatchAllProperties(updatedAtividade);
    }

    @Test
    @Transactional
    void putNonExistingAtividade() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        atividade.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAtividadeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, atividade.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(atividade))
            )
            .andExpect(status().isBadRequest());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchAtividade() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        atividade.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAtividadeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(atividade))
            )
            .andExpect(status().isBadRequest());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamAtividade() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        atividade.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAtividadeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(atividade)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateAtividadeWithPatch() throws Exception {
        // Initialize the database
        insertedAtividade = atividadeRepository.saveAndFlush(atividade);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the atividade using partial update
        Atividade partialUpdatedAtividade = new Atividade();
        partialUpdatedAtividade.setId(atividade.getId());

        partialUpdatedAtividade.status(UPDATED_STATUS);

        restAtividadeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAtividade.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAtividade))
            )
            .andExpect(status().isOk());

        // Validate the Atividade in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAtividadeUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedAtividade, atividade),
            getPersistedAtividade(atividade)
        );
    }

    @Test
    @Transactional
    void fullUpdateAtividadeWithPatch() throws Exception {
        // Initialize the database
        insertedAtividade = atividadeRepository.saveAndFlush(atividade);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the atividade using partial update
        Atividade partialUpdatedAtividade = new Atividade();
        partialUpdatedAtividade.setId(atividade.getId());

        partialUpdatedAtividade
            .titulo(UPDATED_TITULO)
            .modo(UPDATED_MODO)
            .dataInicio(UPDATED_DATA_INICIO)
            .dataFim(UPDATED_DATA_FIM)
            .tempoLimite(UPDATED_TEMPO_LIMITE)
            .status(UPDATED_STATUS);

        restAtividadeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAtividade.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAtividade))
            )
            .andExpect(status().isOk());

        // Validate the Atividade in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAtividadeUpdatableFieldsEquals(partialUpdatedAtividade, getPersistedAtividade(partialUpdatedAtividade));
    }

    @Test
    @Transactional
    void patchNonExistingAtividade() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        atividade.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAtividadeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, atividade.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(atividade))
            )
            .andExpect(status().isBadRequest());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchAtividade() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        atividade.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAtividadeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(atividade))
            )
            .andExpect(status().isBadRequest());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamAtividade() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        atividade.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAtividadeMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(atividade)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Atividade in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteAtividade() throws Exception {
        // Initialize the database
        insertedAtividade = atividadeRepository.saveAndFlush(atividade);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the atividade
        restAtividadeMockMvc
            .perform(delete(ENTITY_API_URL_ID, atividade.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return atividadeRepository.count();
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

    protected Atividade getPersistedAtividade(Atividade atividade) {
        return atividadeRepository.findById(atividade.getId()).orElseThrow();
    }

    protected void assertPersistedAtividadeToMatchAllProperties(Atividade expectedAtividade) {
        assertAtividadeAllPropertiesEquals(expectedAtividade, getPersistedAtividade(expectedAtividade));
    }

    protected void assertPersistedAtividadeToMatchUpdatableProperties(Atividade expectedAtividade) {
        assertAtividadeAllUpdatablePropertiesEquals(expectedAtividade, getPersistedAtividade(expectedAtividade));
    }
}
