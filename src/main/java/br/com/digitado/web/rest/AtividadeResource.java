package br.com.digitado.web.rest;

import br.com.digitado.domain.Atividade;
import br.com.digitado.repository.AtividadeRepository;
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
@RequestMapping("/api/atividades")
@Transactional
public class AtividadeResource {

    private static final Logger LOG = LoggerFactory.getLogger(AtividadeResource.class);

    private static final String ENTITY_NAME = "atividade";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AtividadeRepository atividadeRepository;
    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;

    public AtividadeResource(AtividadeRepository atividadeRepository, UserRepository userRepository, UsuarioRepository usuarioRepository) {
        this.atividadeRepository = atividadeRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("")
    public ResponseEntity<Atividade> createAtividade(@Valid @RequestBody Atividade atividade) throws URISyntaxException {
        LOG.debug("REST request to save Atividade : {}", atividade);
        if (atividade.getId() != null) {
            throw new BadRequestAlertException("A new atividade cannot already have an ID", ENTITY_NAME, "idexists");
        }
        // Só o dono da sala (ou admin) pode criar atividade nela — evita criar
        // atividade na sala de outro professor
        if (!podeCriarNaSala(atividade)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        atividade = atividadeRepository.save(atividade);
        return ResponseEntity.created(new URI("/api/atividades/" + atividade.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, atividade.getId().toString()))
            .body(atividade);
    }

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
        if (!isSalaOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        atividade = atividadeRepository.save(atividade);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, atividade.getId().toString()))
            .body(atividade);
    }

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
        if (!isSalaOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
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

    // Admin vê todas; professor vê apenas as atividades das próprias salas
    @GetMapping("")
    public List<Atividade> getAllAtividades() {
        LOG.debug("REST request to get all Atividades");
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return atividadeRepository.findAll();
        }
        return usuarioAtual()
            .map(u ->
                atividadeRepository
                    .findAll()
                    .stream()
                    .filter(
                        a ->
                            a.getSala() != null &&
                            a.getSala().getProfessor() != null &&
                            a.getSala().getProfessor().getId().equals(u.getId())
                    )
                    .toList()
            )
            .orElse(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Atividade> getAtividade(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Atividade : {}", id);
        if (!atividadeRepository.existsById(id)) {
            return ResponseUtil.wrapOrNotFound(Optional.empty());
        }
        if (!isSalaOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        Optional<Atividade> atividade = atividadeRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(atividade);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAtividade(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Atividade : {}", id);
        if (!atividadeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isSalaOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        atividadeRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    // Usuario do domínio correspondente ao login autenticado (login -> User -> Usuario pelo e-mail)
    private Optional<br.com.digitado.domain.Usuario> usuarioAtual() {
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()));
    }

    // Pode criar a atividade se for admin ou dono da sala indicada no payload
    private boolean podeCriarNaSala(Atividade atividade) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        if (atividade.getSala() == null || atividade.getSala().getCodigo() == null) {
            return false;
        }
        String salaCodigo = atividade.getSala().getCodigo();
        return usuarioAtual().map(u -> u.getSalas().stream().anyMatch(s -> s.getCodigo().equals(salaCodigo))).orElse(false);
    }

    private boolean isSalaOwnerOrAdmin(Long atividadeId) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario ->
                atividadeRepository
                    .findById(atividadeId)
                    .map(
                        at ->
                            at.getSala() != null &&
                            at.getSala().getProfessor() != null &&
                            at.getSala().getProfessor().getId().equals(usuario.getId())
                    )
                    .orElse(false)
            )
            .orElse(false);
    }
}
