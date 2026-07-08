package br.com.digitado.web.rest;

import br.com.digitado.domain.ErroOrtografico;
import br.com.digitado.repository.ErroOrtograficoRepository;
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
 * REST controller for managing {@link br.com.digitado.domain.ErroOrtografico}.
 *
 * Restrito a administradores: são dados pedagógicos (erros de digitação) de
 * TODOS os alunos. Este CRUD genérico não filtra por dono, então liberá-lo a
 * usuários comuns permitiria ler/alterar/apagar erros de qualquer pessoa (IDOR
 * + vazamento de dados pessoais/LGPD). O jogo não usa este endpoint.
 */
@RestController
@RequestMapping("/api/erro-ortograficos")
@Transactional
@Secured(AuthoritiesConstants.ADMIN)
public class ErroOrtograficoResource {

    private static final Logger LOG = LoggerFactory.getLogger(ErroOrtograficoResource.class);

    private static final String ENTITY_NAME = "erroOrtografico";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ErroOrtograficoRepository erroOrtograficoRepository;

    public ErroOrtograficoResource(ErroOrtograficoRepository erroOrtograficoRepository) {
        this.erroOrtograficoRepository = erroOrtograficoRepository;
    }

    /**
     * {@code POST  /erro-ortograficos} : Create a new erroOrtografico.
     *
     * @param erroOrtografico the erroOrtografico to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new erroOrtografico, or with status {@code 400 (Bad Request)} if the erroOrtografico has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ErroOrtografico> createErroOrtografico(@RequestBody ErroOrtografico erroOrtografico) throws URISyntaxException {
        LOG.debug("REST request to save ErroOrtografico : {}", erroOrtografico);
        if (erroOrtografico.getId() != null) {
            throw new BadRequestAlertException("A new erroOrtografico cannot already have an ID", ENTITY_NAME, "idexists");
        }
        erroOrtografico = erroOrtograficoRepository.save(erroOrtografico);
        return ResponseEntity.created(new URI("/api/erro-ortograficos/" + erroOrtografico.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, erroOrtografico.getId().toString()))
            .body(erroOrtografico);
    }

    /**
     * {@code PUT  /erro-ortograficos/:id} : Updates an existing erroOrtografico.
     *
     * @param id the id of the erroOrtografico to save.
     * @param erroOrtografico the erroOrtografico to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated erroOrtografico,
     * or with status {@code 400 (Bad Request)} if the erroOrtografico is not valid,
     * or with status {@code 500 (Internal Server Error)} if the erroOrtografico couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ErroOrtografico> updateErroOrtografico(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody ErroOrtografico erroOrtografico
    ) throws URISyntaxException {
        LOG.debug("REST request to update ErroOrtografico : {}, {}", id, erroOrtografico);
        if (erroOrtografico.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, erroOrtografico.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!erroOrtograficoRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        erroOrtografico = erroOrtograficoRepository.save(erroOrtografico);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, erroOrtografico.getId().toString()))
            .body(erroOrtografico);
    }

    /**
     * {@code PATCH  /erro-ortograficos/:id} : Partial updates given fields of an existing erroOrtografico, field will ignore if it is null
     *
     * @param id the id of the erroOrtografico to save.
     * @param erroOrtografico the erroOrtografico to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated erroOrtografico,
     * or with status {@code 400 (Bad Request)} if the erroOrtografico is not valid,
     * or with status {@code 404 (Not Found)} if the erroOrtografico is not found,
     * or with status {@code 500 (Internal Server Error)} if the erroOrtografico couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<ErroOrtografico> partialUpdateErroOrtografico(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody ErroOrtografico erroOrtografico
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update ErroOrtografico partially : {}, {}", id, erroOrtografico);
        if (erroOrtografico.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, erroOrtografico.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!erroOrtograficoRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<ErroOrtografico> result = erroOrtograficoRepository
            .findById(erroOrtografico.getId())
            .map(existingErroOrtografico -> {
                if (erroOrtografico.getTipoErro() != null) {
                    existingErroOrtografico.setTipoErro(erroOrtografico.getTipoErro());
                }
                if (erroOrtografico.getDescricao() != null) {
                    existingErroOrtografico.setDescricao(erroOrtografico.getDescricao());
                }

                return existingErroOrtografico;
            })
            .map(erroOrtograficoRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, erroOrtografico.getId().toString())
        );
    }

    /**
     * {@code GET  /erro-ortograficos} : get all the erroOrtograficos.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of erroOrtograficos in body.
     */
    @GetMapping("")
    public List<ErroOrtografico> getAllErroOrtograficos() {
        LOG.debug("REST request to get all ErroOrtograficos");
        return erroOrtograficoRepository.findAll();
    }

    /**
     * {@code GET  /erro-ortograficos/:id} : get the "id" erroOrtografico.
     *
     * @param id the id of the erroOrtografico to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the erroOrtografico, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ErroOrtografico> getErroOrtografico(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ErroOrtografico : {}", id);
        Optional<ErroOrtografico> erroOrtografico = erroOrtograficoRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(erroOrtografico);
    }

    /**
     * {@code DELETE  /erro-ortograficos/:id} : delete the "id" erroOrtografico.
     *
     * @param id the id of the erroOrtografico to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteErroOrtografico(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ErroOrtografico : {}", id);
        erroOrtograficoRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
