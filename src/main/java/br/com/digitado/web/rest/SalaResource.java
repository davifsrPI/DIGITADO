package br.com.digitado.web.rest;

import br.com.digitado.domain.ParticipanteSala;
import br.com.digitado.domain.Sala;
import br.com.digitado.domain.enumeration.TipoSala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.security.SecurityUtils;
import br.com.digitado.service.CodigoSalaService;
import br.com.digitado.service.ConfiguracaoPartidaService;
import br.com.digitado.service.EstatisticaPartidaService;
import br.com.digitado.service.JogoSalaService;
import br.com.digitado.service.PalavraAudioService;
import br.com.digitado.service.ParticipanteSalaService;
import br.com.digitado.service.ResumoPartidaService;
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
import java.util.Set;
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
// A sala é identificada pelo código de acesso (PK) - não existe id numérico.
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
    private final EstatisticaPartidaService estatisticaPartidaService;
    private final ParticipanteSalaService participanteSalaService;
    private final ResumoPartidaService resumoPartidaService;
    private final CodigoSalaService codigoSalaService;
    private final ConfiguracaoPartidaService configuracaoPartidaService;
    private final PalavraAudioService palavraAudioService;
    private final ObjectMapper objectMapper;

    public SalaResource(
        SalaRepository salaRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        JogoSalaService jogoSalaService,
        EstatisticaPartidaService estatisticaPartidaService,
        ParticipanteSalaService participanteSalaService,
        ResumoPartidaService resumoPartidaService,
        CodigoSalaService codigoSalaService,
        ConfiguracaoPartidaService configuracaoPartidaService,
        PalavraAudioService palavraAudioService,
        ObjectMapper objectMapper
    ) {
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.jogoSalaService = jogoSalaService;
        this.estatisticaPartidaService = estatisticaPartidaService;
        this.participanteSalaService = participanteSalaService;
        this.resumoPartidaService = resumoPartidaService;
        this.codigoSalaService = codigoSalaService;
        this.configuracaoPartidaService = configuracaoPartidaService;
        this.palavraAudioService = palavraAudioService;
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
                return valor; // não era JSON válido - trata como texto puro
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

    /**
     * Um código de sala livre, sorteado pelo SERVIDOR.
     *
     * A tela de criação pede este código em vez de sortear um por conta própria:
     * o formato (alfabeto sem O/I/1/0 e os 6 caracteres) mora num lugar só, o
     * CodigoSalaService, e o que chega aqui já foi conferido contra o banco.
     *
     * Caminho de dois segmentos de propósito: /api/salas/{codigo} tem um só, então
     * não há como esta rota ser confundida com a busca de uma sala chamada
     * "codigo" (mesma forma de /api/salas/1v1/publicas).
     */
    @GetMapping("/codigo/novo")
    public ResponseEntity<Map<String, String>> novoCodigo() {
        return ResponseEntity.ok(Map.of("codigo", codigoSalaService.gerarDisponivel()));
    }

    // Cria uma nova sala. Automaticamente associa o usuário logado como professor da sala,
    // buscando o Usuario correspondente pelo e-mail do User autenticado.
    @PostMapping("")
    public ResponseEntity<SalaResponseVM> createSala(@Valid @RequestBody Sala sala) throws URISyntaxException {
        LOG.debug("REST request to save Sala : {}", sala);
        // Impede criação com código duplicado - o código é a chave primária
        if (salaRepository.existsById(sala.getCodigo())) {
            throw new BadRequestAlertException("Código de sala já em uso", ENTITY_NAME, "codigoexists");
        }
        // Vincula o professor logado à sala - se não houver Usuario correspondente, professor fica null
        SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .ifPresent(sala::setProfessor);
        // Data de criação é definida pelo servidor - o cliente não consegue forjar
        sala.setDataCriacao(java.time.Instant.now());
        // Normaliza tipo/visibilidade: sem tipo vira TURMA; a escolha pública/privada
        // só existe para duelos 1v1 - salas de turma são sempre acessadas pelo código
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
        // Configuração da partida (tempos, quantidades, palavras escolhidas): gravada
        // na sala, e não só no estado de navegação do cliente, que se perdia no
        // primeiro recarregar da tela. Os números vêm do navegador, então passam pela
        // normalização antes de virar uma partida.
        sala.setConfiguracao(configuracaoPartidaService.serializar(configuracaoPartidaService.ler(sala.getConfiguracao()).orElse(null)));
        sala = salaRepository.save(sala);
        // Retorna apenas os campos públicos da sala (sem o professor, para não vazar dados)
        SalaResponseVM vm = toVM(sala);
        return ResponseEntity.created(new URI("/api/salas/" + sala.getCodigo()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, sala.getCodigo()))
            .body(vm);
    }

    // Atualiza a sala COMPLETA (PUT) - exclusivo do CRUD administrativo: o corpo
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

    // Atualização parcial da sala (PATCH) - apenas campos enviados são alterados.
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
                // Sala NÃO FECHA MAIS: só a reabertura passa daqui. Fechada, ela
                // sumia das listagens e nem o dono entrava - e com ela ia embora o
                // caminho para o desempenho da turma. Um ativo=false que ainda chegue
                // (cliente antigo em cache) é ignorado de propósito.
                if (Boolean.TRUE.equals(sala.getAtivo())) {
                    existingSala.setAtivo(true);
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
    // público - a entidade crua carrega o vínculo com o professor e, se algum
    // dia for serializada inicializada, vazaria o e-mail dele
    //
    // meus=true: quem pede é a tela "Minhas Salas", que precisa SEMPRE do VM.
    // Sem este parâmetro o admin caía na listagem crua de entidades acima - que
    // não tem temEstatisticas - e o botão "Ver estatísticas" nunca aparecia no
    // card: a sala fechada parecia ter perdido a partida. O conjunto de salas
    // que cada um enxerga continua o mesmo de antes (o admin, todas).
    @GetMapping("")
    public ResponseEntity<List<?>> getAllSalas(
        @RequestParam(required = false) Boolean ativo,
        @RequestParam(required = false) Boolean meus
    ) {
        LOG.debug("REST request to get all Salas");
        boolean isAdmin = SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
        if (isAdmin && !Boolean.TRUE.equals(meus)) {
            if (ativo != null) return ResponseEntity.ok(salaRepository.findByAtivo(ativo));
            return ResponseEntity.ok(salaRepository.findAll());
        }
        List<Sala> minhas;
        if (isAdmin) {
            minhas = ativo != null ? salaRepository.findByAtivo(ativo) : salaRepository.findAll();
        } else {
            // Para usuários comuns: une as salas onde é professor com as salas onde é aluno
            minhas = SecurityUtils.getCurrentUserLogin()
                .flatMap(userRepository::findOneByLogin)
                .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
                .map(usuario -> {
                    List<Sala> doUsuario = new java.util.ArrayList<>(usuario.getSalas());
                    doUsuario.addAll(usuario.getSalasAlunos());
                    if (ativo != null) {
                        doUsuario.removeIf(s -> !ativo.equals(s.getAtivo()));
                    }
                    return doUsuario;
                })
                .orElse(List.of());
        }
        // Quais destas salas guardam estatísticas de uma partida encerrada, numa
        // consulta só - é o que decide o botão "Ver estatísticas" no card
        Set<String> comEstatisticas = estatisticaPartidaService.salasComEstatisticas(minhas.stream().map(Sala::getCodigo).toList());
        return ResponseEntity.ok(minhas.stream().map(s -> toVM(s, comEstatisticas.contains(s.getCodigo()))).toList());
    }

    // Lista global de duelos 1v1 PÚBLICOS abertos - qualquer usuário autenticado pode ver
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

    // Converte a entidade para o VM público, anexando quantos jogadores estão conectados agora.
    // temEstatisticas fica em false: só a listagem "Minhas Salas" precisa do dado e
    // ela usa a sobrecarga abaixo, com o resultado de UMA consulta para todas as salas.
    private SalaResponseVM toVM(Sala sala) {
        return toVM(sala, false);
    }

    private SalaResponseVM toVM(Sala sala, boolean temEstatisticas) {
        return new SalaResponseVM(
            sala.getCodigo(),
            sala.getNome(),
            // getDescricaoJson: garante JSON válido mesmo para valor legado em texto puro
            sala.getDescricaoJson(),
            sala.getAtivo(),
            sala.getTipo() != null ? sala.getTipo().name() : TipoSala.TURMA.name(),
            sala.getPrivada(),
            jogoSalaService.conectadosNaSala(sala.getCodigo()),
            temEstatisticas
        );
    }

    // Busca uma sala pelo código - sem restrição de acesso (código é público para
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
     * das palavras e as respostas individuais dos alunos - nenhum aluno pode
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

    /**
     * Estatísticas da ÚLTIMA partida encerrada da sala: ranking final e relatório
     * por palavra, lidos do snapshot gravado no banco quando a partida acabou.
     *
     * É a tela "Ver estatísticas" do professor. Diferente de /relatorio (que lê o
     * jogo em memória e some quando a sala é fechada ou esvazia), este endpoint
     * responde com a sala aberta ou fechada, e depois de reiniciar o servidor.
     *
     * Restrito ao professor dono (ou admin): expõe as respostas individuais dos
     * alunos, como o relatório ao vivo.
     */
    @GetMapping("/{codigo}/estatisticas")
    public ResponseEntity<EstatisticaPartidaService.EstatisticasPartida> getEstatisticas(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        // Sem partida encerrada ainda: 404 e a tela mostra o estado vazio
        return ResponseUtil.wrapOrNotFound(estatisticaPartidaService.buscar(codigo));
    }

    /**
     * O ÁUDIO da palavra da rodada - som, nunca texto.
     *
     * É por aqui que o aluno ouve o ditado depois que o texto da palavra parou de ser
     * transmitido no estado do jogo (ver JogoSalaService.textoTransmissivel). Antes o
     * texto ia no tópico da sala, que todo aluno assina, e a resposta chegava a ele
     * antes de responder - bastava abrir o console.
     *
     * Abre para qualquer participante CONECTADO na sala: é o mesmo grupo que já ouve a
     * palavra, e o corpo da resposta é um WAV. Quem não está na sala não passa.
     */
    @GetMapping("/{codigo}/audio")
    public ResponseEntity<byte[]> getAudioDaPalavra(@PathVariable("codigo") String codigo) {
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (login == null || !jogoSalaService.estaNaSala(codigo, login)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        return jogoSalaService
            .palavraAtualDaSala(codigo)
            .flatMap(palavra -> palavraAudioService.audio(palavra.getId()))
            .map(audio ->
                ResponseEntity.ok()
                    .header("Content-Type", PalavraAudioService.TIPO_AUDIO)
                    // Nada de cache: a palavra muda a cada rodada e o navegador não pode
                    // devolver o áudio da anterior
                    .header("Cache-Control", "no-store")
                    .body(audio)
            )
            // 404 quando não há áudio: aí o texto está sendo transmitido no estado do
            // jogo e a tela do aluno usa a voz do próprio navegador
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * O TEXTO da palavra da rodada, só para quem comanda a sala.
     *
     * O professor precisa dele para ditar a palavra à turma pela caixa de som e para
     * acompanhar o painel. Ele saía junto no estado do jogo, mas o estado é
     * transmitido para o tópico que os ALUNOS também assinam - então o texto passou a
     * vir por aqui, onde dá para exigir que seja o dono da sala.
     */
    @GetMapping("/{codigo}/palavra-atual")
    public ResponseEntity<Map<String, String>> getPalavraAtual(@PathVariable("codigo") String codigo) {
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        return jogoSalaService
            .palavraAtualDaSala(codigo)
            .map(palavra -> ResponseEntity.ok(Map.of("texto", palavra.getTexto())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Configuração da partida desta sala: tempos, quantidades por dificuldade e as
     * palavras escolhidas na criação.
     *
     * Restrito ao dono (ou admin) por um motivo concreto: palavrasIds são as
     * palavras que a partida vai usar, ou seja, as RESPOSTAS - um aluno com acesso
     * a isto consultaria o acervo antes do ditado.
     *
     * É o que a tela de espera lê ao abrir. Antes ela dependia do estado de
     * navegação do React Router: recarregar a página perdia a lista de palavras que
     * o professor tinha conferido uma por uma, e a partida sorteava outras.
     */
    @GetMapping("/{codigo}/configuracao")
    public ResponseEntity<ConfiguracaoPartidaService.ConfiguracaoPartida> getConfiguracao(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        // Sala criada antes desta coluna existir: devolve o padrão, que é o mesmo
        // que a tela mostrava quando não recebia configuração nenhuma
        return ResponseEntity.ok(configuracaoPartidaService.buscar(codigo).orElseGet(ConfiguracaoPartidaService::padrao));
    }

    /**
     * As palavras guardadas nesta sala: as sorteadas na criação e as que o professor
     * escolheu a mão no acervo.
     *
     * A configuração guarda só os ids, e quem prepara a sala numa segunda-feira para
     * usar na quinta não tem como lembrar o que caiu no sorteio. Este endpoint
     * devolve o texto de cada uma, para a tela de espera listar a atividade inteira
     * antes de começar.
     *
     * Restrito ao dono (ou admin) pelo mesmo motivo da configuração: são as RESPOSTAS
     * do ditado.
     */
    @GetMapping("/{codigo}/palavras")
    public ResponseEntity<ConfiguracaoPartidaService.PalavrasDaSala> getPalavras(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        return ResponseEntity.ok(configuracaoPartidaService.palavras(codigo));
    }

    /**
     * Identificação do aluno NESTA sala: nome de verdade, turma e a escolha de
     * aparecer para os colegas pelo apelido.
     *
     * É a tela que abre logo depois de o aluno digitar o código. Cada um grava
     * apenas a própria identificação (o login vem do token, não do corpo), e o
     * nome de verdade nunca é devolvido a outro aluno - para isso existe a
     * listagem /participantes, restrita ao dono da sala.
     */
    @PutMapping("/{codigo}/identificacao")
    public ResponseEntity<ParticipanteSalaService.Identificacao> salvarIdentificacao(
        @PathVariable("codigo") String codigo,
        @RequestBody ParticipanteSalaService.Identificacao dados
    ) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        String login = SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new BadRequestAlertException("Usuário não autenticado", ENTITY_NAME, "naoautenticado"));
        try {
            ParticipanteSala salvo = participanteSalaService.salvar(codigo, login, dados);
            return ResponseEntity.ok(
                new ParticipanteSalaService.Identificacao(salvo.getNome(), salvo.getTurma(), salvo.isUsarApelido(), salvo.getApelido())
            );
        } catch (ParticipanteSalaService.IdentificacaoInvalidaException e) {
            // Os três campos são obrigatórios DE VERDADE: a validação do formulário
            // no navegador não vale como garantia de que eles chegaram preenchidos
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "identificacaoinvalida");
        }
    }

    /**
     * A própria identificação do usuário logado nesta sala (404 quando ele ainda
     * não preencheu). A tela do jogo usa para saber se precisa mandar o aluno
     * para o formulário de entrada - inclusive depois de recarregar a página.
     */
    @GetMapping("/{codigo}/identificacao")
    public ResponseEntity<ParticipanteSalaService.Identificacao> minhaIdentificacao(@PathVariable("codigo") String codigo) {
        return ResponseUtil.wrapOrNotFound(
            SecurityUtils.getCurrentUserLogin()
                .flatMap(login -> participanteSalaService.buscar(codigo, login))
                .map(p -> new ParticipanteSalaService.Identificacao(p.getNome(), p.getTurma(), p.isUsarApelido(), p.getApelido()))
        );
    }

    /**
     * Quem se identificou nesta sala, com o NOME VERDADEIRO e a turma.
     *
     * Restrito ao professor dono (ou admin): o aluno que escolheu jogar de
     * apelido esconde o nome dos COLEGAS, e é este endpoint - e só ele - que
     * devolve o nome real, para o professor saber quem é quem no placar.
     */
    @GetMapping("/{codigo}/participantes")
    public ResponseEntity<List<ParticipanteSalaService.ParticipanteVM>> getParticipantes(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        return ResponseEntity.ok(participanteSalaService.listar(codigo));
    }

    /**
     * Resumo PESSOAL do aluno logado na partida desta sala: o que ele acertou, o
     * que errou, quantos acertos fez, a média da turma e quanta gente errou cada
     * palavra.
     *
     * Só devolve os dados DELE - as respostas dos colegas entram apenas como
     * percentual agregado, nunca nominalmente (para isso existe /relatorio, que
     * é do professor). Serve tanto à partida recém-encerrada quanto às antigas:
     * o relatório vem do jogo em memória e, quando ele já foi descartado, do
     * snapshot gravado no banco.
     */
    @GetMapping("/{codigo}/meu-resumo")
    public ResponseEntity<ResumoPartidaService.ResumoAluno> getMeuResumo(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        String login = SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new BadRequestAlertException("Usuário não autenticado", ENTITY_NAME, "naoautenticado"));
        return ResponseUtil.wrapOrNotFound(resumoDoAluno(codigo, login, false));
    }

    /**
     * Resumo de UM aluno, para o professor abrir a métrica de cada um a partir
     * do ranking da partida. Restrito ao dono da sala (ou admin) - é o mesmo
     * resumo que o aluno vê, aqui com o nome verdadeiro dele no cabeçalho.
     */
    @GetMapping("/{codigo}/resumo/{login}")
    public ResponseEntity<ResumoPartidaService.ResumoAluno> getResumoDoAluno(
        @PathVariable("codigo") String codigo,
        @PathVariable("login") String login
    ) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        if (!isOwnerOrAdmin(codigo)) {
            throw new BadRequestAlertException("Acesso negado", ENTITY_NAME, "forbidden");
        }
        return ResponseUtil.wrapOrNotFound(resumoDoAluno(codigo, login, true));
    }

    /**
     * Monta o resumo de um aluno na última partida da sala.
     *
     * A partida em memória tem a palavra da vez e vale enquanto a sala está
     * viva; quando o estado já foi descartado (todo mundo saiu, servidor
     * reiniciado) cai no snapshot - é o que faz o resumo existir também para as
     * salas jogadas há semanas.
     *
     * visaoProfessor: troca o nome público pelo nome verdadeiro no cabeçalho.
     */
    private Optional<ResumoPartidaService.ResumoAluno> resumoDoAluno(String codigo, String login, boolean visaoProfessor) {
        List<JogoSalaService.RelatorioPalavra> relatorio = jogoSalaService.gerarRelatorio(codigo);
        if (relatorio.isEmpty()) {
            relatorio = estatisticaPartidaService
                .buscar(codigo)
                .map(EstatisticaPartidaService.EstatisticasPartida::relatorio)
                .orElse(List.of());
        }
        Optional<ResumoPartidaService.ResumoAluno> resumo = resumoPartidaService.montar(relatorio, login, login);
        if (visaoProfessor) {
            // O professor vê o nome real (com o apelido entre parênteses quando o
            // aluno escolheu jogar escondido dos colegas)
            String nomeReal = participanteSalaService.nomesParaProfessor(codigo).get(login);
            if (nomeReal != null) {
                resumo = resumo.map(r ->
                    new ResumoPartidaService.ResumoAluno(
                        r.login(),
                        nomeReal,
                        r.totalPalavras(),
                        r.acertos(),
                        r.erros(),
                        r.semResposta(),
                        r.mediaAcertosTurma(),
                        r.totalParticipantes(),
                        r.palavras()
                    )
                );
            }
        }
        return resumo;
    }

    // O papel de professor vive no estado de navegação do front e se perde ao
    // recarregar a página da sala - este endpoint permite à tela redescobrir se o
    // usuário logado é o dono (ou admin) e renderizar a visão de professor de novo.
    @GetMapping("/{codigo}/sou-professor")
    public ResponseEntity<Map<String, Boolean>> souProfessor(@PathVariable("codigo") String codigo) {
        if (!salaRepository.existsById(codigo)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "codigonotfound");
        }
        return ResponseEntity.ok(Map.of("souProfessor", isOwnerOrAdmin(codigo)));
    }

    // Exclui uma sala - apenas o professor dono ou admin podem excluir
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
        // Sala excluída do banco: o estado em memória do jogo também não tem mais
        // dono. Aqui o descarte NÃO grava estatísticas - a sala não existe mais,
        // o snapshot ficaria órfão (a FK cascata já levou o antigo junto)
        jogoSalaService.descartarSalaSemSalvar(codigo);
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
