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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
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
    private final ObjectMapper objectMapper;

    public SalaResource(
        SalaRepository salaRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        JogoSalaService jogoSalaService,
        ObjectMapper objectMapper
    ) {
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.jogoSalaService = jogoSalaService;
        this.objectMapper = objectMapper;
    }

    // A coluna descricao guarda {"descricao": "<texto>", "modo": "1v1"|"normal"}.
    // Quem monta o JSON é o backend: o cliente manda só o texto e o modo vem do
    // tipo da sala, então não dá pra gravar outro modo.

    // pega só o texto da descrição, aceitando texto puro ou o JSON já montado
    // (caso o cliente devolva no PUT o objeto que veio do GET)
    private String extrairTextoDescricao(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String t = valor.trim();
        if (t.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(t);
                JsonNode texto = node.get("descricao");
                return texto == null || texto.isNull() ? null : texto.asText();
            } catch (com.fasterxml.jackson.core.JacksonException e) {
                return valor; // não era JSON válido — trata como texto puro
            }
        }
        return valor;
    }

    // Monta o JSON final da coluna a partir do texto e do tipo da sala
    private String montarDescricaoJson(String descricaoOriginal, TipoSala tipo) {
        ObjectNode node = objectMapper.createObjectNode();
        String texto = extrairTextoDescricao(descricaoOriginal);
        if (texto == null) {
            node.putNull("descricao");
        } else {
            node.put("descricao", texto);
        }
        node.put("modo", tipo == TipoSala.UM_V_UM ? "1v1" : "normal");
        return node.toString();
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
        // A descrição vai para o banco como JSON {"descricao": texto, "modo": ...}
        sala.setDescricao(montarDescricaoJson(sala.getDescricao(), sala.getTipo()));
        sala = salaRepository.save(sala);
        // Retorna apenas os campos públicos da sala (sem o professor, para não vazar dados)
        SalaResponseVM vm = toVM(sala);
        return ResponseEntity.created(new URI("/api/salas/" + sala.getCodigo()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, sala.getCodigo()))
            .body(vm);
    }

    // Atualiza a sala COMPLETA (PUT) — exclusivo do CRUD administrativo: o corpo
    // substitui a entidade inteira (inclusive professor, tipo e dataCriacao), o
    // que não pode ficar nas mãos do dono comum (ele poderia, por exemplo,
    // transferir a sala de professor pelo payload). O caminho do professor é o
    // PATCH abaixo, que copia somente os campos editáveis sobre o registro do banco.
    @Secured(AuthoritiesConstants.ADMIN)
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
        // Reembrulha a descrição em JSON com o modo do tipo enviado (default TURMA)
        sala.setDescricao(montarDescricaoJson(sala.getDescricao(), sala.getTipo()));
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
                    // O modo dentro do JSON segue o tipo REAL da sala no banco (PATCH não muda tipo)
                    existingSala.setDescricao(montarDescricaoJson(sala.getDescricao(), existingSala.getTipo()));
                }
                if (sala.getAtivo() != null) {
                    existingSala.setAtivo(sala.getAtivo());
                    // Sala fechada em definitivo (ativo=false): libera o estado do jogo
                    // da memória — placar, palavras e relatório não serão mais consultados
                    if (Boolean.FALSE.equals(sala.getAtivo())) {
                        jogoSalaService.descartarSala(codigo);
                    }
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
    // admin vê todas (entidade completa, para as telas CRUD); professores e
    // alunos veem apenas as salas que lhes pertencem, já convertidas no VM
    // público — a entidade crua carrega o vínculo com o professor e, se algum
    // dia for serializada inicializada, vazaria o e-mail dele
    @GetMapping("")
    public ResponseEntity<List<?>> getAllSalas(@RequestParam(required = false) Boolean ativo) {
        LOG.debug("REST request to get all Salas");
        boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
        if (isAdmin) {
            if (ativo != null) return ResponseEntity.ok(salaRepository.findByAtivo(ativo));
            return ResponseEntity.ok(salaRepository.findAll());
        }
        // Para usuários comuns: une as salas onde é professor com as salas onde é aluno
        List<SalaResponseVM> salas = SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(usuario -> {
                List<Sala> minhas = new java.util.ArrayList<>(usuario.getSalas());
                minhas.addAll(usuario.getSalasAlunos());
                if (ativo != null) {
                    minhas.removeIf(s -> !ativo.equals(s.getAtivo()));
                }
                return minhas.stream().map(this::toVM).toList();
            })
            .orElse(List.of());
        return ResponseEntity.ok(salas);
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
            // getDescricaoJson: garante JSON válido mesmo para valor legado em texto puro
            sala.getDescricaoJson(),
            sala.getAtivo(),
            sala.getTipo() != null ? sala.getTipo().name() : TipoSala.TURMA.name(),
            sala.getPrivada(),
            jogoSalaService.conectadosNaSala(sala.getCodigo())
        );
    }

    // Busca uma sala pelo código — sem restrição de acesso (código é público para
    // quem tiver o link). Não-admin recebe o VM público; a entidade completa
    // (com vínculos de professor/alunos) fica restrita às telas CRUD do admin.
    @GetMapping("/{codigo}")
    public ResponseEntity<?> getSala(@PathVariable("codigo") String codigo) {
        LOG.debug("REST request to get Sala : {}", codigo);
        Optional<Sala> sala = salaRepository.findById(codigo);
        if (SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            return ResponseUtil.wrapOrNotFound(sala);
        }
        return ResponseUtil.wrapOrNotFound(sala.map(this::toVM));
    }

    /**
     * Relatório da partida da sala: cada palavra JÁ JOGADA com as respostas
     * digitadas de cada jogador (quem escreveu o quê, acertou ou não, ordem de
     * chegada) e os totais por palavra.
     *
     * Usado pela tela do professor em dois momentos:
     * - DURANTE a partida: alimenta o painel de palavras (quantos responderam
     *   e % de acerto por palavra);
     * - AO FINAL: vira o relatório completo, junto com o ranking.
     *
     * Restrito ao professor dono da sala (ou admin): o relatório expõe o texto
     * das palavras e as respostas individuais dos alunos — nenhum aluno pode
     * consultar este endpoint para colar ou espiar os colegas.
     */
    @GetMapping("/{codigo}/relatorio")
    public ResponseEntity<List<JogoSalaService.RelatorioPalavra>> getRelatorio(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        return ResponseEntity.ok(jogoSalaService.gerarRelatorio(codigo));
    }

    // O papel de professor vive no estado de navegação do front e se perde ao
    // recarregar a página da sala — este endpoint permite à tela redescobrir se o
    // usuário logado é o dono (ou admin) e renderizar a visão de professor de novo.
    @GetMapping("/{codigo}/sou-professor")
    public ResponseEntity<Map<String, Boolean>> souProfessor(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        return ResponseEntity.ok(Map.of("souProfessor", isOwnerOrAdmin(codigo)));
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
        // Sala excluída do banco: o estado em memória do jogo também não tem mais dono
        jogoSalaService.descartarSala(codigo);
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
