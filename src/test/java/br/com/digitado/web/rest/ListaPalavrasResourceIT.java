package br.com.digitado.web.rest;

import static br.com.digitado.domain.ListaPalavrasAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.ListaPalavras;
import br.com.digitado.repository.ListaPalavrasRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link ListaPalavrasResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class ListaPalavrasResourceIT {

    private static final String DEFAULT_NOME_LISTA = "AAAAAAAAAA";
    private static final String UPDATED_NOME_LISTA = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRICAO = "AAAAAAAAAA";
    private static final String UPDATED_DESCRICAO = "BBBBBBBBBB";

    private static final Boolean DEFAULT_ATIVO = false;
    private static final Boolean UPDATED_ATIVO = true;

    private static final String ENTITY_API_URL = "/api/lista-palavras";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ListaPalavrasRepository listaPalavrasRepository;

    @Mock
    private ListaPalavrasRepository listaPalavrasRepositoryMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restListaPalavrasMockMvc;

    private ListaPalavras listaPalavras;

    private ListaPalavras insertedListaPalavras;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ListaPalavras createEntity() {
        return new ListaPalavras().nomeLista(DEFAULT_NOME_LISTA).descricao(DEFAULT_DESCRICAO).ativo(DEFAULT_ATIVO);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ListaPalavras createUpdatedEntity() {
        return new ListaPalavras().nomeLista(UPDATED_NOME_LISTA).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);
    }

    @BeforeEach
    void initTest() {
        listaPalavras = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedListaPalavras != null) {
            listaPalavrasRepository.delete(insertedListaPalavras);
            insertedListaPalavras = null;
        }
    }

    @Test
    @Transactional
    void createListaPalavras() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ListaPalavras
        var returnedListaPalavras = om.readValue(
            restListaPalavrasMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(listaPalavras)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ListaPalavras.class
        );

        // Validate the ListaPalavras in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertListaPalavrasUpdatableFieldsEquals(returnedListaPalavras, getPersistedListaPalavras(returnedListaPalavras));

        insertedListaPalavras = returnedListaPalavras;
    }

    @Test
    @Transactional
    void createListaPalavrasWithExistingId() throws Exception {
        // Create the ListaPalavras with an existing ID
        listaPalavras.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restListaPalavrasMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(listaPalavras)))
            .andExpect(status().isBadRequest());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNomeListaIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        listaPalavras.setNomeLista(null);

        // Create the ListaPalavras, which fails.

        restListaPalavrasMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(listaPalavras)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllListaPalavras() throws Exception {
        // Initialize the database
        insertedListaPalavras = listaPalavrasRepository.saveAndFlush(listaPalavras);

        // Get all the listaPalavrasList
        restListaPalavrasMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(listaPalavras.getId().intValue())))
            .andExpect(jsonPath("$.[*].nomeLista").value(hasItem(DEFAULT_NOME_LISTA)))
            .andExpect(jsonPath("$.[*].descricao").value(hasItem(DEFAULT_DESCRICAO)))
            .andExpect(jsonPath("$.[*].ativo").value(hasItem(DEFAULT_ATIVO)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllListaPalavrasWithEagerRelationshipsIsEnabled() throws Exception {
        when(listaPalavrasRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restListaPalavrasMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(listaPalavrasRepositoryMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllListaPalavrasWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(listaPalavrasRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restListaPalavrasMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(listaPalavrasRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getListaPalavras() throws Exception {
        // Initialize the database
        insertedListaPalavras = listaPalavrasRepository.saveAndFlush(listaPalavras);

        // Get the listaPalavras
        restListaPalavrasMockMvc
            .perform(get(ENTITY_API_URL_ID, listaPalavras.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(listaPalavras.getId().intValue()))
            .andExpect(jsonPath("$.nomeLista").value(DEFAULT_NOME_LISTA))
            .andExpect(jsonPath("$.descricao").value(DEFAULT_DESCRICAO))
            .andExpect(jsonPath("$.ativo").value(DEFAULT_ATIVO));
    }

    @Test
    @Transactional
    void getNonExistingListaPalavras() throws Exception {
        // Get the listaPalavras
        restListaPalavrasMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingListaPalavras() throws Exception {
        // Initialize the database
        insertedListaPalavras = listaPalavrasRepository.saveAndFlush(listaPalavras);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the listaPalavras
        ListaPalavras updatedListaPalavras = listaPalavrasRepository.findById(listaPalavras.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedListaPalavras are not directly saved in db
        em.detach(updatedListaPalavras);
        updatedListaPalavras.nomeLista(UPDATED_NOME_LISTA).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);

        restListaPalavrasMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedListaPalavras.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedListaPalavras))
            )
            .andExpect(status().isOk());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedListaPalavrasToMatchAllProperties(updatedListaPalavras);
    }

    @Test
    @Transactional
    void putNonExistingListaPalavras() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        listaPalavras.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restListaPalavrasMockMvc
            .perform(
                put(ENTITY_API_URL_ID, listaPalavras.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(listaPalavras))
            )
            .andExpect(status().isBadRequest());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchListaPalavras() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        listaPalavras.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restListaPalavrasMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(listaPalavras))
            )
            .andExpect(status().isBadRequest());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamListaPalavras() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        listaPalavras.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restListaPalavrasMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(listaPalavras)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateListaPalavrasWithPatch() throws Exception {
        // Initialize the database
        insertedListaPalavras = listaPalavrasRepository.saveAndFlush(listaPalavras);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the listaPalavras using partial update
        ListaPalavras partialUpdatedListaPalavras = new ListaPalavras();
        partialUpdatedListaPalavras.setId(listaPalavras.getId());

        restListaPalavrasMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedListaPalavras.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedListaPalavras))
            )
            .andExpect(status().isOk());

        // Validate the ListaPalavras in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertListaPalavrasUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedListaPalavras, listaPalavras),
            getPersistedListaPalavras(listaPalavras)
        );
    }

    @Test
    @Transactional
    void fullUpdateListaPalavrasWithPatch() throws Exception {
        // Initialize the database
        insertedListaPalavras = listaPalavrasRepository.saveAndFlush(listaPalavras);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the listaPalavras using partial update
        ListaPalavras partialUpdatedListaPalavras = new ListaPalavras();
        partialUpdatedListaPalavras.setId(listaPalavras.getId());

        partialUpdatedListaPalavras.nomeLista(UPDATED_NOME_LISTA).descricao(UPDATED_DESCRICAO).ativo(UPDATED_ATIVO);

        restListaPalavrasMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedListaPalavras.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedListaPalavras))
            )
            .andExpect(status().isOk());

        // Validate the ListaPalavras in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertListaPalavrasUpdatableFieldsEquals(partialUpdatedListaPalavras, getPersistedListaPalavras(partialUpdatedListaPalavras));
    }

    @Test
    @Transactional
    void patchNonExistingListaPalavras() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        listaPalavras.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restListaPalavrasMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, listaPalavras.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(listaPalavras))
            )
            .andExpect(status().isBadRequest());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchListaPalavras() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        listaPalavras.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restListaPalavrasMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(listaPalavras))
            )
            .andExpect(status().isBadRequest());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamListaPalavras() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        listaPalavras.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restListaPalavrasMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(listaPalavras)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ListaPalavras in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteListaPalavras() throws Exception {
        // Initialize the database
        insertedListaPalavras = listaPalavrasRepository.saveAndFlush(listaPalavras);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the listaPalavras
        restListaPalavrasMockMvc
            .perform(delete(ENTITY_API_URL_ID, listaPalavras.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return listaPalavrasRepository.count();
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

    protected ListaPalavras getPersistedListaPalavras(ListaPalavras listaPalavras) {
        return listaPalavrasRepository.findById(listaPalavras.getId()).orElseThrow();
    }

    protected void assertPersistedListaPalavrasToMatchAllProperties(ListaPalavras expectedListaPalavras) {
        assertListaPalavrasAllPropertiesEquals(expectedListaPalavras, getPersistedListaPalavras(expectedListaPalavras));
    }

    protected void assertPersistedListaPalavrasToMatchUpdatableProperties(ListaPalavras expectedListaPalavras) {
        assertListaPalavrasAllUpdatablePropertiesEquals(expectedListaPalavras, getPersistedListaPalavras(expectedListaPalavras));
    }
}
