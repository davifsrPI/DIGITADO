package br.com.digitado.web.rest;

import br.com.digitado.domain.ListaPalavras;
import br.com.digitado.repository.ListaPalavrasRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.security.SecurityUtils;
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

@RestController
@RequestMapping("/api/lista-palavras")
@Transactional
public class ListaPalavrasResource {

    private static final Logger LOG = LoggerFactory.getLogger(ListaPalavrasResource.class);

    private static final String ENTITY_NAME = "listaPalavras";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ListaPalavrasRepository listaPalavrasRepository;
    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;

    public ListaPalavrasResource(
        ListaPalavrasRepository listaPalavrasRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository
    ) {
        this.listaPalavrasRepository = listaPalavrasRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("")
    public ResponseEntity<ListaPalavras> createListaPalavras(@Valid @RequestBody ListaPalavras listaPalavras) throws URISyntaxException {
        LOG.debug("REST request to save ListaPalavras : {}", listaPalavras);
        if (listaPalavras.getId() != null) {
            throw new BadRequestAlertException("A new listaPalavras cannot already have an ID", ENTITY_NAME, "idexists");
        }
        // Força professor a ser o usuário logado
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .ifPresent(listaPalavras::setProfessor);

        listaPalavras = listaPalavrasRepository.save(listaPalavras);
        return ResponseEntity.created(new URI("/api/lista-palavras/" + listaPalavras.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, listaPalavras.getId().toString()))
            .body(listaPalavras);
    }

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
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        listaPalavras = listaPalavrasRepository.save(listaPalavras);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, listaPalavras.getId().toString()))
            .body(listaPalavras);
    }

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
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
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

    @GetMapping("")
    public List<ListaPalavras> getAllListaPalavras(
        @RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload
    ) {
        LOG.debug("REST request to get all ListaPalavras");
        return eagerload ? listaPalavrasRepository.findAllWithEagerRelationships() : listaPalavrasRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListaPalavras> getListaPalavras(@PathVariable("id") Long id) {
        LOG.debug("REST request to get ListaPalavras : {}", id);
        Optional<ListaPalavras> listaPalavras = listaPalavrasRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(listaPalavras);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteListaPalavras(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete ListaPalavras : {}", id);
        if (!listaPalavrasRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        listaPalavrasRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    private boolean isOwnerOrAdmin(Long listaId) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario ->
                listaPalavrasRepository
                    .findById(listaId)
                    .map(lp -> lp.getProfessor() != null && lp.getProfessor().getId().equals(usuario.getId()))
                    .orElse(false)
            )
            .orElse(false);
    }
}
