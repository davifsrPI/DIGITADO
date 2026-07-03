package br.com.digitado.web.rest;

import br.com.digitado.domain.UsuarioConquista;
import br.com.digitado.repository.UsuarioConquistaRepository;
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
 * REST controller for managing {@link br.com.digitado.domain.UsuarioConquista}.
 *
 * Restrito a administradores: estes endpoints leem e gravam conquistas de QUALQUER usuário.
 * O usuário comum consulta as próprias conquistas apenas por GET /api/conquistas/minhas,
 * onde a identidade é resolvida no backend a partir do token — assim ninguém consegue
 * ver ou conceder conquistas de/para outra conta.
 */
@RestController
@RequestMapping("/api/usuario-conquistas")
@Transactional
@Secured(AuthoritiesConstants.ADMIN)
public class UsuarioConquistaResource {

    private static final Logger LOG = LoggerFactory.getLogger(UsuarioConquistaResource.class);

    private static final String ENTITY_NAME = "usuarioConquista";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UsuarioConquistaRepository usuarioConquistaRepository;

    public UsuarioConquistaResource(UsuarioConquistaRepository usuarioConquistaRepository) {
        this.usuarioConquistaRepository = usuarioConquistaRepository;
    }

    /**
     * {@code POST  /usuario-conquistas} : Create a new usuarioConquista.
     *
     * @param usuarioConquista the usuarioConquista to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new usuarioConquista, or with status {@code 400 (Bad Request)} if the usuarioConquista has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<UsuarioConquista> createUsuarioConquista(@RequestBody UsuarioConquista usuarioConquista)
        throws URISyntaxException {
        LOG.debug("REST request to save UsuarioConquista : {}", usuarioConquista);
        if (usuarioConquista.getId() != null) {
            throw new BadRequestAlertException("A new usuarioConquista cannot already have an ID", ENTITY_NAME, "idexists");
        }
        usuarioConquista = usuarioConquistaRepository.save(usuarioConquista);
        return ResponseEntity.created(new URI("/api/usuario-conquistas/" + usuarioConquista.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, usuarioConquista.getId().toString()))
            .body(usuarioConquista);
    }

    /**
     * {@code PUT  /usuario-conquistas/:id} : Updates an existing usuarioConquista.
     *
     * @param id the id of the usuarioConquista to save.
     * @param usuarioConquista the usuarioConquista to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated usuarioConquista,
     * or with status {@code 400 (Bad Request)} if the usuarioConquista is not valid,
     * or with status {@code 500 (Internal Server Error)} if the usuarioConquista couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioConquista> updateUsuarioConquista(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody UsuarioConquista usuarioConquista
    ) throws URISyntaxException {
        LOG.debug("REST request to update UsuarioConquista : {}, {}", id, usuarioConquista);
        if (usuarioConquista.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, usuarioConquista.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!usuarioConquistaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        usuarioConquista = usuarioConquistaRepository.save(usuarioConquista);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, usuarioConquista.getId().toString()))
            .body(usuarioConquista);
    }

    /**
     * {@code PATCH  /usuario-conquistas/:id} : Partial updates given fields of an existing usuarioConquista, field will ignore if it is null
     *
     * @param id the id of the usuarioConquista to save.
     * @param usuarioConquista the usuarioConquista to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated usuarioConquista,
     * or with status {@code 400 (Bad Request)} if the usuarioConquista is not valid,
     * or with status {@code 404 (Not Found)} if the usuarioConquista is not found,
     * or with status {@code 500 (Internal Server Error)} if the usuarioConquista couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<UsuarioConquista> partialUpdateUsuarioConquista(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody UsuarioConquista usuarioConquista
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update UsuarioConquista partially : {}, {}", id, usuarioConquista);
        if (usuarioConquista.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, usuarioConquista.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!usuarioConquistaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<UsuarioConquista> result = usuarioConquistaRepository
            .findById(usuarioConquista.getId())
            .map(existingUsuarioConquista -> {
                if (usuarioConquista.getDataConquista() != null) {
                    existingUsuarioConquista.setDataConquista(usuarioConquista.getDataConquista());
                }
                if (usuarioConquista.getProgresso() != null) {
                    existingUsuarioConquista.setProgresso(usuarioConquista.getProgresso());
                }
                if (usuarioConquista.getConcluida() != null) {
                    existingUsuarioConquista.setConcluida(usuarioConquista.getConcluida());
                }

                return existingUsuarioConquista;
            })
            .map(usuarioConquistaRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, usuarioConquista.getId().toString())
        );
    }

    /**
     * {@code GET  /usuario-conquistas} : get all the usuarioConquistas.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of usuarioConquistas in body.
     */
    @GetMapping("")
    public List<UsuarioConquista> getAllUsuarioConquistas() {
        LOG.debug("REST request to get all UsuarioConquistas");
        return usuarioConquistaRepository.findAll();
    }

    /**
     * {@code GET  /usuario-conquistas/:id} : get the "id" usuarioConquista.
     *
     * @param id the id of the usuarioConquista to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the usuarioConquista, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioConquista> getUsuarioConquista(@PathVariable("id") Long id) {
        LOG.debug("REST request to get UsuarioConquista : {}", id);
        Optional<UsuarioConquista> usuarioConquista = usuarioConquistaRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(usuarioConquista);
    }

    /**
     * {@code DELETE  /usuario-conquistas/:id} : delete the "id" usuarioConquista.
     *
     * @param id the id of the usuarioConquista to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUsuarioConquista(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete UsuarioConquista : {}", id);
        usuarioConquistaRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
