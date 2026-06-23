package br.com.digitado.web.rest;

import static br.com.digitado.domain.RespostaAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Resposta;
import br.com.digitado.repository.RespostaRepository;
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
 * Integration tests for the {@link RespostaResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class RespostaResourceIT {

    private static final String DEFAULT_RESPOSTA_DIGITADA = "AAAAAAAAAA";
    private static final String UPDATED_RESPOSTA_DIGITADA = "BBBBBBBBBB";

    private static final Boolean DEFAULT_CORRETA = false;
    private static final Boolean UPDATED_CORRETA = true;

    private static final Integer DEFAULT_TEMPO_RESPOSTA = 1;
    private static final Integer UPDATED_TEMPO_RESPOSTA = 2;

    private static final Integer DEFAULT_PONTUACAO = 1;
    private static final Integer UPDATED_PONTUACAO = 2;

    private static final Instant DEFAULT_DATA_RESPOSTA = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_DATA_RESPOSTA = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/respostas";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private RespostaRepository respostaRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restRespostaMockMvc;

    private Resposta resposta;

    private Resposta insertedResposta;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Resposta createEntity() {
        return new Resposta()
            .respostaDigitada(DEFAULT_RESPOSTA_DIGITADA)
            .correta(DEFAULT_CORRETA)
            .tempoResposta(DEFAULT_TEMPO_RESPOSTA)
            .pontuacao(DEFAULT_PONTUACAO)
            .dataResposta(DEFAULT_DATA_RESPOSTA);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Resposta createUpdatedEntity() {
        return new Resposta()
            .respostaDigitada(UPDATED_RESPOSTA_DIGITADA)
            .correta(UPDATED_CORRETA)
            .tempoResposta(UPDATED_TEMPO_RESPOSTA)
            .pontuacao(UPDATED_PONTUACAO)
            .dataResposta(UPDATED_DATA_RESPOSTA);
    }

    @BeforeEach
    void initTest() {
        resposta = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedResposta != null) {
            respostaRepository.delete(insertedResposta);
            insertedResposta = null;
        }
    }

    @Test
    @Transactional
    void createResposta() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Resposta
        var returnedResposta = om.readValue(
            restRespostaMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(resposta)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Resposta.class
        );

        // Validate the Resposta in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertRespostaUpdatableFieldsEquals(returnedResposta, getPersistedResposta(returnedResposta));

        insertedResposta = returnedResposta;
    }

    @Test
    @Transactional
    void createRespostaWithExistingId() throws Exception {
        // Create the Resposta with an existing ID
        resposta.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restRespostaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(resposta)))
            .andExpect(status().isBadRequest());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllRespostas() throws Exception {
        // Initialize the database
        insertedResposta = respostaRepository.saveAndFlush(resposta);

        // Get all the respostaList
        restRespostaMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(resposta.getId().intValue())))
            .andExpect(jsonPath("$.[*].respostaDigitada").value(hasItem(DEFAULT_RESPOSTA_DIGITADA)))
            .andExpect(jsonPath("$.[*].correta").value(hasItem(DEFAULT_CORRETA)))
            .andExpect(jsonPath("$.[*].tempoResposta").value(hasItem(DEFAULT_TEMPO_RESPOSTA)))
            .andExpect(jsonPath("$.[*].pontuacao").value(hasItem(DEFAULT_PONTUACAO)))
            .andExpect(jsonPath("$.[*].dataResposta").value(hasItem(DEFAULT_DATA_RESPOSTA.toString())));
    }

    @Test
    @Transactional
    void getResposta() throws Exception {
        // Initialize the database
        insertedResposta = respostaRepository.saveAndFlush(resposta);

        // Get the resposta
        restRespostaMockMvc
            .perform(get(ENTITY_API_URL_ID, resposta.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(resposta.getId().intValue()))
            .andExpect(jsonPath("$.respostaDigitada").value(DEFAULT_RESPOSTA_DIGITADA))
            .andExpect(jsonPath("$.correta").value(DEFAULT_CORRETA))
            .andExpect(jsonPath("$.tempoResposta").value(DEFAULT_TEMPO_RESPOSTA))
            .andExpect(jsonPath("$.pontuacao").value(DEFAULT_PONTUACAO))
            .andExpect(jsonPath("$.dataResposta").value(DEFAULT_DATA_RESPOSTA.toString()));
    }

    @Test
    @Transactional
    void getNonExistingResposta() throws Exception {
        // Get the resposta
        restRespostaMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingResposta() throws Exception {
        // Initialize the database
        insertedResposta = respostaRepository.saveAndFlush(resposta);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the resposta
        Resposta updatedResposta = respostaRepository.findById(resposta.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedResposta are not directly saved in db
        em.detach(updatedResposta);
        updatedResposta
            .respostaDigitada(UPDATED_RESPOSTA_DIGITADA)
            .correta(UPDATED_CORRETA)
            .tempoResposta(UPDATED_TEMPO_RESPOSTA)
            .pontuacao(UPDATED_PONTUACAO)
            .dataResposta(UPDATED_DATA_RESPOSTA);

        restRespostaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedResposta.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedResposta))
            )
            .andExpect(status().isOk());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedRespostaToMatchAllProperties(updatedResposta);
    }

    @Test
    @Transactional
    void putNonExistingResposta() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        resposta.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRespostaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, resposta.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(resposta))
            )
            .andExpect(status().isBadRequest());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchResposta() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        resposta.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRespostaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(resposta))
            )
            .andExpect(status().isBadRequest());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamResposta() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        resposta.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRespostaMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(resposta)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateRespostaWithPatch() throws Exception {
        // Initialize the database
        insertedResposta = respostaRepository.saveAndFlush(resposta);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the resposta using partial update
        Resposta partialUpdatedResposta = new Resposta();
        partialUpdatedResposta.setId(resposta.getId());

        partialUpdatedResposta.correta(UPDATED_CORRETA).tempoResposta(UPDATED_TEMPO_RESPOSTA).dataResposta(UPDATED_DATA_RESPOSTA);

        restRespostaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedResposta.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedResposta))
            )
            .andExpect(status().isOk());

        // Validate the Resposta in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRespostaUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedResposta, resposta), getPersistedResposta(resposta));
    }

    @Test
    @Transactional
    void fullUpdateRespostaWithPatch() throws Exception {
        // Initialize the database
        insertedResposta = respostaRepository.saveAndFlush(resposta);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the resposta using partial update
        Resposta partialUpdatedResposta = new Resposta();
        partialUpdatedResposta.setId(resposta.getId());

        partialUpdatedResposta
            .respostaDigitada(UPDATED_RESPOSTA_DIGITADA)
            .correta(UPDATED_CORRETA)
            .tempoResposta(UPDATED_TEMPO_RESPOSTA)
            .pontuacao(UPDATED_PONTUACAO)
            .dataResposta(UPDATED_DATA_RESPOSTA);

        restRespostaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedResposta.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedResposta))
            )
            .andExpect(status().isOk());

        // Validate the Resposta in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRespostaUpdatableFieldsEquals(partialUpdatedResposta, getPersistedResposta(partialUpdatedResposta));
    }

    @Test
    @Transactional
    void patchNonExistingResposta() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        resposta.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRespostaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, resposta.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(resposta))
            )
            .andExpect(status().isBadRequest());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchResposta() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        resposta.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRespostaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(resposta))
            )
            .andExpect(status().isBadRequest());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamResposta() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        resposta.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRespostaMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(resposta)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Resposta in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteResposta() throws Exception {
        // Initialize the database
        insertedResposta = respostaRepository.saveAndFlush(resposta);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the resposta
        restRespostaMockMvc
            .perform(delete(ENTITY_API_URL_ID, resposta.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return respostaRepository.count();
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

    protected Resposta getPersistedResposta(Resposta resposta) {
        return respostaRepository.findById(resposta.getId()).orElseThrow();
    }

    protected void assertPersistedRespostaToMatchAllProperties(Resposta expectedResposta) {
        assertRespostaAllPropertiesEquals(expectedResposta, getPersistedResposta(expectedResposta));
    }

    protected void assertPersistedRespostaToMatchUpdatableProperties(Resposta expectedResposta) {
        assertRespostaAllUpdatablePropertiesEquals(expectedResposta, getPersistedResposta(expectedResposta));
    }
}
