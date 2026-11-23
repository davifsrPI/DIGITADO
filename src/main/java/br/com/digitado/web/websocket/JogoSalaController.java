package br.com.digitado.web.websocket;

import br.com.digitado.domain.enumeration.TipoSala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.TentativasCodigoSala;
import br.com.digitado.service.ConfiguracaoPartidaService;
import br.com.digitado.service.ConquistaEngineService;
import br.com.digitado.service.JogoSalaService;
import br.com.digitado.service.ParticipanteSalaService;
import br.com.digitado.web.websocket.dto.*;
import java.security.Principal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

// Controller WebSocket que recebe e processa todas as ações do jogo em tempo real.
// Cada método responde a uma mensagem STOMP enviada pelo cliente via /app/sala/{codigo}/acao.
// @Transactional garante que entidades JPA com lazy loading possam ser acessadas sem erro.
@Controller
@Transactional
public class JogoSalaController {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaController.class);

    private final JogoSalaService jogoService;
    private final SalaRepository salaRepository;
    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;
    private final SimpMessagingTemplate messaging;
    private final ConquistaEngineService conquistaEngine;
    private final ParticipanteSalaService participanteSalaService;
    private final ConfiguracaoPartidaService configuracaoPartidaService;
    private final TentativasCodigoSala tentativasCodigoSala;

    public JogoSalaController(
        JogoSalaService jogoService,
        SalaRepository salaRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        SimpMessagingTemplate messaging,
        ConquistaEngineService conquistaEngine,
        ParticipanteSalaService participanteSalaService,
        ConfiguracaoPartidaService configuracaoPartidaService,
        TentativasCodigoSala tentativasCodigoSala
    ) {
        this.jogoService = jogoService;
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.messaging = messaging;
        this.conquistaEngine = conquistaEngine;
        this.participanteSalaService = participanteSalaService;
        this.configuracaoPartidaService = configuracaoPartidaService;
        this.tentativasCodigoSala = tentativasCodigoSala;
    }

    // Maior código de sala que vale a pena procurar no banco: o de verdade tem 6
    private static final int MAX_CODIGO = 20;
    // Nome que o cliente manda ao entrar, quando não há outro: mesmo teto do apelido
    private static final int MAX_NOME_ENVIADO = 30;

    /**
     * A sala desta mensagem EXISTE?
     *
     * Toda mensagem /app/sala/{codigo}/... criava um estado de jogo em memória para o
     * código que viesse, existisse a sala ou não: uma conexão mandando códigos
     * inventados em série enchia a memória do servidor, e "entrar" respondia de um
     * jeito para sala real e de outro para inventada - um jeito de achar sala alheia
     * por tentativa. Agora código que não existe é ignorado e conta como chute para
     * a conta (o mesmo contador da API, ver TentativasCodigoSala).
     */
    private boolean salaExiste(String codigo, Principal principal) {
        if (principal == null || codigo == null) {
            return false;
        }
        String login = principal.getName();
        if (tentativasCodigoSala.bloqueado(login)) {
            return false;
        }
        if (jogoService.temJogoEmMemoria(codigo)) {
            return true;
        }
        if (codigo.length() <= MAX_CODIGO && salaRepository.existsById(codigo)) {
            return true;
        }
        LOG.warn(
            "WebSocket: {} mandou mensagem para a sala inexistente {}",
            login,
            codigo.length() > MAX_CODIGO ? "(código longo)" : codigo
        );
        tentativasCodigoSala.registrarFalha(login);
        return false;
    }

    /**
     * Nome público do jogador no placar - o que os COLEGAS veem.
     *
     * Vale primeiro a identificação que o aluno preencheu ao entrar NESTA sala
     * (o nome que ele digitou ou, se ele preferiu se esconder, o apelido). Sem
     * ela - professor, duelo 1v1, sala entrada antes desta tela existir - segue
     * a regra antiga: o apelido do perfil e, por último, o nome que o cliente
     * mandou. Tudo resolvido no servidor: um cliente adulterado não escolhe como
     * aparece para os outros, nem consegue exibir o nome real de quem optou pelo
     * apelido.
     */
    private String nomeExibicao(String codigoSala, String login, String nomeEnviado) {
        return participanteSalaService
            .nomePublico(codigoSala, login)
            .orElseGet(() ->
                userRepository
                    .findOneByLogin(login)
                    .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
                    .map(u -> u.getApelido())
                    .filter(a -> a != null && !a.isBlank())
                    .map(String::trim)
                    .orElse(nomeEnviado)
            );
    }

    // Registra um aluno (ou professor) na sala quando ele se conecta.
    // Em duelos 1v1 o servidor limita a 2 jogadores: o 3º recebe erro e não entra.
    // Depois de registrar, faz broadcast do estado atual para todos na sala.
    @MessageMapping("/sala/{codigo}/entrar")
    public void entrar(
        @DestinationVariable String codigo,
        @Payload EntradaAluno entrada,
        Principal principal,
        SimpMessageHeaderAccessor headers
    ) {
        if (!salaExiste(codigo, principal)) {
            if (principal != null) {
                messaging.convertAndSendToUser(
                    principal.getName(),
                    "/queue/sala/" + codigo + "/erro",
                    Map.of("tipo", "SALA_INEXISTENTE", "mensagem", "Esta sala não existe")
                );
            }
            return;
        }
        String login = principal.getName();
        // Id da sessão WebSocket desta conexão: é por sessão que o participante entra e
        // sai da sala, e não por login - ver JogoSalaService.aoDesconectar
        String sessaoId = headers != null ? headers.getSessionId() : null;
        String nomeSala = getNomeSala(codigo);
        String nomeEnviado = entrada != null ? entrada.nome() : null;
        if (nomeEnviado != null && nomeEnviado.length() > MAX_NOME_ENVIADO) {
            nomeEnviado = nomeEnviado.substring(0, MAX_NOME_ENVIADO);
        }
        String nome = nomeExibicao(codigo, login, nomeEnviado);
        boolean duelo = salaRepository.findByCodigo(codigo).map(s -> s.getTipo() == TipoSala.UM_V_UM).orElse(false);
        if (duelo) {
            boolean entrou = jogoService.registrarNoDuelo(codigo, login, nome, sessaoId);
            if (!entrou) {
                LOG.warn("Duelo {} cheio - entrada negada para {}", codigo, login);
                messaging.convertAndSendToUser(
                    login,
                    "/queue/sala/" + codigo + "/erro",
                    Map.of("tipo", "SALA_CHEIA", "mensagem", "Este duelo já está com 2 jogadores")
                );
                return;
            }
        } else if (isProfessorDaSala(codigo, principal)) {
            // O criador conduz a sala mas nao joga: fica de fora do placar e da lista de
            // alunos, para nao aparecer na tela de espera nem no ranking da turma. Mas a
            // CONEXAO dele conta: enquanto o professor esta na sala ela nao esta vazia e
            // nao pode ser fechada por falta de participantes.
            jogoService.registrarProfessor(codigo, login, nome, sessaoId);
        } else {
            jogoService.registrarAluno(codigo, login, nome, sessaoId);
        }
        // Conquista "Bem-vindo à Turma" (primeira sala) - nunca derruba a conexão
        try {
            conquistaEngine.aoEntrarNaSala(login);
        } catch (Exception e) {
            LOG.error("Falha ao processar conquista de entrada de {}: {}", login, e.getMessage(), e);
        }
        EstadoJogoDTO estado = jogoService.getEstado(codigo, nomeSala);
        broadcast(codigo, estado);
    }

    // Inicia o jogo com a configuração escolhida pelo professor (quantidade de palavras, tempo limite).
    // Verifica antes se o usuário é realmente o professor da sala; se não for, envia um erro via WebSocket.
    @MessageMapping("/sala/{codigo}/iniciar")
    public void iniciar(@DestinationVariable String codigo, @Payload IniciarPayload payload, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Iniciar negado para sala {} - usuário: {}", codigo, principal != null ? principal.getName() : "anônimo");
            if (principal != null) {
                // Manda o erro direto para o usuário que tentou iniciar sem permissão
                messaging.convertAndSendToUser(
                    principal.getName(),
                    "/queue/sala/" + codigo + "/erro",
                    Map.of("tipo", "NAO_AUTORIZADO", "mensagem", "Sem permissão para iniciar esta sala")
                );
            }
            return;
        }
        String nomeSala = getNomeSala(codigo);
        // Grava na sala a configuração com que a partida está começando: os ajustes
        // que o professor fez na tela de espera passam a ser o que ela tem guardado,
        // então recarregar a página devolve o que ele escolheu, e não o padrão
        configuracaoPartidaService.salvar(
            codigo,
            new ConfiguracaoPartidaService.ConfiguracaoPartida(
                payload.tempoFacil(),
                payload.tempoMedio(),
                payload.tempoDificil(),
                payload.qtdFacil(),
                payload.qtdMedio(),
                payload.qtdDificil(),
                payload.palavrasExtrasIds(),
                payload.palavrasIds()
            )
        );
        EstadoJogoDTO estado = jogoService.iniciar(codigo, nomeSala, payload, principal.getName());
        broadcast(codigo, estado);
    }

    /**
     * Passa para a próxima palavra na hora, a pedido de quem comanda a sala (botão
     * "Todos responderam"). Restrito ao professor da sala.
     *
     * O tempo normal da rodada NÃO depende mais desta mensagem: quem vira a rodada
     * quando o ranking acaba é o relógio do servidor (JogoSalaRodadaScheduler). Antes
     * era o navegador do professor que contava os 8 segundos e pedia a virada, e a aba
     * dele fechando deixava a turma parada no ranking para sempre.
     *
     * indiceAtual: a rodada que o cliente acha que está terminando. Serve para um
     * pedido atrasado (ou um clique junto com a virada do servidor) não pular uma
     * palavra. Opcional, para um cliente antigo ainda em cache continuar funcionando.
     */
    @MessageMapping("/sala/{codigo}/proxima")
    public void proxima(@DestinationVariable String codigo, @Payload(required = false) AvancoPayload payload, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn(
                "Tentativa não autorizada de avançar palavra em sala {} por {}",
                codigo,
                principal != null ? principal.getName() : "anônimo"
            );
            return;
        }
        String nomeSala = getNomeSala(codigo);
        Integer indiceEsperado = payload != null ? payload.indiceAtual() : null;
        EstadoJogoDTO estado = jogoService.proximaPalavra(codigo, nomeSala, principal.getName(), indiceEsperado);
        if (estado != null) broadcast(codigo, estado);
    }

    // Pausa o jogo - restrito ao professor
    @MessageMapping("/sala/{codigo}/pausar")
    public void pausar(@DestinationVariable String codigo, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Tentativa não autorizada de pausar sala {} por {}", codigo, principal != null ? principal.getName() : "anônimo");
            return;
        }
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.pausar(codigo, nomeSala);
        if (estado != null) broadcast(codigo, estado);
    }

    // Encerra o jogo antes do tempo - restrito ao professor
    @MessageMapping("/sala/{codigo}/encerrar")
    public void encerrar(@DestinationVariable String codigo, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Tentativa não autorizada de encerrar sala {} por {}", codigo, principal != null ? principal.getName() : "anônimo");
            return;
        }
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.encerrar(codigo, nomeSala, principal.getName());
        if (estado != null) broadcast(codigo, estado);
    }

    // Recebe a resposta digitada por um aluno (ou professor jogando).
    // Calcula acerto/erro e pontuação, manda o feedback só para quem respondeu,
    // e faz broadcast do placar atualizado para todos.
    @MessageMapping("/sala/{codigo}/responder")
    public void responder(@DestinationVariable String codigo, @Payload RespostaPayload payload, Principal principal) {
        // Resposta só existe para partida em andamento, que está em memória: nada de
        // ir ao banco nem criar estado para código inventado
        if (principal == null || payload == null || !jogoService.temJogoEmMemoria(codigo)) return;
        String login = principal.getName();
        String nomeSala = getNomeSala(codigo);
        int tentativasBurla = payload.tentativasBurla() != null ? payload.tentativasBurla() : 0;
        JogoSalaService.ResultadoResposta resultado = jogoService.responder(
            codigo,
            nomeSala,
            login,
            // fallback do nome público: o valor que vale é o resolvido na entrada da sala
            login,
            payload.respostaDigitada(),
            tentativasBurla
        );
        if (resultado == null) return;

        // Feedback vai apenas para quem respondeu (via user destination privada)
        messaging.convertAndSendToUser(login, "/queue/sala/" + codigo + "/feedback", resultado.feedback());

        // Estado COMPLETO só quando a sala inteira precisa: rodada fechada ou duelo
        // 1v1. Resposta recusada não mexe no placar e vem sem estado.
        if (resultado.estado() != null) {
            broadcast(codigo, resultado.estado());
        } else if (resultado.evento() != null) {
            // Rodada em andamento numa turma: o aviso leve vai SÓ para quem comanda a
            // sala, que é quem acompanha "quantos já responderam". Os aparelhos dos
            // alunos não recebem nada até a rodada fechar - durante a rodada a tela
            // deles mostra a pontuação congelada de propósito, então o placar que
            // chegava a cada resposta era 5 KB e um redesenho de tela por colega.
            String professor = jogoService.loginProfessorDaSala(codigo);
            if (professor != null) {
                messaging.convertAndSendToUser(professor, "/queue/sala/" + codigo + "/rodada", resultado.evento());
            }
        }
    }

    /**
     * Devolve o estado completo da sala SÓ para quem pediu.
     *
     * Durante a rodada o placar não é mais transmitido a cada resposta, então quando o
     * tempo esgota sem todo mundo ter respondido o aparelho está com a pontuação do
     * início da rodada. A tela de ranking pede o estado neste instante e recebe os
     * números novos - uma mensagem por aparelho por rodada, no lugar de uma por
     * resposta de cada colega.
     */
    @MessageMapping("/sala/{codigo}/estado")
    public void estado(@DestinationVariable String codigo, Principal principal) {
        if (!salaExiste(codigo, principal)) return;
        EstadoJogoDTO estado = jogoService.getEstado(codigo, getNomeSala(codigo));
        messaging.convertAndSendToUser(principal.getName(), "/queue/sala/" + codigo + "/estado", estado);
    }

    // Envia o estado do jogo para todos os participantes inscritos no tópico da sala
    private void broadcast(String codigo, EstadoJogoDTO estado) {
        messaging.convertAndSend("/topic/sala/" + codigo, estado);
    }

    // Nome da sala com cache em memória (EstadoJogo): o banco só é consultado na
    // primeira mensagem de cada sala - antes era um SELECT por mensagem WebSocket,
    // inclusive um por resposta de aluno durante a rodada. Fallback: o próprio código.
    private String getNomeSala(String codigo) {
        return jogoService.nomeSalaCacheado(codigo, () -> salaRepository.findByCodigo(codigo).map(s -> s.getNome()).orElse(codigo));
    }

    // Verifica se o usuário conectado tem permissão para controlar esta sala: compara
    // o e-mail do User autenticado com o e-mail do professor cadastrado na sala.
    // O admin NÃO comanda mais qualquer sala - a conta de administração nem conecta
    // no WebSocket do jogo (ver JogoSalaJwtInterceptor).
    private boolean isProfessorDaSala(String codigoSala, Principal principal) {
        if (principal == null || codigoSala == null || codigoSala.length() > MAX_CODIGO) return false;
        return salaRepository
            .findByCodigoWithProfessor(codigoSala)
            .map(sala ->
                userRepository
                    .findOneByLogin(principal.getName())
                    .map(user -> {
                        if (sala.getProfessor() == null) {
                            LOG.warn("Sala {} não tem professor definido - acesso negado para {}", codigoSala, user.getLogin());
                            return false;
                        }

                        // Compara pelo email: o User autenticado deve ter o mesmo e-mail que o Usuario professor da sala
                        boolean match = user.getEmail() != null && user.getEmail().equalsIgnoreCase(sala.getProfessor().getEmail());
                        if (!match) {
                            LOG.warn(
                                "Email mismatch na sala {}: user.email={}, professor.email={}",
                                codigoSala,
                                user.getEmail(),
                                sala.getProfessor().getEmail()
                            );
                        }
                        return match;
                    })
                    .orElse(false)
            )
            .orElse(false);
    }
}
