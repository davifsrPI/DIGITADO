package br.com.digitado.web.rest;

import static br.com.digitado.domain.RankingAsserts.*;
import static br.com.digitado.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Ranking;
import br.com.digitado.repository.RankingRepository;
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
 * Integration tests for the {@link RankingResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class RankingResourceIT {

    private static final Integer DEFAULT_POSICAO = 1;
    private static final Integer UPDATED_POSICAO = 2;

    private static final Integer DEFAULT_PONTUACAO_TOTAL = 1;
    private static final Integer UPDATED_PONTUACAO_TOTAL = 2;

    private static final Instant DEFAULT_ULTIMA_ATUALIZACAO = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_ULTIMA_ATUALIZACAO = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/rankings";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restRankingMockMvc;

    private Ranking ranking;

    private Ranking insertedRanking;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Ranking createEntity() {
        return new Ranking().posicao(DEFAULT_POSICAO).pontuacaoTotal(DEFAULT_PONTUACAO_TOTAL).ultimaAtualizacao(DEFAULT_ULTIMA_ATUALIZACAO);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Ranking createUpdatedEntity() {
        return new Ranking().posicao(UPDATED_POSICAO).pontuacaoTotal(UPDATED_PONTUACAO_TOTAL).ultimaAtualizacao(UPDATED_ULTIMA_ATUALIZACAO);
    }

    @BeforeEach
    void initTest() {
        ranking = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedRanking != null) {
            rankingRepository.delete(insertedRanking);
            insertedRanking = null;
        }
    }

    @Test
    @Transactional
    void createRanking() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Ranking
        var returnedRanking = om.readValue(
            restRankingMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ranking)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Ranking.class
        );

        // Validate the Ranking in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertRankingUpdatableFieldsEquals(returnedRanking, getPersistedRanking(returnedRanking));

        insertedRanking = returnedRanking;
    }

    @Test
    @Transactional
    void createRankingWithExistingId() throws Exception {
        // Create the Ranking with an existing ID
        ranking.setId(1L);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restRankingMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ranking)))
            .andExpect(status().isBadRequest());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllRankings() throws Exception {
        // Initialize the database
        insertedRanking = rankingRepository.saveAndFlush(ranking);

        // Get all the rankingList
        restRankingMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(ranking.getId().intValue())))
            .andExpect(jsonPath("$.[*].posicao").value(hasItem(DEFAULT_POSICAO)))
            .andExpect(jsonPath("$.[*].pontuacaoTotal").value(hasItem(DEFAULT_PONTUACAO_TOTAL)))
            .andExpect(jsonPath("$.[*].ultimaAtualizacao").value(hasItem(DEFAULT_ULTIMA_ATUALIZACAO.toString())));
    }

    @Test
    @Transactional
    void getRanking() throws Exception {
        // Initialize the database
        insertedRanking = rankingRepository.saveAndFlush(ranking);

        // Get the ranking
        restRankingMockMvc
            .perform(get(ENTITY_API_URL_ID, ranking.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(ranking.getId().intValue()))
            .andExpect(jsonPath("$.posicao").value(DEFAULT_POSICAO))
            .andExpect(jsonPath("$.pontuacaoTotal").value(DEFAULT_PONTUACAO_TOTAL))
            .andExpect(jsonPath("$.ultimaAtualizacao").value(DEFAULT_ULTIMA_ATUALIZACAO.toString()));
    }

    @Test
    @Transactional
    void getNonExistingRanking() throws Exception {
        // Get the ranking
        restRankingMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingRanking() throws Exception {
        // Initialize the database
        insertedRanking = rankingRepository.saveAndFlush(ranking);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ranking
        Ranking updatedRanking = rankingRepository.findById(ranking.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedRanking are not directly saved in db
        em.detach(updatedRanking);
        updatedRanking.posicao(UPDATED_POSICAO).pontuacaoTotal(UPDATED_PONTUACAO_TOTAL).ultimaAtualizacao(UPDATED_ULTIMA_ATUALIZACAO);

        restRankingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedRanking.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedRanking))
            )
            .andExpect(status().isOk());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedRankingToMatchAllProperties(updatedRanking);
    }

    @Test
    @Transactional
    void putNonExistingRanking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ranking.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRankingMockMvc
            .perform(put(ENTITY_API_URL_ID, ranking.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ranking)))
            .andExpect(status().isBadRequest());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchRanking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ranking.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRankingMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(ranking))
            )
            .andExpect(status().isBadRequest());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamRanking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ranking.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRankingMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(ranking)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateRankingWithPatch() throws Exception {
        // Initialize the database
        insertedRanking = rankingRepository.saveAndFlush(ranking);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ranking using partial update
        Ranking partialUpdatedRanking = new Ranking();
        partialUpdatedRanking.setId(ranking.getId());

        partialUpdatedRanking.posicao(UPDATED_POSICAO);

        restRankingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRanking.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedRanking))
            )
            .andExpect(status().isOk());

        // Validate the Ranking in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRankingUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedRanking, ranking), getPersistedRanking(ranking));
    }

    @Test
    @Transactional
    void fullUpdateRankingWithPatch() throws Exception {
        // Initialize the database
        insertedRanking = rankingRepository.saveAndFlush(ranking);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the ranking using partial update
        Ranking partialUpdatedRanking = new Ranking();
        partialUpdatedRanking.setId(ranking.getId());

        partialUpdatedRanking
            .posicao(UPDATED_POSICAO)
            .pontuacaoTotal(UPDATED_PONTUACAO_TOTAL)
            .ultimaAtualizacao(UPDATED_ULTIMA_ATUALIZACAO);

        restRankingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRanking.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedRanking))
            )
            .andExpect(status().isOk());

        // Validate the Ranking in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRankingUpdatableFieldsEquals(partialUpdatedRanking, getPersistedRanking(partialUpdatedRanking));
    }

    @Test
    @Transactional
    void patchNonExistingRanking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ranking.setId(longCount.incrementAndGet());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRankingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, ranking.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(ranking))
            )
            .andExpect(status().isBadRequest());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchRanking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ranking.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRankingMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(ranking))
            )
            .andExpect(status().isBadRequest());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamRanking() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        ranking.setId(longCount.incrementAndGet());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRankingMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(ranking)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Ranking in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteRanking() throws Exception {
        // Initialize the database
        insertedRanking = rankingRepository.saveAndFlush(ranking);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the ranking
        restRankingMockMvc
            .perform(delete(ENTITY_API_URL_ID, ranking.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return rankingRepository.count();
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

    protected Ranking getPersistedRanking(Ranking ranking) {
        return rankingRepository.findById(ranking.getId()).orElseThrow();
    }

    protected void assertPersistedRankingToMatchAllProperties(Ranking expectedRanking) {
        assertRankingAllPropertiesEquals(expectedRanking, getPersistedRanking(expectedRanking));
    }

    protected void assertPersistedRankingToMatchUpdatableProperties(Ranking expectedRanking) {
        assertRankingAllUpdatablePropertiesEquals(expectedRanking, getPersistedRanking(expectedRanking));
    }
}
