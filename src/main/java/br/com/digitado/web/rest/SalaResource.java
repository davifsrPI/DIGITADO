package br.com.digitado.web.rest;

import br.com.digitado.domain.Sala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.SecurityUtils;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import br.com.digitado.web.rest.vm.SalaResponseVM;
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
 * REST controller for managing {@link br.com.digitado.domain.Sala}.
 */
@RestController
@RequestMapping("/api/salas")
@Transactional
public class SalaResource {

    private static final Logger LOG = LoggerFactory.getLogger(SalaResource.class);

    private static final String ENTITY_NAME = "sala";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SalaRepository salaRepository;
    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;

    public SalaResource(SalaRepository salaRepository, UserRepository userRepository, UsuarioRepository usuarioRepository) {
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * {@code POST  /salas} : Create a new sala.
     *
     * @param sala the sala to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new sala, or with status {@code 400 (Bad Request)} if the sala has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<SalaResponseVM> createSala(@Valid @RequestBody Sala sala) throws URISyntaxException {
        LOG.debug("REST request to save Sala : {}", sala);
        if (sala.getId() != null) {
            throw new BadRequestAlertException("A new sala cannot already have an ID", ENTITY_NAME, "idexists");
        }
        if (sala.getCodigo() != null && salaRepository.findByCodigo(sala.getCodigo()).isPresent()) {
            throw new BadRequestAlertException("Código de sala já em uso", ENTITY_NAME, "codigoexists");
        }
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .ifPresent(sala::setProfessor);
        sala = salaRepository.save(sala);
        SalaResponseVM vm = new SalaResponseVM(sala.getId(), sala.getNome(), sala.getCodigo(), sala.getDescricao(), sala.getAtivo());
        return ResponseEntity.created(new URI("/api/salas/" + sala.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, sala.getId().toString()))
            .body(vm);
    }

    /**
     * {@code PUT  /salas/:id} : Updates an existing sala.
     *
     * @param id the id of the sala to save.
     * @param sala the sala to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated sala,
     * or with status {@code 400 (Bad Request)} if the sala is not valid,
     * or with status {@code 500 (Internal Server Error)} if the sala couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Sala> updateSala(@PathVariable(value = "id", required = false) final Long id, @Valid @RequestBody Sala sala)
        throws URISyntaxException {
        LOG.debug("REST request to update Sala : {}, {}", id, sala);
        if (sala.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, sala.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!salaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        sala = salaRepository.save(sala);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, sala.getId().toString()))
            .body(sala);
    }

    /**
     * {@code PATCH  /salas/:id} : Partial updates given fields of an existing sala, field will ignore if it is null
     *
     * @param id the id of the sala to save.
     * @param sala the sala to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated sala,
     * or with status {@code 400 (Bad Request)} if the sala is not valid,
     * or with status {@code 404 (Not Found)} if the sala is not found,
     * or with status {@code 500 (Internal Server Error)} if the sala couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Sala> partialUpdateSala(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Sala sala
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Sala partially : {}, {}", id, sala);
        if (sala.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, sala.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!salaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<Sala> result = salaRepository
            .findById(sala.getId())
            .map(existingSala -> {
                if (sala.getNome() != null) {
                    existingSala.setNome(sala.getNome());
                }
                if (sala.getCodigo() != null) {
                    existingSala.setCodigo(sala.getCodigo());
                }
                if (sala.getDescricao() != null) {
                    existingSala.setDescricao(sala.getDescricao());
                }
                if (sala.getAtivo() != null) {
                    existingSala.setAtivo(sala.getAtivo());
                }

                return existingSala;
            })
            .map(salaRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, sala.getId().toString())
        );
    }

    /**
     * {@code GET  /salas} : get all the salas.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of salas in body.
     */
    @GetMapping("")
    public List<Sala> getAllSalas(@RequestParam(required = false) Boolean ativo) {
        LOG.debug("REST request to get all Salas");
        if (ativo != null) return salaRepository.findByAtivo(ativo);
        return salaRepository.findAll();
    }

    /**
     * {@code GET  /salas/:id} : get the "id" sala.
     *
     * @param id the id of the sala to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the sala, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Sala> getSala(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Sala : {}", id);
        Optional<Sala> sala = salaRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(sala);
    }

    /**
     * {@code DELETE  /salas/:id} : delete the "id" sala.
     *
     * @param id the id of the sala to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSala(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Sala : {}", id);
        salaRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
