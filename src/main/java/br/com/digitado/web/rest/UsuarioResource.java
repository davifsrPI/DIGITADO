package br.com.digitado.web.rest;

import br.com.digitado.domain.Usuario;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.security.PasswordPolicy;
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
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

// REST controller para gerenciar os Usuários do domínio (professores e alunos).
// Diferente do User do JHipster (autenticação), o Usuario guarda dados do perfil pedagógico.
// A ligação entre os dois é feita pelo campo e-mail.
@RestController
@RequestMapping("/api/usuarios")
@Transactional
public class UsuarioResource {

    private static final Logger LOG = LoggerFactory.getLogger(UsuarioResource.class);

    private static final String ENTITY_NAME = "usuario";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UsuarioRepository usuarioRepository;
    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UsuarioResource(
        UsuarioRepository usuarioRepository,
        UserRepository userRepository,
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Cria um novo usuário — restrito a administradores.
    // A senha é obrigatória na criação (mas nunca é retornada pela API por causa do @JsonIgnore).
    @Secured(AuthoritiesConstants.ADMIN)
    @PostMapping("")
    public ResponseEntity<Usuario> createUsuario(@Valid @RequestBody Usuario usuario) throws URISyntaxException {
        LOG.debug("REST request to save Usuario : {}", usuario);
        if (usuario.getId() != null) {
            throw new BadRequestAlertException("A new usuario cannot already have an ID", ENTITY_NAME, "idexists");
        }
        // Garante que a senha foi enviada na criação (não pode ficar em branco)
        if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
            throw new BadRequestAlertException("Senha é obrigatória", ENTITY_NAME, "senhanull");
        }
        // Nunca persistir a senha em texto puro — armazena só o hash (bcrypt).
        // Este campo é legado (a autenticação usa o User do JHipster), mas mesmo
        // assim não deve guardar segredo em claro no banco.
        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        usuario = usuarioRepository.save(usuario);
        return ResponseEntity.created(new URI("/api/usuarios/" + usuario.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, usuario.getId().toString()))
            .body(usuario);
    }

    // Atualiza os dados de um usuário.
    // A senha nunca vem no body (está com @JsonIgnore), então sempre preserva a senha atual do banco.
    // tipoUsuario e ativo só podem ser alterados por admin — outros usuários não podem se "promover".
    @PutMapping("/{id}")
    public ResponseEntity<Usuario> updateUsuario(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Usuario usuario
    ) throws URISyntaxException {
        LOG.debug("REST request to update Usuario : {}, {}", id, usuario);
        if (usuario.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, usuario.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!usuarioRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }

        // Senha é IMUTÁVEL via update: sempre preserva a do banco, ignorando o que
        // vier no corpo. Assim nenhum PUT grava senha em claro, mesmo agora que o
        // campo aceita escrita (WRITE_ONLY, necessário para a criação).
        // Também protege tipoUsuario/ativo de edição por não-admins.
        Usuario existing = usuarioRepository.findById(id).orElseThrow();
        usuario.setSenha(existing.getSenha());
        if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            usuario.setTipoUsuario(existing.getTipoUsuario());
            usuario.setAtivo(existing.getAtivo());
        }

        usuario = usuarioRepository.save(usuario);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, usuario.getId().toString()))
            .body(usuario);
    }

    // Atualização parcial (PATCH) — altera apenas os campos enviados, mantém o restante intacto
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Usuario> partialUpdateUsuario(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody Usuario usuario
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Usuario partially : {}, {}", id, usuario);
        if (usuario.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, usuario.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!usuarioRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }

        Optional<Usuario> result = usuarioRepository
            .findById(usuario.getId())
            .map(existingUsuario -> {
                if (usuario.getNome() != null) {
                    existingUsuario.setNome(usuario.getNome());
                }
                if (usuario.getSobrenome() != null) {
                    existingUsuario.setSobrenome(usuario.getSobrenome());
                }
                if (usuario.getEmail() != null) {
                    existingUsuario.setEmail(usuario.getEmail());
                }
                // Senha é imutável via update (não se altera por PATCH) — nunca grava
                // senha em claro nem permite troca por este endpoint genérico.
                // tipoUsuario e ativo só podem ser alterados por administrador
                if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
                    if (usuario.getTipoUsuario() != null) {
                        existingUsuario.setTipoUsuario(usuario.getTipoUsuario());
                    }
                    if (usuario.getAtivo() != null) {
                        existingUsuario.setAtivo(usuario.getAtivo());
                    }
                }
                return existingUsuario;
            })
            .map(usuarioRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, usuario.getId().toString())
        );
    }

    // Listagem de usuários com controle de visibilidade:
    // admin vê todos; usuário comum vê apenas o próprio perfil (buscado pelo e-mail do User autenticado)
    @GetMapping("")
    public List<Usuario> getAllUsuarios(@RequestParam(name = "eagerload", required = false, defaultValue = "true") boolean eagerload) {
        LOG.debug("REST request to get all Usuarios");
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return eagerload ? usuarioRepository.findAllWithEagerRelationships() : usuarioRepository.findAll();
        }
        // Usuário comum: retorna somente o próprio registro baseado no e-mail do User autenticado
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(List::of)
            .orElse(List.of());
    }

    // Busca um usuário pelo ID — verifica permissão antes de retornar
    @GetMapping("/{id}")
    public ResponseEntity<Usuario> getUsuario(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Usuario : {}", id);
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        Optional<Usuario> usuario = usuarioRepository.findOneWithEagerRelationships(id);
        return ResponseUtil.wrapOrNotFound(usuario);
    }

    // Exclui um usuário — apenas o próprio ou admin podem excluir
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUsuario(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Usuario : {}", id);
        if (!usuarioRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        usuarioRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    // Corpo da troca de senha: a atual (prova de posse) e a nova
    public record AlterarSenhaVM(String senhaAtual, String novaSenha) {}

    /**
     * Troca a senha do PRÓPRIO Usuario — único caminho para alterar a senha
     * (os updates PUT/PATCH a preservam sempre). A conta alvo é resolvida
     * exclusivamente pelo token JWT (sem id na URL — imune a IDOR) e a senha
     * atual é conferida via bcrypt antes de aceitar a nova.
     */
    @PostMapping("/alterar-senha")
    public ResponseEntity<Void> alterarSenha(@RequestBody AlterarSenhaVM vm) {
        // Mesma política forte da conta principal (PasswordPolicy): esta senha
        // também confirma a exclusão LGPD da conta — não pode ser mais fraca
        if (vm == null || PasswordPolicy.isInvalid(vm.novaSenha())) {
            throw new BadRequestAlertException(
                "Nova senha inválida: mínimo 8 caracteres com maiúscula, minúscula, número e caractere especial",
                ENTITY_NAME,
                "senhainvalida"
            );
        }
        Usuario meu = SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .orElseThrow(() -> new BadRequestAlertException("Conta sem perfil de usuário", ENTITY_NAME, "semusuario"));

        // Conta que já tem senha exige a atual correta; conta legada sem senha
        // registrada pode definir a primeira diretamente
        boolean temSenha = meu.getSenha() != null && !meu.getSenha().isBlank();
        if (temSenha && (vm.senhaAtual() == null || !passwordEncoder.matches(vm.senhaAtual(), meu.getSenha()))) {
            throw new BadRequestAlertException("Senha atual incorreta", ENTITY_NAME, "senhaatualincorreta");
        }

        meu.setSenha(passwordEncoder.encode(vm.novaSenha()));
        usuarioRepository.save(meu);
        LOG.info("Senha do usuario {} alterada pelo próprio dono", meu.getId());
        return ResponseEntity.noContent().build();
    }

    // Verifica se o usuário logado é o dono do perfil ou administrador do sistema.
    // A identificação é feita comparando o e-mail do User autenticado com o e-mail do Usuario.
    private boolean isOwnerOrAdmin(Long usuarioId) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario -> usuario.getId().equals(usuarioId))
            .orElse(false);
    }
}
