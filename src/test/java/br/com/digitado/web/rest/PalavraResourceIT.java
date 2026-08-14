package br.com.digitado.web.rest;

import static br.com.digitado.domain.PalavraAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Palavra;
import br.com.digitado.repository.PalavraRepository;
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
 * Integration tests for the {@link PalavraResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
// Curadoria de palavras é restrita a ADMIN - os ITs de CRUD rodam como admin
@WithMockUser(authorities = "ROLE_ADMIN")
class PalavraResourceIT {

    private static final String DEFAULT_TEXTO = "AAAAAAAAAA";
    private static final String UPDATED_TEXTO = "BBBBBBBBBB";

    private static final String DEFAULT_CATEGORIA = "AAAAAAAAAA";
    private static final String UPDATED_CATEGORIA = "BBBBBBBBBB";

    private static final String DEFAULT_IDIOMA = "AAAAAAAAAA";
    private static final String UPDATED_IDIOMA = "BBBBBBBBBB";

    private static final Boolean DEFAULT_POSSUI_ACENTO = false;
    private static final Boolean UPDATED_POSSUI_ACENTO = true;

    private static final Boolean DEFAULT_ATIVA = false;
    private static final Boolean UPDATED_ATIVA = true;

    private static final String ENTITY_API_URL = "/api/palavras";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PalavraRepository palavraRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restPalavraMockMvc;

    private Palavra palavra;

    private Palavra insertedPalavra;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Palavra createEntity() {
        return new Palavra()
            .texto(DEFAULT_TEXTO)
            .categoria(DEFAULT_CATEGORIA)
            .idioma(DEFAULT_IDIOMA)
            .possuiAcento(DEFAULT_POSSUI_ACENTO)
            .ativa(DEFAULT_ATIVA);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Palavra createUpdatedEntity() {
        return new Palavra()
            .texto(UPDATED_TEXTO)
            .categoria(UPDATED_CATEGORIA)
            .idioma(UPDATED_IDIOMA)
            .possuiAcento(UPDATED_POSSUI_ACENTO)
            .ativa(UPDATED_ATIVA);
    }

    @BeforeEach
    void initTest() {
        palavra = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPalavra != null) {
            palavraRepository.delete(insertedPalavra);
            insertedPalavra = null;
        }
    }

    @Test
    @Transactional
    void createPalavra() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Palavra
        var returnedPalavra = om.readValue(
            restPalavraMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(palavra)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Palavra.class
        );

        // Validate the Palavra in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertPalavraUpdatableFieldsEquals(returnedPalavra, getPersistedPalavra(returnedPalavra));

        insertedPalavra = returnedPalavra;
    }

    @Test
    @Transactional
    void createPalavraWithExistingId() throws Exception {
        // Create the Palavra with an existing ID
        palavra.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPalavraMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(palavra)))
            .andExpect(status().isBadRequest());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkTextoIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        palavra.setTexto(null);

        // Create the Palavra, which fails.

        restPalavraMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(palavra)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    // O teste checkDificuldadeIsRequired foi removido: dificuldade deixou de ser
    // campo persistido - agora é calculada pela taxa de acerto da palavra

    @Test
    @Transactional
    void getAllPalavras() throws Exception {
        // Initialize the database
        insertedPalavra = palavraRepository.saveAndFlush(palavra);

        // Dificuldade calculada provisória (id % 3) - depende do id gerado
        String dificuldadeEsperada = insertedPalavra.getDificuldade().toString();

        // Get all the palavraList
        restPalavraMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(palavra.getId().intValue())))
            .andExpect(jsonPath("$.[*].texto").value(hasItem(DEFAULT_TEXTO)))
            .andExpect(jsonPath("$.[*].dificuldade").value(hasItem(dificuldadeEsperada)))
            .andExpect(jsonPath("$.[*].categoria").value(hasItem(DEFAULT_CATEGORIA)))
            .andExpect(jsonPath("$.[*].idioma").value(hasItem(DEFAULT_IDIOMA)))
            .andExpect(jsonPath("$.[*].possuiAcento").value(hasItem(DEFAULT_POSSUI_ACENTO)))
            .andExpect(jsonPath("$.[*].ativa").value(hasItem(DEFAULT_ATIVA)));
    }

    @Test
    @Transactional
    void getPalavra() throws Exception {
        // Initialize the database
        insertedPalavra = palavraRepository.saveAndFlush(palavra);

        // Palavra sem tentativas: a dificuldade calculada é provisória e depende do id
        // (id % 3 → FACIL/MEDIO/DIFICIL), igual ao getter Palavra.getDificuldade()
        String dificuldadeEsperada = insertedPalavra.getDificuldade().toString();

        // Get the palavra
        restPalavraMockMvc
            .perform(get(ENTITY_API_URL_ID, palavra.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(palavra.getId().intValue()))
            .andExpect(jsonPath("$.texto").value(DEFAULT_TEXTO))
            .andExpect(jsonPath("$.dificuldade").value(dificuldadeEsperada))
            .andExpect(jsonPath("$.categoria").value(DEFAULT_CATEGORIA))
            .andExpect(jsonPath("$.idioma").value(DEFAULT_IDIOMA))
            .andExpect(jsonPath("$.possuiAcento").value(DEFAULT_POSSUI_ACENTO))
            .andExpect(jsonPath("$.ativa").value(DEFAULT_ATIVA));
    }

    @Test
    @Transactional
    void getNonExistingPalavra() throws Exception {
        // Get the palavra
        restPalavraMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingPalavra() throws Exception {
        // Initialize the database
        insertedPalavra = palavraRepository.saveAndFlush(palavra);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the palavra
        Palavra updatedPalavra = palavraRepository.findById(palavra.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedPalavra are not directly saved in db
        em.detach(updatedPalavra);
        updatedPalavra
            .texto(UPDATED_TEXTO)
            .categoria(UPDATED_CATEGORIA)
            .idioma(UPDATED_IDIOMA)
            .possuiAcento(UPDATED_POSSUI_ACENTO)
            .ativa(UPDATED_ATIVA);

        restPalavraMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedPalavra.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedPalavra))
            )
            .andExpect(status().isOk());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPalavraToMatchAllProperties(updatedPalavra);
    }

    @Test
    @Transactional
    void putNonExistingPalavra() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        palavra.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPalavraMockMvc
            .perform(put(ENTITY_API_URL_ID, palavra.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(palavra)))
            .andExpect(status().isBadRequest());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchPalavra() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        palavra.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPalavraMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(palavra))
            )
            .andExpect(status().isBadRequest());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamPalavra() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        palavra.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPalavraMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(palavra)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdatePalavraWithPatch() throws Exception {
        // Initialize the database
        insertedPalavra = palavraRepository.saveAndFlush(palavra);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the palavra using partial update
        Palavra partialUpdatedPalavra = new Palavra();
        partialUpdatedPalavra.setId(palavra.getId());

        partialUpdatedPalavra.categoria(UPDATED_CATEGORIA).ativa(UPDATED_ATIVA);

        restPalavraMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPalavra.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPalavra))
            )
            .andExpect(status().isOk());

        // Validate the Palavra in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPalavraUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedPalavra, palavra), getPersistedPalavra(palavra));
    }

    @Test
    @Transactional
    void fullUpdatePalavraWithPatch() throws Exception {
        // Initialize the database
        insertedPalavra = palavraRepository.saveAndFlush(palavra);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the palavra using partial update
        Palavra partialUpdatedPalavra = new Palavra();
        partialUpdatedPalavra.setId(palavra.getId());

        partialUpdatedPalavra
            .texto(UPDATED_TEXTO)
            .categoria(UPDATED_CATEGORIA)
            .idioma(UPDATED_IDIOMA)
            .possuiAcento(UPDATED_POSSUI_ACENTO)
            .ativa(UPDATED_ATIVA);

        restPalavraMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPalavra.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPalavra))
            )
            .andExpect(status().isOk());

        // Validate the Palavra in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPalavraUpdatableFieldsEquals(partialUpdatedPalavra, getPersistedPalavra(partialUpdatedPalavra));
    }

    @Test
    @Transactional
    void patchNonExistingPalavra() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        palavra.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPalavraMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, palavra.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(palavra))
            )
            .andExpect(status().isBadRequest());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchPalavra() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        palavra.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPalavraMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(palavra))
            )
            .andExpect(status().isBadRequest());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamPalavra() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        palavra.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPalavraMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(palavra)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Palavra in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deletePalavra() throws Exception {
        // Initialize the database
        insertedPalavra = palavraRepository.saveAndFlush(palavra);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the palavra
        restPalavraMockMvc
            .perform(delete(ENTITY_API_URL_ID, palavra.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return palavraRepository.count();
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

    protected Palavra getPersistedPalavra(Palavra palavra) {
        return palavraRepository.findById(palavra.getId()).orElseThrow();
    }

    protected void assertPersistedPalavraToMatchAllProperties(Palavra expectedPalavra) {
        assertPalavraAllPropertiesEquals(expectedPalavra, getPersistedPalavra(expectedPalavra));
    }

    protected void assertPersistedPalavraToMatchUpdatableProperties(Palavra expectedPalavra) {
        assertPalavraAllUpdatablePropertiesEquals(expectedPalavra, getPersistedPalavra(expectedPalavra));
    }
}
