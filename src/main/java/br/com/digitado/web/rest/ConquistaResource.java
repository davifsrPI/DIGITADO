package br.com.digitado.web.rest;

import br.com.digitado.domain.Conquista;
import br.com.digitado.repository.ConquistaRepository;
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
 * REST controller for managing {@link br.com.digitado.domain.Conquista}.
 */
@RestController
@RequestMapping("/api/conquistas")
@Transactional
public class ConquistaResource {

    private static final Logger LOG = LoggerFactory.getLogger(ConquistaResource.class);

    private static final String ENTITY_NAME = "conquista";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ConquistaRepository conquistaRepository;

    public ConquistaResource(ConquistaRepository conquistaRepository) {
        this.conquistaRepository = conquistaRepository;
    }

    /**
     * {@code POST  /conquistas} : Create a new conquista.
     *
     * @param conquista the conquista to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new conquista, or with status {@code 400 (Bad Request)} if the conquista has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Conquista> createConquista(@Valid @RequestBody Conquista conquista) throws URISyntaxException {
        LOG.debug("REST request to save Conquista : {}", conquista);
        if (conquista.getId() != null) {
            throw new BadRequestAlertException("A new conquista cannot already have an ID", ENTITY_NAME, "idexists");
        }
        conquista = conquistaRepository.save(conquista);
        return ResponseEntity.created(new URI("/api/conquistas/" + conquista.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, conquista.getId().toString()))
            .body(conquista);
    }

    /**
     * {@code PUT  /conquistas/:id} : Updates an existing conquista.
     *
     * @param id the id of the conquista to save.
     * @param conquista the conquista to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated conquista,
     * or with status {@code 400 (Bad Request)} if the conquista is not valid,
     * or with status {@code 500 (Internal Server Error)} if the conquista couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Conquista> updateConquista(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Conquista conquista
    ) throws URISyntaxException {
        LOG.debug("REST request to update Conquista : {}, {}", id, conquista);
        if (conquista.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, conquista.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!conquistaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        conquista = conquistaRepository.save(conquista);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, conquista.getId().toString()))
            .body(conquista);
    }

    /**
     * {@code PATCH  /conquistas/:id} : Partial updates given fields of an existing conquista, field will ignore if it is null
     *
     * @param id the id of the conquista to save.
     * @param conquista the conquista to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated conquista,
     * or with status {@code 400 (Bad Request)} if the conquista is not valid,
     * or with status {@code 404 (Not Found)} if the conquista is not found,
     * or with status {@code 500 (Internal Server Error)} if the conquista couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Conquista> partialUpdateConquista(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Conquista conquista
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Conquista partially : {}, {}", id, conquista);
        if (conquista.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, conquista.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!conquistaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Conquista> result = conquistaRepository
            .findById(conquista.getId())
            .map(existingConquista -> {
                if (conquista.getNome() != null) {
                    existingConquista.setNome(conquista.getNome());
                }
                if (conquista.getDescricao() != null) {
                    existingConquista.setDescricao(conquista.getDescricao());
                }
                if (conquista.getXpRecompensa() != null) {
                    existingConquista.setXpRecompensa(conquista.getXpRecompensa());
                }

                return existingConquista;
            })
            .map(conquistaRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, conquista.getId().toString())
        );
    }

    /**
     * {@code GET  /conquistas} : get all the conquistas.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of conquistas in body.
     */
    @GetMapping("")
    public List<Conquista> getAllConquistas() {
        LOG.debug("REST request to get all Conquistas");
        return conquistaRepository.findAll();
    }

    /**
     * {@code GET  /conquistas/:id} : get the "id" conquista.
     *
     * @param id the id of the conquista to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the conquista, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Conquista> getConquista(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Conquista : {}", id);
        Optional<Conquista> conquista = conquistaRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(conquista);
    }

    /**
     * {@code DELETE  /conquistas/:id} : delete the "id" conquista.
     *
     * @param id the id of the conquista to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConquista(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Conquista : {}", id);
        conquistaRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
