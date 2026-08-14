package br.com.digitado.web.rest;

import br.com.digitado.domain.Ranking;
import br.com.digitado.repository.RankingRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link br.com.digitado.domain.Ranking}.
 */
@RestController
@RequestMapping("/api/rankings")
@Transactional
public class RankingResource {

    private static final Logger LOG = LoggerFactory.getLogger(RankingResource.class);

    private static final String ENTITY_NAME = "ranking";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final RankingRepository rankingRepository;

    public RankingResource(RankingRepository rankingRepository) {
        this.rankingRepository = rankingRepository;
    }

    /**
     * {@code POST  /rankings} : Create a new ranking.
     *
     * @param ranking the ranking to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new ranking, or with status {@code 400 (Bad Request)} if the ranking has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @Secured(AuthoritiesConstants.ADMIN)
    @PostMapping("")
    public ResponseEntity<Ranking> createRanking(@RequestBody Ranking ranking) throws URISyntaxException {
        LOG.debug("REST request to save Ranking : {}", ranking);
        if (ranking.getId() != null) {
            throw new BadRequestAlertException("A new ranking cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ranking = rankingRepository.save(ranking);
        return ResponseEntity.created(new URI("/api/rankings/" + ranking.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ranking.getId().toString()))
            .body(ranking);
    }

    /**
     * {@code PUT  /rankings/:id} : Updates an existing ranking.
     *
     * @param id the id of the ranking to save.
     * @param ranking the ranking to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ranking,
     * or with status {@code 400 (Bad Request)} if the ranking is not valid,
     * or with status {@code 500 (Internal Server Error)} if the ranking couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @Secured(AuthoritiesConstants.ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<Ranking> updateRanking(@PathVariable(value = "id", required = false) final Long id, @RequestBody Ranking ranking)
        throws URISyntaxException {
        LOG.debug("REST request to update Ranking : {}, {}", id, ranking);
        if (ranking.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ranking.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!rankingRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        ranking = rankingRepository.save(ranking);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, ranking.getId().toString()))
            .body(ranking);
    }

    /**
     * {@code PATCH  /rankings/:id} : Partial updates given fields of an existing ranking, field will ignore if it is null
     *
     * @param id the id of the ranking to save.
     * @param ranking the ranking to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated ranking,
     * or with status {@code 400 (Bad Request)} if the ranking is not valid,
     * or with status {@code 404 (Not Found)} if the ranking is not found,
     * or with status {@code 500 (Internal Server Error)} if the ranking couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @Secured(AuthoritiesConstants.ADMIN)
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Ranking> partialUpdateRanking(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody Ranking ranking
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Ranking partially : {}, {}", id, ranking);
        if (ranking.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, ranking.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!rankingRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Ranking> result = rankingRepository
            .findById(ranking.getId())
            .map(existingRanking -> {
                if (ranking.getPosicao() != null) {
                    existingRanking.setPosicao(ranking.getPosicao());
                }
                if (ranking.getPontuacaoTotal() != null) {
                    existingRanking.setPontuacaoTotal(ranking.getPontuacaoTotal());
                }
                if (ranking.getUltimaAtualizacao() != null) {
                    existingRanking.setUltimaAtualizacao(ranking.getUltimaAtualizacao());
                }

                return existingRanking;
            })
            .map(rankingRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, ranking.getId().toString())
        );
    }

    /**
     * {@code GET  /rankings} : get all the rankings.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of rankings in body.
     */
    // Entidade Ranking (legada, por sala) exposta só a admin - o ranking visível
    // aos jogadores é o /api/ranking-mundial, que já filtra os dados públicos
    @Secured(AuthoritiesConstants.ADMIN)
    @GetMapping("")
    public List<Ranking> getAllRankings() {
        LOG.debug("REST request to get all Rankings");
        return rankingRepository.findAll();
    }

    /**
     * {@code GET  /rankings/:id} : get the "id" ranking.
     *
     * @param id the id of the ranking to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the ranking, or with status {@code 404 (Not Found)}.
     */
    @Secured(AuthoritiesConstants.ADMIN)
    @GetMapping("/{id}")
    public ResponseEntity<Ranking> getRanking(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Ranking : {}", id);
        Optional<Ranking> ranking = rankingRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(ranking);
    }

    /**
     * {@code DELETE  /rankings/:id} : delete the "id" ranking.
     *
     * @param id the id of the ranking to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @Secured(AuthoritiesConstants.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRanking(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Ranking : {}", id);
        rankingRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
