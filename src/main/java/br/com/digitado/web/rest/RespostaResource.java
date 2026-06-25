package br.com.digitado.web.rest;

import br.com.digitado.domain.Resposta;
import br.com.digitado.repository.RespostaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.security.SecurityUtils;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
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
@RequestMapping("/api/respostas")
@Transactional
public class RespostaResource {

    private static final Logger LOG = LoggerFactory.getLogger(RespostaResource.class);

    private static final String ENTITY_NAME = "resposta";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final RespostaRepository respostaRepository;
    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;

    public RespostaResource(RespostaRepository respostaRepository, UserRepository userRepository, UsuarioRepository usuarioRepository) {
        this.respostaRepository = respostaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("")
    public ResponseEntity<Resposta> createResposta(@Valid @RequestBody Resposta resposta) throws URISyntaxException {
        LOG.debug("REST request to save Resposta : {}", resposta);
        if (resposta.getId() != null) {
            throw new BadRequestAlertException("A new resposta cannot already have an ID", ENTITY_NAME, "idexists");
        }

        // Força o aluno a ser o usuário logado — cliente não pode escolher
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .ifPresent(resposta::setAluno);

        // Calcula correta server-side comparando com o texto da palavra
        if (resposta.getPalavra() != null && resposta.getPalavra().getTexto() != null && resposta.getRespostaDigitada() != null) {
            boolean correta = resposta.getPalavra().getTexto().equalsIgnoreCase(resposta.getRespostaDigitada().trim());
            resposta.setCorreta(correta);
            resposta.setPontuacao(correta ? calcularPontuacao(resposta.getTempoResposta()) : 0);
        } else {
            resposta.setCorreta(false);
            resposta.setPontuacao(0);
        }

        // Data sempre gerada pelo servidor
        resposta.setDataResposta(Instant.now());

        resposta = respostaRepository.save(resposta);
        return ResponseEntity.created(new URI("/api/respostas/" + resposta.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, resposta.getId().toString()))
            .body(resposta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Resposta> updateResposta(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody Resposta resposta
    ) throws URISyntaxException {
        LOG.debug("REST request to update Resposta : {}, {}", id, resposta);
        if (resposta.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, resposta.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!respostaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }

        // Preserva campos calculados pelo servidor
        Resposta existing = respostaRepository.findById(id).orElseThrow();
        resposta.setCorreta(existing.getCorreta());
        resposta.setPontuacao(existing.getPontuacao());
        resposta.setAluno(existing.getAluno());
        resposta.setDataResposta(existing.getDataResposta());

        resposta = respostaRepository.save(resposta);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, resposta.getId().toString()))
            .body(resposta);
    }

    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<Resposta> partialUpdateResposta(
        @PathVariable(value = "id", required = false) final Long id,
        @RequestBody Resposta resposta
    ) throws URISyntaxException {
        LOG.debug("REST request to partial update Resposta partially : {}, {}", id, resposta);
        if (resposta.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, resposta.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }
        if (!respostaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }

        Optional<Resposta> result = respostaRepository
            .findById(resposta.getId())
            .map(existingResposta -> {
                if (resposta.getRespostaDigitada() != null) {
                    existingResposta.setRespostaDigitada(resposta.getRespostaDigitada());
                }
                if (resposta.getTempoResposta() != null) {
                    existingResposta.setTempoResposta(resposta.getTempoResposta());
                }
                // correta, pontuacao, aluno e dataResposta não são atualizáveis pelo cliente
                return existingResposta;
            })
            .map(respostaRepository::save);

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, resposta.getId().toString())
        );
    }

    @GetMapping("")
    public List<Resposta> getAllRespostas() {
        LOG.debug("REST request to get all Respostas");
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return respostaRepository.findAll();
        }
        // Usuários comuns veem apenas as próprias respostas
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario -> respostaRepository.findByAlunoId(usuario.getId()))
            .orElse(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resposta> getResposta(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Resposta : {}", id);
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        Optional<Resposta> resposta = respostaRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(resposta);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResposta(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Resposta : {}", id);
        if (!respostaRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        if (!isOwnerOrAdmin(id)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        respostaRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }

    private boolean isOwnerOrAdmin(Long respostaId) {
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return true;
        }
        return SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario ->
                respostaRepository
                    .findById(respostaId)
                    .map(r -> r.getAluno() != null && r.getAluno().getId().equals(usuario.getId()))
                    .orElse(false)
            )
            .orElse(false);
    }

    private int calcularPontuacao(Integer tempoResposta) {
        if (tempoResposta == null || tempoResposta <= 0) return 10;
        // Quanto mais rápido, mais pontos (mínimo 1, máximo 100)
        int pontos = Math.max(1, 100 - tempoResposta);
        return Math.min(pontos, 100);
    }
}
