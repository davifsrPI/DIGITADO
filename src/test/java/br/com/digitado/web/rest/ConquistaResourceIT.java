package br.com.digitado.web.rest;

import static br.com.digitado.domain.ConquistaAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Conquista;
import br.com.digitado.repository.ConquistaRepository;
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
 * Integration tests for the {@link ConquistaResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
// Executa como ADMIN pois as mutações do catálogo de conquistas agora são restritas a administradores
@WithMockUser(authorities = { "ROLE_ADMIN", "ROLE_USER" })
class ConquistaResourceIT {

    private static final String DEFAULT_NOME = "AAAAAAAAAA";
    private static final String UPDATED_NOME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRICAO = "AAAAAAAAAA";
    private static final String UPDATED_DESCRICAO = "BBBBBBBBBB";

    private static final Integer DEFAULT_XP_RECOMPENSA = 1;
    private static final Integer UPDATED_XP_RECOMPENSA = 2;

    private static final String ENTITY_API_URL = "/api/conquistas";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ConquistaRepository conquistaRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restConquistaMockMvc;

    private Conquista conquista;

    private Conquista insertedConquista;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Conquista createEntity() {
        return new Conquista().nome(DEFAULT_NOME).descricao(DEFAULT_DESCRICAO).xpRecompensa(DEFAULT_XP_RECOMPENSA);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Conquista createUpdatedEntity() {
        return new Conquista().nome(UPDATED_NOME).descricao(UPDATED_DESCRICAO).xpRecompensa(UPDATED_XP_RECOMPENSA);
    }

    @BeforeEach
    void initTest() {
        conquista = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedConquista != null) {
            conquistaRepository.delete(insertedConquista);
            insertedConquista = null;
        }
    }

    @Test
    @Transactional
    void createConquista() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Conquista
        var returnedConquista = om.readValue(
            restConquistaMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(conquista)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Conquista.class
        );

        // Validate the Conquista in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertConquistaUpdatableFieldsEquals(returnedConquista, getPersistedConquista(returnedConquista));

        insertedConquista = returnedConquista;
    }

    @Test
    @Transactional
    void createConquistaWithExistingId() throws Exception {
        // Create the Conquista with an existing ID
        conquista.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restConquistaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(conquista)))
            .andExpect(status().isBadRequest());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNomeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        conquista.setNome(null);

        // Create the Conquista, which fails.

        restConquistaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(conquista)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllConquistas() throws Exception {
        // Initialize the database
        insertedConquista = conquistaRepository.saveAndFlush(conquista);

        // Get all the conquistaList
        restConquistaMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(conquista.getId().intValue())))
            .andExpect(jsonPath("$.[*].nome").value(hasItem(DEFAULT_NOME)))
            .andExpect(jsonPath("$.[*].descricao").value(hasItem(DEFAULT_DESCRICAO)))
            .andExpect(jsonPath("$.[*].xpRecompensa").value(hasItem(DEFAULT_XP_RECOMPENSA)));
    }

    @Test
    @Transactional
    void getConquista() throws Exception {
        // Initialize the database
        insertedConquista = conquistaRepository.saveAndFlush(conquista);

        // Get the conquista
        restConquistaMockMvc
            .perform(get(ENTITY_API_URL_ID, conquista.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(conquista.getId().intValue()))
            .andExpect(jsonPath("$.nome").value(DEFAULT_NOME))
            .andExpect(jsonPath("$.descricao").value(DEFAULT_DESCRICAO))
            .andExpect(jsonPath("$.xpRecompensa").value(DEFAULT_XP_RECOMPENSA));
    }

    @Test
    @Transactional
    void getNonExistingConquista() throws Exception {
        // Get the conquista
        restConquistaMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingConquista() throws Exception {
        // Initialize the database
        insertedConquista = conquistaRepository.saveAndFlush(conquista);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the conquista
        Conquista updatedConquista = conquistaRepository.findById(conquista.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedConquista are not directly saved in db
        em.detach(updatedConquista);
        updatedConquista.nome(UPDATED_NOME).descricao(UPDATED_DESCRICAO).xpRecompensa(UPDATED_XP_RECOMPENSA);

        restConquistaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedConquista.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedConquista))
            )
            .andExpect(status().isOk());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedConquistaToMatchAllProperties(updatedConquista);
    }

    @Test
    @Transactional
    void putNonExistingConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        conquista.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restConquistaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, conquista.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(conquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        conquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConquistaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(conquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        conquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConquistaMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(conquista)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateConquistaWithPatch() throws Exception {
        // Initialize the database
        insertedConquista = conquistaRepository.saveAndFlush(conquista);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the conquista using partial update
        Conquista partialUpdatedConquista = new Conquista();
        partialUpdatedConquista.setId(conquista.getId());

        partialUpdatedConquista.nome(UPDATED_NOME).descricao(UPDATED_DESCRICAO);

        restConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedConquista.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedConquista))
            )
            .andExpect(status().isOk());

        // Validate the Conquista in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertConquistaUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedConquista, conquista),
            getPersistedConquista(conquista)
        );
    }

    @Test
    @Transactional
    void fullUpdateConquistaWithPatch() throws Exception {
        // Initialize the database
        insertedConquista = conquistaRepository.saveAndFlush(conquista);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the conquista using partial update
        Conquista partialUpdatedConquista = new Conquista();
        partialUpdatedConquista.setId(conquista.getId());

        partialUpdatedConquista.nome(UPDATED_NOME).descricao(UPDATED_DESCRICAO).xpRecompensa(UPDATED_XP_RECOMPENSA);

        restConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedConquista.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedConquista))
            )
            .andExpect(status().isOk());

        // Validate the Conquista in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertConquistaUpdatableFieldsEquals(partialUpdatedConquista, getPersistedConquista(partialUpdatedConquista));
    }

    @Test
    @Transactional
    void patchNonExistingConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        conquista.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, conquista.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(conquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        conquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(conquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        conquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restConquistaMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(conquista)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Conquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteConquista() throws Exception {
        // Initialize the database
        insertedConquista = conquistaRepository.saveAndFlush(conquista);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the conquista
        restConquistaMockMvc
            .perform(delete(ENTITY_API_URL_ID, conquista.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return conquistaRepository.count();
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

    protected Conquista getPersistedConquista(Conquista conquista) {
        return conquistaRepository.findById(conquista.getId()).orElseThrow();
    }

    protected void assertPersistedConquistaToMatchAllProperties(Conquista expectedConquista) {
        assertConquistaAllPropertiesEquals(expectedConquista, getPersistedConquista(expectedConquista));
    }

    protected void assertPersistedConquistaToMatchUpdatableProperties(Conquista expectedConquista) {
        assertConquistaAllUpdatablePropertiesEquals(expectedConquista, getPersistedConquista(expectedConquista));
    }
}
