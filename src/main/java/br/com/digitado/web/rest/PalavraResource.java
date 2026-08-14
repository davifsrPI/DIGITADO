package br.com.digitado.web.rest;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.security.SecurityUtils;
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
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

// REST controller para o banco de palavras do DIGITADO.
// Os métodos CRUD abaixo foram gerados pelo JHipster; o endpoint /buscar é customizado.
@RestController
@RequestMapping("/api/palavras")
@Transactional
public class PalavraResource {

    private static final Logger LOG = LoggerFactory.getLogger(PalavraResource.class);

    private static final String ENTITY_NAME = "palavra";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PalavraRepository palavraRepository;
    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;

    public PalavraResource(PalavraRepository palavraRepository, UserRepository userRepository, UsuarioRepository usuarioRepository) {
        this.palavraRepository = palavraRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // Busca uma palavra pelo texto exato; se não encontrar, retorna até 5 palavras semelhantes
    // (busca parcial por LIKE). Usado na tela de criação de sala para o professor adicionar palavras extras.
    @GetMapping("/buscar")
    public ResponseEntity<Map<String, Object>> buscarPalavra(@RequestParam String texto) {
        String busca = texto.trim();
        Map<String, Object> response = new HashMap<>();
        Optional<Palavra> exata = palavraRepository.findFirstByTextoIgnoreCaseOrderByIdAsc(busca);
        if (exata.isPresent()) {
            response.put("encontrada", true);
            response.put("exata", true);
            response.put("palavra", exata.orElseThrow());
            return ResponseEntity.ok(response);
        }
        List<Palavra> similares = palavraRepository.findTop5ByTextoContainingIgnoreCaseAndAtivaTrue(busca);
        response.put("encontrada", false);
        response.put("exata", false);
        response.put("similares", similares);
        return ResponseEntity.ok(response);
    }

    // Corpo do cadastro rápido: a palavra digitada e a dificuldade marcada pelo professor
    public record SugestaoPalavraPayload(String texto, String dificuldade) {}

    // Cadastro rápido feito na tela de criação de sala: palavra que não existe no
    // banco entra como INATIVA - vale como palavra extra da sala de quem cadastrou,
    // mas fica fora dos sorteios e da Palavra do Dia até um admin ativá-la.
    // Se a palavra já existir (qualquer caixa), devolve a existente: nunca duplica.
    @PostMapping("/sugerir")
    public ResponseEntity<Palavra> sugerirPalavra(@RequestBody SugestaoPalavraPayload payload) {
        if (payload == null || payload.texto() == null || payload.texto().isBlank()) {
            throw new BadRequestAlertException("Texto vazio", ENTITY_NAME, "textovazio");
        }
        String texto = payload.texto().trim().toLowerCase();
        if (texto.length() > 60) {
            throw new BadRequestAlertException("Palavra longa demais", ENTITY_NAME, "textolongo");
        }
        // Já existe (mesmo inativa)? Devolve a existente - também cobre a corrida de
        // duas pessoas cadastrando a mesma palavra quase ao mesmo tempo
        Optional<Palavra> existente = palavraRepository.findFirstByTextoIgnoreCaseOrderByIdAsc(texto);
        if (existente.isPresent()) {
            return ResponseEntity.ok(existente.orElseThrow());
        }
        Dificuldade dificuldade;
        try {
            dificuldade = Dificuldade.valueOf(payload.dificuldade().trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestAlertException("Dificuldade inválida", ENTITY_NAME, "dificuldadeinvalida");
        }
        Palavra palavra = new Palavra();
        palavra.setTexto(texto);
        palavra.setAtiva(false);
        palavra.setDificuldadeCadastrada(dificuldade);
        palavra.setIdioma("PT");
        String semAcento = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replaceAll(
            "\\p{InCombiningDiacriticalMarks}+",
            ""
        );
        palavra.setPossuiAcento(!semAcento.equals(texto));
        // Registra quem sugeriu (facilita a curadoria do admin depois)
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .ifPresent(palavra::setCriador);
        return ResponseEntity.ok(palavraRepository.save(palavra));
    }

    // Sorteia palavras ativas de uma dificuldade, excluindo ids já escolhidos.
    // Usado na tela de criação de sala: o professor vê as palavras sorteadas e pode
    // trocar uma por outra ("gerar outra") sem repetir as que já estão na lista.
    @GetMapping("/sortear")
    public ResponseEntity<List<Palavra>> sortearPalavras(
        @RequestParam String dificuldade,
        @RequestParam(defaultValue = "1") int quantidade,
        @RequestParam(required = false) List<Long> excluirIds
    ) {
        Dificuldade dif;
        try {
            dif = Dificuldade.valueOf(dificuldade.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("Dificuldade inválida", ENTITY_NAME, "dificuldadeinvalida");
        }
        int n = Math.max(1, Math.min(30, quantidade));
        // NOT IN vazio quebra o SQL nativo - usa um id impossível como sentinela
        List<Long> excluir = excluirIds == null || excluirIds.isEmpty() ? List.of(-1L) : excluirIds;
        return ResponseEntity.ok(palavraRepository.findRandomByDificuldadeExcluindo(dif.name(), n, excluir));
    }

    /**
     * {@code POST  /palavras} : Create a new palavra.
     *
     * @param palavra the palavra to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new palavra, or with status {@code 400 (Bad Request)} if the palavra has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    // Curadoria do banco de palavras é restrita a administradores: as estatísticas
    // (que definem a dificuldade e alimentam o jogo) são compartilhadas por todos,
    // então nenhum aluno/professor pode criar, alterar ou apagar palavras do acervo.
    @Secured(AuthoritiesConstants.ADMIN)
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
    @Secured(AuthoritiesConstants.ADMIN)
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
    @Secured(AuthoritiesConstants.ADMIN)
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
                // dificuldade cadastrada: vale enquanto a palavra tem menos de 15
                // tentativas; depois disso a taxa de acerto assume
                if (palavra.getDificuldadeCadastrada() != null) {
                    existingPalavra.setDificuldadeCadastrada(palavra.getDificuldadeCadastrada());
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
    @Secured(AuthoritiesConstants.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePalavra(@PathVariable("id") Long id) {
        LOG.debug("REST request to delete Palavra : {}", id);
        palavraRepository.deleteById(id);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
