package br.com.digitado.web.rest;

import br.com.digitado.domain.Sala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
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

// REST controller para gerenciar salas de aula.
// Um professor cria e controla a sala; alunos só podem listar as salas que participam.
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

    // Cria uma nova sala. Automaticamente associa o usuário logado como professor da sala,
    // buscando o Usuario correspondente pelo e-mail do User autenticado.
    @PostMapping("")
    public ResponseEntity<SalaResponseVM> createSala(@Valid @RequestBody Sala sala) throws URISyntaxException {
        LOG.debug("REST request to save Sala : {}", sala);
        if (sala.getId() != null) {
            throw new BadRequestAlertException("A new sala cannot already have an ID", ENTITY_NAME, "idexists");
        }
        // Impede criação com código duplicado
        if (sala.getCodigo() != null && salaRepository.findByCodigo(sala.getCodigo()).isPresent()) {
            throw new BadRequestAlertException("Código de sala já em uso", ENTITY_NAME, "codigoexists");
        }
        // Vincula o professor logado à sala — se não houver Usuario correspondente, professor fica null
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .ifPresent(sala::setProfessor);
        sala = salaRepository.save(sala);
        // Retorna apenas os campos públicos da sala (sem o professor, para não vazar dados)
        SalaResponseVM vm = new SalaResponseVM(sala.getId(), sala.getNome(), sala.getCodigo(), sala.getDescricao(), sala.getAtivo());
        return ResponseEntity.created(new URI("/api/salas/" + sala.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, sala.getId().toString()))
            .body(vm);
    }

    // Atualiza os dados de uma sala — apenas o dono ou admin podem fazer isso
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
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        sala = salaRepository.save(sala);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, sala.getId().toString()))
            .body(sala);
    }

    // Atualização parcial da sala (PATCH) — apenas campos enviados são alterados
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
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
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

    // Listagem de salas com controle de visibilidade:
    // admin vê todas; professores e alunos veem apenas as salas que lhes pertencem
    @GetMapping("")
    public List<Sala> getAllSalas(@RequestParam(required = false) Boolean ativo) {
        LOG.debug("REST request to get all Salas");
        boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
        if (isAdmin) {
            if (ativo != null) return salaRepository.findByAtivo(ativo);
            return salaRepository.findAll();
        }
        // Para usuários comuns: une as salas onde é professor com as salas onde é aluno
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario -> {
                List<Sala> salas = new java.util.ArrayList<>(usuario.getSalas());
                salas.addAll(usuario.getSalasAlunos());
                if (ativo != null) {
                    salas.removeIf(s -> !ativo.equals(s.getAtivo()));
                }
                return salas;
            })
            .orElse(List.of());
    }

    // Busca uma sala pelo ID — sem restrição de acesso (código é público para quem tiver o link)
    @GetMapping("/{id}")
    public ResponseEntity<Sala> getSala(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Sala : {}", id);
        Optional<Sala> sala = salaRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(sala);
    }

    // Exclui uma sala — apenas o professor dono ou admin podem excluir
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSala(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Sala : {}", id);
        if (!salaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        salaRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    // Verifica se o usuário logado é dono da sala (como professor) ou administrador do sistema
    private boolean isOwnerOrAdmin(Long salaId) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario ->
                salaRepository
                    .findById(salaId)
                    .map(sala -> sala.getProfessor() != null && sala.getProfessor().getId().equals(usuario.getId()))
                    .orElse(false)
            )
            .orElse(false);
    }
}
