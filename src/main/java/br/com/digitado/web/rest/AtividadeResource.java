package br.com.digitado.web.rest;

import br.com.digitado.domain.Atividade;
import br.com.digitado.repository.AtividadeRepository;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link br.com.digitado.domain.Atividade}.
 */
@RestController
@RequestMapping("/api/atividades")
@Transactional
public class AtividadeResource {

    private static final Logger LOG = LoggerFactory.getLogger(AtividadeResource.class);

    private static final String ENTITY_NAME = "atividade";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AtividadeRepository atividadeRepository;

    public AtividadeResource(AtividadeRepository atividadeRepository) {
        this.atividadeRepository = atividadeRepository;
    }

    /**
     * {@code POST  /atividades} : Create a new atividade.
     *
     * @param atividade the atividade to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new atividade, or with status {@code 400 (Bad Request)} if the atividade has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Atividade> createAtividade(@Valid @RequestBody Atividade atividade) throws URISyntaxException {
        LOG.debug("REST request to save Atividade : {}", atividade);
        if (atividade.getId() != null) {
            throw new BadRequestAlertException("A new atividade cannot already have an ID", ENTITY_NAME, "idexists");
        }
        atividade = atividadeRepository.save(atividade);
        return ResponseEntity.created(new URI("/api/atividades/" + atividade.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, atividade.getId().toString()))
            .body(atividade);
    }

    /**
     * {@code PUT  /atividades/:id} : Updates an existing atividade.
     *
     * @param id the id of the atividade to save.
     * @param atividade the atividade to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated atividade,
     * or with status {@code 400 (Bad Request)} if the atividade is not valid,
     * or with status {@code 500 (Internal Server Error)} if the atividade couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Atividade> updateAtividade(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Atividade atividade
    ) throws URISyntaxException {
        LOG.debug("REST request to update Atividade : {}, {}", id, atividade);
        if (atividade.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, atividade.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!atividadeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        atividade = atividadeRepository.save(atividade);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, atividade.getId().toString()))
            .body(atividade);
    }

    /**
     * {@code PATCH  /atividades/:id} : Partial updates given fields of an existing atividade, field will ignore if it is null
     *
     * @param id the id of the atividade to save.
     * @param atividade the atividade to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated atividade,
     * or with status {@code 400 (Bad Request)} if the atividade is not valid,
     * or with status {@code 404 (Not Found)} if the atividade is not found,
     * or with status {@code 500 (Internal Server Error)} if the atividade couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Atividade> partialUpdateAtividade(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Atividade atividade
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Atividade partially : {}, {}", id, atividade);
        if (atividade.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, atividade.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!atividadeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Atividade> result = atividadeRepository
            .findById(atividade.getId())
            .map(existingAtividade -> {
                if (atividade.getTitulo() != null) {
                    existingAtividade.setTitulo(atividade.getTitulo());
                }
                if (atividade.getModo() != null) {
                    existingAtividade.setModo(atividade.getModo());
                }
                if (atividade.getDataInicio() != null) {
                    existingAtividade.setDataInicio(atividade.getDataInicio());
                }
                if (atividade.getDataFim() != null) {
                    existingAtividade.setDataFim(atividade.getDataFim());
                }
                if (atividade.getTempoLimite() != null) {
                    existingAtividade.setTempoLimite(atividade.getTempoLimite());
                }
                if (atividade.getStatus() != null) {
                    existingAtividade.setStatus(atividade.getStatus());
                }

                return existingAtividade;
            })
            .map(atividadeRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, atividade.getId().toString())
        );
    }

    /**
     * {@code GET  /atividades} : get all the atividades.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of atividades in body.
     */
    @GetMapping("")
    public List<Atividade> getAllAtividades() {
        LOG.debug("REST request to get all Atividades");
        return atividadeRepository.findAll();
    }

    /**
     * {@code GET  /atividades/:id} : get the "id" atividade.
     *
     * @param id the id of the atividade to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the atividade, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Atividade> getAtividade(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Atividade : {}", id);
        Optional<Atividade> atividade = atividadeRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(atividade);
    }

    /**
     * {@code DELETE  /atividades/:id} : delete the "id" atividade.
     *
     * @param id the id of the atividade to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAtividade(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Atividade : {}", id);
        atividadeRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
