package br.com.digitado.web.rest;

import static br.com.digitado.domain.ErroOrtograficoAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.ErroOrtografico;
import br.com.digitado.domain.enumeration.TipoErro;
import br.com.digitado.repository.ErroOrtograficoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
 * Integration tests for the {@link ErroOrtograficoResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ErroOrtograficoResourceIT {

    private static final TipoErro DEFAULT_TIPO_ERRO = TipoErro.ACENTUACAO;
    private static final TipoErro UPDATED_TIPO_ERRO = TipoErro.TROCA_LETRA;

    private static final String DEFAULT_DESCRICAO = "AAAAAAAAAA";
    private static final String UPDATED_DESCRICAO = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/erro-ortograficos";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ErroOrtograficoRepository erroOrtograficoRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restErroOrtograficoMockMvc;

    private ErroOrtografico erroOrtografico;

    private ErroOrtografico insertedErroOrtografico;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ErroOrtografico createEntity() {
        return new ErroOrtografico().tipoErro(DEFAULT_TIPO_ERRO).descricao(DEFAULT_DESCRICAO);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ErroOrtografico createUpdatedEntity() {
        return new ErroOrtografico().tipoErro(UPDATED_TIPO_ERRO).descricao(UPDATED_DESCRICAO);
    }

    @BeforeEach
    void initTest() {
        erroOrtografico = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedErroOrtografico != null) {
            erroOrtograficoRepository.delete(insertedErroOrtografico);
            insertedErroOrtografico = null;
        }
    }

    @Test
    @Transactional
    void createErroOrtografico() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ErroOrtografico
        var returnedErroOrtografico = om.readValue(
            restErroOrtograficoMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(erroOrtografico)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ErroOrtografico.class
        );

        // Validate the ErroOrtografico in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertErroOrtograficoUpdatableFieldsEquals(returnedErroOrtografico, getPersistedErroOrtografico(returnedErroOrtografico));

        insertedErroOrtografico = returnedErroOrtografico;
    }

    @Test
    @Transactional
    void createErroOrtograficoWithExistingId() throws Exception {
        // Create the ErroOrtografico with an existing ID
        erroOrtografico.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restErroOrtograficoMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(erroOrtografico)))
            .andExpect(status().isBadRequest());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllErroOrtograficos() throws Exception {
        // Initialize the database
        insertedErroOrtografico = erroOrtograficoRepository.saveAndFlush(erroOrtografico);

        // Get all the erroOrtograficoList
        restErroOrtograficoMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(erroOrtografico.getId().intValue())))
            .andExpect(jsonPath("$.[*].tipoErro").value(hasItem(DEFAULT_TIPO_ERRO.toString())))
            .andExpect(jsonPath("$.[*].descricao").value(hasItem(DEFAULT_DESCRICAO)));
    }

    @Test
    @Transactional
    void getErroOrtografico() throws Exception {
        // Initialize the database
        insertedErroOrtografico = erroOrtograficoRepository.saveAndFlush(erroOrtografico);

        // Get the erroOrtografico
        restErroOrtograficoMockMvc
            .perform(get(ENTITY_API_URL_ID, erroOrtografico.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(erroOrtografico.getId().intValue()))
            .andExpect(jsonPath("$.tipoErro").value(DEFAULT_TIPO_ERRO.toString()))
            .andExpect(jsonPath("$.descricao").value(DEFAULT_DESCRICAO));
    }

    @Test
    @Transactional
    void getNonExistingErroOrtografico() throws Exception {
        // Get the erroOrtografico
        restErroOrtograficoMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingErroOrtografico() throws Exception {
        // Initialize the database
        insertedErroOrtografico = erroOrtograficoRepository.saveAndFlush(erroOrtografico);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the erroOrtografico
        ErroOrtografico updatedErroOrtografico = erroOrtograficoRepository.findById(erroOrtografico.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedErroOrtografico are not directly saved in db
        em.detach(updatedErroOrtografico);
        updatedErroOrtografico.tipoErro(UPDATED_TIPO_ERRO).descricao(UPDATED_DESCRICAO);

        restErroOrtograficoMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedErroOrtografico.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedErroOrtografico))
            )
            .andExpect(status().isOk());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedErroOrtograficoToMatchAllProperties(updatedErroOrtografico);
    }

    @Test
    @Transactional
    void putNonExistingErroOrtografico() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        erroOrtografico.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restErroOrtograficoMockMvc
            .perform(
                put(ENTITY_API_URL_ID, erroOrtografico.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(erroOrtografico))
            )
            .andExpect(status().isBadRequest());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchErroOrtografico() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        erroOrtografico.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restErroOrtograficoMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(erroOrtografico))
            )
            .andExpect(status().isBadRequest());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamErroOrtografico() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        erroOrtografico.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restErroOrtograficoMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(erroOrtografico)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateErroOrtograficoWithPatch() throws Exception {
        // Initialize the database
        insertedErroOrtografico = erroOrtograficoRepository.saveAndFlush(erroOrtografico);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the erroOrtografico using partial update
        ErroOrtografico partialUpdatedErroOrtografico = new ErroOrtografico();
        partialUpdatedErroOrtografico.setId(erroOrtografico.getId());

        partialUpdatedErroOrtografico.tipoErro(UPDATED_TIPO_ERRO).descricao(UPDATED_DESCRICAO);

        restErroOrtograficoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedErroOrtografico.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedErroOrtografico))
            )
            .andExpect(status().isOk());

        // Validate the ErroOrtografico in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertErroOrtograficoUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedErroOrtografico, erroOrtografico),
            getPersistedErroOrtografico(erroOrtografico)
        );
    }

    @Test
    @Transactional
    void fullUpdateErroOrtograficoWithPatch() throws Exception {
        // Initialize the database
        insertedErroOrtografico = erroOrtograficoRepository.saveAndFlush(erroOrtografico);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the erroOrtografico using partial update
        ErroOrtografico partialUpdatedErroOrtografico = new ErroOrtografico();
        partialUpdatedErroOrtografico.setId(erroOrtografico.getId());

        partialUpdatedErroOrtografico.tipoErro(UPDATED_TIPO_ERRO).descricao(UPDATED_DESCRICAO);

        restErroOrtograficoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedErroOrtografico.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedErroOrtografico))
            )
            .andExpect(status().isOk());

        // Validate the ErroOrtografico in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertErroOrtograficoUpdatableFieldsEquals(
            partialUpdatedErroOrtografico,
            getPersistedErroOrtografico(partialUpdatedErroOrtografico)
        );
    }

    @Test
    @Transactional
    void patchNonExistingErroOrtografico() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        erroOrtografico.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restErroOrtograficoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, erroOrtografico.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(erroOrtografico))
            )
            .andExpect(status().isBadRequest());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchErroOrtografico() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        erroOrtografico.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restErroOrtograficoMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(erroOrtografico))
            )
            .andExpect(status().isBadRequest());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamErroOrtografico() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        erroOrtografico.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restErroOrtograficoMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(erroOrtografico)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ErroOrtografico in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteErroOrtografico() throws Exception {
        // Initialize the database
        insertedErroOrtografico = erroOrtograficoRepository.saveAndFlush(erroOrtografico);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the erroOrtografico
        restErroOrtograficoMockMvc
            .perform(delete(ENTITY_API_URL_ID, erroOrtografico.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return erroOrtograficoRepository.count();
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

    protected ErroOrtografico getPersistedErroOrtografico(ErroOrtografico erroOrtografico) {
        return erroOrtograficoRepository.findById(erroOrtografico.getId()).orElseThrow();
    }

    protected void assertPersistedErroOrtograficoToMatchAllProperties(ErroOrtografico expectedErroOrtografico) {
        assertErroOrtograficoAllPropertiesEquals(expectedErroOrtografico, getPersistedErroOrtografico(expectedErroOrtografico));
    }

    protected void assertPersistedErroOrtograficoToMatchUpdatableProperties(ErroOrtografico expectedErroOrtografico) {
        assertErroOrtograficoAllUpdatablePropertiesEquals(expectedErroOrtografico, getPersistedErroOrtografico(expectedErroOrtografico));
    }
}
