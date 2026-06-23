package br.com.digitado.web.rest;

import br.com.digitado.domain.ListaPalavras;
import br.com.digitado.repository.ListaPalavrasRepository;
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
 * REST controller for managing {@link br.com.digitado.domain.ListaPalavras}.
 */
@RestController
@RequestMapping("/api/lista-palavras")
@Transactional
public class ListaPalavrasResource {

    private static final Logger LOG = LoggerFactory.getLogger(ListaPalavrasResource.class);

    private static final String ENTITY_NAME = "listaPalavras";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ListaPalavrasRepository listaPalavrasRepository;

    public ListaPalavrasResource(ListaPalavrasRepository listaPalavrasRepository) {
        this.listaPalavrasRepository = listaPalavrasRepository;
    }

    /**
     * {@code POST  /lista-palavras} : Create a new listaPalavras.
     *
     * @param listaPalavras the listaPalavras to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new listaPalavras, or with status {@code 400 (Bad Request)} if the listaPalavras has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ListaPalavras> createListaPalavras(@Valid @RequestBody ListaPalavras listaPalavras) throws URISyntaxException {
        LOG.debug("REST request to save ListaPalavras : {}", listaPalavras);
        if (listaPalavras.getId() != null) {
            throw new BadRequestAlertException("A new listaPalavras cannot already have an ID", ENTITY_NAME, "idexists");
        }
        listaPalavras = listaPalavrasRepository.save(listaPalavras);
        return ResponseEntity.created(new URI("/api/lista-palavras/" + listaPalavras.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, listaPalavras.getId().toString()))
            .body(listaPalavras);
    }

    /**
     * {@code PUT  /lista-palavras/:id} : Updates an existing listaPalavras.
     *
     * @param id the id of the listaPalavras to save.
     * @param listaPalavras the listaPalavras to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated listaPalavras,
     * or with status {@code 400 (Bad Request)} if the listaPalavras is not valid,
     * or with status {@code 500 (Internal Server Error)} if the listaPalavras couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ListaPalavras> updateListaPalavras(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody ListaPalavras listaPalavras
    ) throws URISyntaxException {
        LOG.debug("REST request to update ListaPalavras : {}, {}", id, listaPalavras);
        if (listaPalavras.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, listaPalavras.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!listaPalavrasRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        listaPalavras = listaPalavrasRepository.save(listaPalavras);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, listaPalavras.getId().toString()))
            .body(listaPalavras);
    }

    /**
     * {@code PATCH  /lista-palavras/:id} : Partial updates given fields of an existing listaPalavras, field will ignore if it is null
     *
     * @param id the id of the listaPalavras to save.
     * @param listaPalavras the listaPalavras to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated listaPalavras,
     * or with status {@code 400 (Bad Request)} if the listaPalavras is not valid,
     * or with status {@code 404 (Not Found)} if the listaPalavras is not found,
     * or with status {@code 500 (Internal Server Error)} if the listaPalavras couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ListaPalavras> partialUpdateListaPalavras(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody ListaPalavras listaPalavras
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ListaPalavras partially : {}, {}", id, listaPalavras);
        if (listaPalavras.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, listaPalavras.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!listaPalavrasRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ListaPalavras> result = listaPalavrasRepository
            .findById(listaPalavras.getId())
            .map(existingListaPalavras -> {
                if (listaPalavras.getNomeLista() != null) {
                    existingListaPalavras.setNomeLista(listaPalavras.getNomeLista());
                }
                if (listaPalavras.getDescricao() != null) {
                    existingListaPalavras.setDescricao(listaPalavras.getDescricao());
                }
                if (listaPalavras.getAtivo() != null) {
                    existingListaPalavras.setAtivo(listaPalavras.getAtivo());
                }

                return existingListaPalavras;
            })
            .map(listaPalavrasRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, listaPalavras.getId().toString())
        );
    }

    /**
     * {@code GET  /lista-palavras} : get all the listaPalavras.
     *
     * @param eagerload flag to eager load entities from relationships (This is applicable for many-to-many).
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of listaPalavras in body.
     */
    @GetMapping("")
    public List<ListaPalavras> getAllListaPalavras(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all ListaPalavras");
        if (eagerload) {
            return listaPalavrasRepository.findAllWithEagerRelationships();
        } else {
            return listaPalavrasRepository.findAll();
        }
    }

    /**
     * {@code GET  /lista-palavras/:id} : get the "id" listaPalavras.
     *
     * @param id the id of the listaPalavras to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the listaPalavras, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ListaPalavras> getListaPalavras(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ListaPalavras : {}", id);
        Optional<ListaPalavras> listaPalavras = listaPalavrasRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(listaPalavras);
    }

    /**
     * {@code DELETE  /lista-palavras/:id} : delete the "id" listaPalavras.
     *
     * @param id the id of the listaPalavras to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteListaPalavras(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ListaPalavras : {}", id);
        listaPalavrasRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
