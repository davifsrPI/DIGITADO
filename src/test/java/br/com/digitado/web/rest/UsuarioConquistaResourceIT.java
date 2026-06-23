package br.com.digitado.web.rest;

import static br.com.digitado.domain.UsuarioConquistaAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.UsuarioConquista;
import br.com.digitado.repository.UsuarioConquistaRepository;
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
 * Integration tests for the {@link UsuarioConquistaResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class UsuarioConquistaResourceIT {

    private static final Instant DEFAULT_DATA_CONQUISTA = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_DATA_CONQUISTA = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Integer DEFAULT_PROGRESSO = 1;
    private static final Integer UPDATED_PROGRESSO = 2;

    private static final Boolean DEFAULT_CONCLUIDA = false;
    private static final Boolean UPDATED_CONCLUIDA = true;

    private static final String ENTITY_API_URL = "/api/usuario-conquistas";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UsuarioConquistaRepository usuarioConquistaRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restUsuarioConquistaMockMvc;

    private UsuarioConquista usuarioConquista;

    private UsuarioConquista insertedUsuarioConquista;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UsuarioConquista createEntity() {
        return new UsuarioConquista().dataConquista(DEFAULT_DATA_CONQUISTA).progresso(DEFAULT_PROGRESSO).concluida(DEFAULT_CONCLUIDA);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UsuarioConquista createUpdatedEntity() {
        return new UsuarioConquista().dataConquista(UPDATED_DATA_CONQUISTA).progresso(UPDATED_PROGRESSO).concluida(UPDATED_CONCLUIDA);
    }

    @BeforeEach
    void initTest() {
        usuarioConquista = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedUsuarioConquista != null) {
            usuarioConquistaRepository.delete(insertedUsuarioConquista);
            insertedUsuarioConquista = null;
        }
    }

    @Test
    @Transactional
    void createUsuarioConquista() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the UsuarioConquista
        var returnedUsuarioConquista = om.readValue(
            restUsuarioConquistaMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(usuarioConquista)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            UsuarioConquista.class
        );

        // Validate the UsuarioConquista in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertUsuarioConquistaUpdatableFieldsEquals(returnedUsuarioConquista, getPersistedUsuarioConquista(returnedUsuarioConquista));

        insertedUsuarioConquista = returnedUsuarioConquista;
    }

    @Test
    @Transactional
    void createUsuarioConquistaWithExistingId() throws Exception {
        // Create the UsuarioConquista with an existing ID
        usuarioConquista.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restUsuarioConquistaMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(usuarioConquista)))
            .andExpect(status().isBadRequest());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllUsuarioConquistas() throws Exception {
        // Initialize the database
        insertedUsuarioConquista = usuarioConquistaRepository.saveAndFlush(usuarioConquista);

        // Get all the usuarioConquistaList
        restUsuarioConquistaMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(usuarioConquista.getId().intValue())))
            .andExpect(jsonPath("$.[*].dataConquista").value(hasItem(DEFAULT_DATA_CONQUISTA.toString())))
            .andExpect(jsonPath("$.[*].progresso").value(hasItem(DEFAULT_PROGRESSO)))
            .andExpect(jsonPath("$.[*].concluida").value(hasItem(DEFAULT_CONCLUIDA)));
    }

    @Test
    @Transactional
    void getUsuarioConquista() throws Exception {
        // Initialize the database
        insertedUsuarioConquista = usuarioConquistaRepository.saveAndFlush(usuarioConquista);

        // Get the usuarioConquista
        restUsuarioConquistaMockMvc
            .perform(get(ENTITY_API_URL_ID, usuarioConquista.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(usuarioConquista.getId().intValue()))
            .andExpect(jsonPath("$.dataConquista").value(DEFAULT_DATA_CONQUISTA.toString()))
            .andExpect(jsonPath("$.progresso").value(DEFAULT_PROGRESSO))
            .andExpect(jsonPath("$.concluida").value(DEFAULT_CONCLUIDA));
    }

    @Test
    @Transactional
    void getNonExistingUsuarioConquista() throws Exception {
        // Get the usuarioConquista
        restUsuarioConquistaMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingUsuarioConquista() throws Exception {
        // Initialize the database
        insertedUsuarioConquista = usuarioConquistaRepository.saveAndFlush(usuarioConquista);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the usuarioConquista
        UsuarioConquista updatedUsuarioConquista = usuarioConquistaRepository.findById(usuarioConquista.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedUsuarioConquista are not directly saved in db
        em.detach(updatedUsuarioConquista);
        updatedUsuarioConquista.dataConquista(UPDATED_DATA_CONQUISTA).progresso(UPDATED_PROGRESSO).concluida(UPDATED_CONCLUIDA);

        restUsuarioConquistaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedUsuarioConquista.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedUsuarioConquista))
            )
            .andExpect(status().isOk());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedUsuarioConquistaToMatchAllProperties(updatedUsuarioConquista);
    }

    @Test
    @Transactional
    void putNonExistingUsuarioConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        usuarioConquista.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUsuarioConquistaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, usuarioConquista.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(usuarioConquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchUsuarioConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        usuarioConquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUsuarioConquistaMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(usuarioConquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamUsuarioConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        usuarioConquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUsuarioConquistaMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(usuarioConquista)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateUsuarioConquistaWithPatch() throws Exception {
        // Initialize the database
        insertedUsuarioConquista = usuarioConquistaRepository.saveAndFlush(usuarioConquista);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the usuarioConquista using partial update
        UsuarioConquista partialUpdatedUsuarioConquista = new UsuarioConquista();
        partialUpdatedUsuarioConquista.setId(usuarioConquista.getId());

        partialUpdatedUsuarioConquista.dataConquista(UPDATED_DATA_CONQUISTA).progresso(UPDATED_PROGRESSO).concluida(UPDATED_CONCLUIDA);

        restUsuarioConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUsuarioConquista.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUsuarioConquista))
            )
            .andExpect(status().isOk());

        // Validate the UsuarioConquista in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUsuarioConquistaUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedUsuarioConquista, usuarioConquista),
            getPersistedUsuarioConquista(usuarioConquista)
        );
    }

    @Test
    @Transactional
    void fullUpdateUsuarioConquistaWithPatch() throws Exception {
        // Initialize the database
        insertedUsuarioConquista = usuarioConquistaRepository.saveAndFlush(usuarioConquista);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the usuarioConquista using partial update
        UsuarioConquista partialUpdatedUsuarioConquista = new UsuarioConquista();
        partialUpdatedUsuarioConquista.setId(usuarioConquista.getId());

        partialUpdatedUsuarioConquista.dataConquista(UPDATED_DATA_CONQUISTA).progresso(UPDATED_PROGRESSO).concluida(UPDATED_CONCLUIDA);

        restUsuarioConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUsuarioConquista.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUsuarioConquista))
            )
            .andExpect(status().isOk());

        // Validate the UsuarioConquista in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUsuarioConquistaUpdatableFieldsEquals(
            partialUpdatedUsuarioConquista,
            getPersistedUsuarioConquista(partialUpdatedUsuarioConquista)
        );
    }

    @Test
    @Transactional
    void patchNonExistingUsuarioConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        usuarioConquista.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUsuarioConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, usuarioConquista.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(usuarioConquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchUsuarioConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        usuarioConquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUsuarioConquistaMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(usuarioConquista))
            )
            .andExpect(status().isBadRequest());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamUsuarioConquista() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        usuarioConquista.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUsuarioConquistaMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(usuarioConquista)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the UsuarioConquista in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteUsuarioConquista() throws Exception {
        // Initialize the database
        insertedUsuarioConquista = usuarioConquistaRepository.saveAndFlush(usuarioConquista);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the usuarioConquista
        restUsuarioConquistaMockMvc
            .perform(delete(ENTITY_API_URL_ID, usuarioConquista.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return usuarioConquistaRepository.count();
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

    protected UsuarioConquista getPersistedUsuarioConquista(UsuarioConquista usuarioConquista) {
        return usuarioConquistaRepository.findById(usuarioConquista.getId()).orElseThrow();
    }

    protected void assertPersistedUsuarioConquistaToMatchAllProperties(UsuarioConquista expectedUsuarioConquista) {
        assertUsuarioConquistaAllPropertiesEquals(expectedUsuarioConquista, getPersistedUsuarioConquista(expectedUsuarioConquista));
    }

    protected void assertPersistedUsuarioConquistaToMatchUpdatableProperties(UsuarioConquista expectedUsuarioConquista) {
        assertUsuarioConquistaAllUpdatablePropertiesEquals(
            expectedUsuarioConquista,
            getPersistedUsuarioConquista(expectedUsuarioConquista)
        );
    }
}
