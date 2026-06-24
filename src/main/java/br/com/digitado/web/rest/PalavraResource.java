package br.com.digitado.web.rest;

import br.com.digitado.domain.Palavra;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * REST controller for managing {@link br.com.digitado.domain.Palavra}.
 */
@RestController
@RequestMapping("/api/palavras")
@Transactional
public class PalavraResource {

    private static final Logger LOG = LoggerFactory.getLogger(PalavraResource.class);

    private static final String ENTITY_NAME = "palavra";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PalavraRepository palavraRepository;

    public PalavraResource(PalavraRepository palavraRepository) {
        this.palavraRepository = palavraRepository;
    }

    @GetMapping("/buscar")
    public ResponseEntity<Map<String, Object>> buscarPalavra(@RequestParam String texto) {
        String busca = texto.trim();
        Map<String, Object> response = new HashMap<>();
        Optional<Palavra> exata = palavraRepository.findByTextoIgnoreCase(busca);
        if (exata.isPresent()) {
            response.put("encontrada", true);
            response.put("exata", true);
            response.put("palavra", exata.get());
            return ResponseEntity.ok(response);
        }
        List<Palavra> similares = palavraRepository.findTop5ByTextoContainingIgnoreCaseAndAtivaTrue(busca);
        response.put("encontrada", false);
        response.put("exata", false);
        response.put("similares", similares);
        return ResponseEntity.ok(response);
    }

    /**
     * {@code POST  /palavras} : Create a new palavra.
     *
     * @param palavra the palavra to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new palavra, or with status {@code 400 (Bad Request)} if the palavra has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<Palavra> createPalavra(@Valid @RequestBody Palavra palavra) throws URISyntaxException {
        LOG.debug("REST request to save Palavra : {}", palavra);
        if (palavra.getId() != null) {
            throw new BadRequestAlertException("A new palavra cannot already have an ID", ENTITY_NAME, "idexists");
        }
        palavra = palavraRepository.save(palavra);
        return ResponseEntity.created(new URI("/api/palavras/" + palavra.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, palavra.getId().toString()))
            .body(palavra);
    }

    /**
     * {@code PUT  /palavras/:id} : Updates an existing palavra.
     *
     * @param id the id of the palavra to save.
     * @param palavra the palavra to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated palavra,
     * or with status {@code 400 (Bad Request)} if the palavra is not valid,
     * or with status {@code 500 (Internal Server Error)} if the palavra couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Palavra> updatePalavra(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Palavra palavra
    ) throws URISyntaxException {
        LOG.debug("REST request to update Palavra : {}, {}", id, palavra);
        if (palavra.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, palavra.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!palavraRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        palavra = palavraRepository.save(palavra);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, palavra.getId().toString()))
            .body(palavra);
    }

    /**
     * {@code PATCH  /palavras/:id} : Partial updates given fields of an existing palavra, field will ignore if it is null
     *
     * @param id the id of the palavra to save.
     * @param palavra the palavra to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated palavra,
     * or with status {@code 400 (Bad Request)} if the palavra is not valid,
     * or with status {@code 404 (Not Found)} if the palavra is not found,
     * or with status {@code 500 (Internal Server Error)} if the palavra couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Palavra> partialUpdatePalavra(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Palavra palavra
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Palavra partially : {}, {}", id, palavra);
        if (palavra.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, palavra.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!palavraRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Palavra> result = palavraRepository
            .findById(palavra.getId())
            .map(existingPalavra -> {
                if (palavra.getTexto() != null) {
                    existingPalavra.setTexto(palavra.getTexto());
                }
                if (palavra.getDificuldade() != null) {
                    existingPalavra.setDificuldade(palavra.getDificuldade());
                }
                if (palavra.getCategoria() != null) {
                    existingPalavra.setCategoria(palavra.getCategoria());
                }
                if (palavra.getIdioma() != null) {
                    existingPalavra.setIdioma(palavra.getIdioma());
                }
                if (palavra.getPossuiAcento() != null) {
                    existingPalavra.setPossuiAcento(palavra.getPossuiAcento());
                }
                if (palavra.getAtiva() != null) {
                    existingPalavra.setAtiva(palavra.getAtiva());
                }

                return existingPalavra;
            })
            .map(palavraRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, palavra.getId().toString())
        );
    }

    /**
     * {@code GET  /palavras} : get all the palavras.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of palavras in body.
     */
    @GetMapping("")
    public List<Palavra> getAllPalavras() {
        LOG.debug("REST request to get all Palavras");
        return palavraRepository.findAll();
    }

    /**
     * {@code GET  /palavras/:id} : get the "id" palavra.
     *
     * @param id the id of the palavra to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the palavra, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Palavra> getPalavra(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Palavra : {}", id);
        Optional<Palavra> palavra = palavraRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(palavra);
    }

    /**
     * {@code DELETE  /palavras/:id} : delete the "id" palavra.
     *
     * @param id the id of the palavra to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePalavra(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Palavra : {}", id);
        palavraRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
