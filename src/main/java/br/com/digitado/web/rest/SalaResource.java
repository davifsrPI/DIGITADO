package br.com.digitado.web.rest;

import br.com.digitado.domain.Sala;
import br.com.digitado.domain.enumeration.TipoSala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.security.SecurityUtils;
import br.com.digitado.service.JogoSalaService;
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
// A sala é identificada pelo código de acesso (PK) — não existe id numérico.
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
    private final JogoSalaService jogoSalaService;

    public SalaResource(
        SalaRepository salaRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        JogoSalaService jogoSalaService
    ) {
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.jogoSalaService = jogoSalaService;
    }

    // Cria uma nova sala. Automaticamente associa o usuário logado como professor da sala,
    // buscando o Usuario correspondente pelo e-mail do User autenticado.
    @PostMapping("")
    public ResponseEntity<SalaResponseVM> createSala(@Valid @RequestBody Sala sala) throws URISyntaxException {
        LOG.debug("REST request to save Sala : {}", sala);
        // Impede criação com código duplicado — o código é a chave primária
        if (salaRepository.existsById(sala.getCodigo())) {
            throw new BadRequestAlertException("Código de sala já em uso", ENTITY_NAME, "codigoexists");
        }
        // Vincula o professor logado à sala — se não houver Usuario correspondente, professor fica null
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .ifPresent(sala::setProfessor);
        // Data de criação é definida pelo servidor — o cliente não consegue forjar
        sala.setDataCriacao(java.time.Instant.now());
        // Normaliza tipo/visibilidade: sem tipo vira TURMA; a escolha pública/privada
        // só existe para duelos 1v1 — salas de turma são sempre acessadas pelo código
        if (sala.getTipo() == null) {
            sala.setTipo(TipoSala.TURMA);
        }
        if (sala.getTipo() != TipoSala.UM_V_UM) {
            sala.setPrivada(true);
        } else if (sala.getPrivada() == null) {
            sala.setPrivada(true);
        }
        sala = salaRepository.save(sala);
        // Retorna apenas os campos públicos da sala (sem o professor, para não vazar dados)
        SalaResponseVM vm = toVM(sala);
        return ResponseEntity.created(new URI("/api/salas/" + sala.getCodigo()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, sala.getCodigo()))
            .body(vm);
    }

    // Atualiza os dados de uma sala — apenas o dono ou admin podem fazer isso
    @PutMapping("/{codigo}")
    public ResponseEntity<Sala> updateSala(
        @PathVariable(value = "codigo", required = false) final String codigo,
        @Valid @RequestBody Sala sala
    ) throws URISyntaxException {
        LOG.debug("REST request to update Sala : {}, {}", codigo, sala);
        if (sala.getCodigo() == null) {
            throw new BadRequestAlertException("Invalid codigo", ENTITY_NAME, "codigonull");
        }
        if (!Objects.equals(codigo, sala.getCodigo())) {
            throw new BadRequestAlertException("Invalid codigo", ENTITY_NAME, "codigoinvalid");
        }
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        sala = salaRepository.save(sala);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, sala.getCodigo()))
            .body(sala);
    }

    // Atualização parcial da sala (PATCH) — apenas campos enviados são alterados.
    // O código não pode ser alterado: é a chave primária da sala.
    @PatchMapping(value = "/{codigo}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Sala> partialUpdateSala(
        @PathVariable(value = "codigo", required = false) final String codigo,
        @NotNull @RequestBody Sala sala
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Sala partially : {}, {}", codigo, sala);
        if (sala.getCodigo() == null) {
            throw new BadRequestAlertException("Invalid codigo", ENTITY_NAME, "codigonull");
        }
        if (!Objects.equals(codigo, sala.getCodigo())) {
            throw new BadRequestAlertException("Invalid codigo", ENTITY_NAME, "codigoinvalid");
        }
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }

        Optional<Sala> result = salaRepository
            .findById(sala.getCodigo())
            .map(existingSala -> {
                if (sala.getNome() != null) {
                    existingSala.setNome(sala.getNome());
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
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, sala.getCodigo())
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

    // Lista global de duelos 1v1 PÚBLICOS abertos — qualquer usuário autenticado pode ver
    // e entrar. Duelos privados nunca aparecem aqui: só entra quem tiver o código.
    // Duelos que já estão com 2 jogadores conectados ficam de fora (sala cheia).
    @GetMapping("/1v1/publicas")
    public List<SalaResponseVM> getDuelosPublicos() {
        LOG.debug("REST request to get duelos 1v1 publicos");
        return salaRepository
            .findByTipoAndPrivadaFalseAndAtivoTrueOrderByDataCriacaoDesc(TipoSala.UM_V_UM)
            .stream()
            .map(this::toVM)
            .filter(vm -> vm.jogadores() < 2)
            .toList();
    }

    // Converte a entidade para o VM público, anexando quantos jogadores estão conectados agora
    private SalaResponseVM toVM(Sala sala) {
        return new SalaResponseVM(
            sala.getCodigo(),
            sala.getNome(),
            sala.getDescricao(),
            sala.getAtivo(),
            sala.getTipo() != null ? sala.getTipo().name() : TipoSala.TURMA.name(),
            sala.getPrivada(),
            jogoSalaService.conectadosNaSala(sala.getCodigo())
        );
    }

    // Busca uma sala pelo código — sem restrição de acesso (código é público para quem tiver o link)
    @GetMapping("/{codigo}")
    public ResponseEntity<Sala> getSala(@PathVariable("codigo") String codigo) {
        LOG.debug("REST request to get Sala : {}", codigo);
        Optional<Sala> sala = salaRepository.findById(codigo);
        return ResponseUtil.wrapOrNotFound(sala);
    }

    // Exclui uma sala — apenas o professor dono ou admin podem excluir
    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> deleteSala(@PathVariable("codigo") String codigo) {
        LOG.debug("REST request to delete Sala : {}", codigo);
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        salaRepository.deleteById(codigo);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, codigo)).build();
    }

    // Verifica se o usuário logado é dono da sala (como professor) ou administrador do sistema
    private boolean isOwnerOrAdmin(String codigo) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario ->
                salaRepository
                    .findById(codigo)
                    .map(sala -> sala.getProfessor() != null && sala.getProfessor().getId().equals(usuario.getId()))
                    .orElse(false)
            )
            .orElse(false);
    }
}
