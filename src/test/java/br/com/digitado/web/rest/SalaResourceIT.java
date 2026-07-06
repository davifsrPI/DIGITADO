package br.com.digitado.web.rest;

import static br.com.digitado.domain.SalaAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Sala;
import br.com.digitado.repository.SalaRepository;
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
 * Integration tests for the {@link SalaResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
// Executa como ADMIN: o SalaResource aplica regras de dono (professor) que o
// usuário mock genérico não satisfaz — admin enxerga e edita qualquer sala
@WithMockUser(authorities = { "ROLE_ADMIN", "ROLE_USER" })
class SalaResourceIT {

    private static final String DEFAULT_NOME = "AAAAAAAAAA";
    private static final String UPDATED_NOME = "BBBBBBBBBB";

    private static final String DEFAULT_CODIGO = "AAAAAAAAAA";
    private static final String UPDATED_CODIGO = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRICAO = "AAAAAAAAAA";
    private static final String UPDATED_DESCRICAO = "BBBBBBBBBB";

    private static final Boolean DEFAULT_ATIVO = false;
    private static final Boolean UPDATED_ATIVO = true;

    private static final String ENTITY_API_URL = "/api/salas";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

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

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Sala createEntity() {
        return new Sala().nome(DEFAULT_NOME).codigo(DEFAULT_CODIGO).descricao(DEFAULT_DESCRICAO).ativo(DEFAULT_ATIVO);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Sala createUpdatedEntity() {
        return new Sala().nome(UPDATED_NOME).codigo(UPDATED_CODIGO).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);
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
    void createSalaWithExistingId() throws Exception {
        // Create the Sala with an existing ID
        sala.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restSalaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
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
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(sala.getId().intValue())))
            .andExpect(jsonPath("$.[*].nome").value(hasItem(DEFAULT_NOME)))
            .andExpect(jsonPath("$.[*].codigo").value(hasItem(DEFAULT_CODIGO)))
            .andExpect(jsonPath("$.[*].descricao").value(hasItem(DEFAULT_DESCRICAO)))
            .andExpect(jsonPath("$.[*].ativo").value(hasItem(DEFAULT_ATIVO)));
    }

    @Test
    @Transactional
    void getSala() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        // Get the sala
        restSalaMockMvc
            .perform(get(ENTITY_API_URL_ID, sala.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(sala.getId().intValue()))
            .andExpect(jsonPath("$.nome").value(DEFAULT_NOME))
            .andExpect(jsonPath("$.codigo").value(DEFAULT_CODIGO))
            .andExpect(jsonPath("$.descricao").value(DEFAULT_DESCRICAO))
            .andExpect(jsonPath("$.ativo").value(DEFAULT_ATIVO));
    }

    @Test
    @Transactional
    void getNonExistingSala() throws Exception {
        // Get the sala
        restSalaMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingSala() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the sala
        Sala updatedSala = salaRepository.findById(sala.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedSala are not directly saved in db
        em.detach(updatedSala);
        updatedSala.nome(UPDATED_NOME).codigo(UPDATED_CODIGO).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);

        restSalaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedSala.getId())
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
        sala.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(put(ENTITY_API_URL_ID, sala.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(sala))
            )
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(sala)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateSalaWithPatch() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the sala using partial update
        Sala partialUpdatedSala = new Sala();
        partialUpdatedSala.setId(sala.getId());

        partialUpdatedSala.ativo(UPDATED_ATIVO);

        restSalaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSala.getId())
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
    void fullUpdateSalaWithPatch() throws Exception {
        // Initialize the database
        insertedSala = salaRepository.saveAndFlush(sala);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the sala using partial update
        Sala partialUpdatedSala = new Sala();
        partialUpdatedSala.setId(sala.getId());

        partialUpdatedSala.nome(UPDATED_NOME).codigo(UPDATED_CODIGO).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);

        restSalaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSala.getId())
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
    void patchNonExistingSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(patch(ENTITY_API_URL_ID, sala.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(sala)))
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSalaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(sala))
            )
            .andExpect(status().isBadRequest());

        // Validate the Sala in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamSala() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        sala.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
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
            .perform(delete(ENTITY_API_URL_ID, sala.getId()).accept(MediaType.APPLICATION_JSON))
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
        return salaRepository.findById(sala.getId()).orElseThrow();
    }

    protected void assertPersistedSalaToMatchAllProperties(Sala expectedSala) {
        assertSalaAllPropertiesEquals(expectedSala, getPersistedSala(expectedSala));
    }

    protected void assertPersistedSalaToMatchUpdatableProperties(Sala expectedSala) {
        assertSalaAllUpdatablePropertiesEquals(expectedSala, getPersistedSala(expectedSala));
    }
}
