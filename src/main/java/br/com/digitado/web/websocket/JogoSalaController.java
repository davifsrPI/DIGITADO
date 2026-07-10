package br.com.digitado.web.websocket;

import br.com.digitado.domain.enumeration.TipoSala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.service.ConquistaEngineService;
import br.com.digitado.service.JogoSalaService;
import br.com.digitado.web.websocket.dto.*;
import java.security.Principal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
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

    public JogoSalaController(
        JogoSalaService jogoService,
        SalaRepository salaRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        SimpMessagingTemplate messaging,
        ConquistaEngineService conquistaEngine
    ) {
        this.jogoService = jogoService;
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.messaging = messaging;
        this.conquistaEngine = conquistaEngine;
    }

    // Nome público do jogador no placar: o APELIDO cadastrado no perfil vence o nome
    // enviado pelo cliente (que fica só como fallback) — resolvido no servidor, um
    // cliente adulterado não escolhe como aparece para os outros
    private String nomeExibicao(String login, String nomeEnviado) {
        return userRepository
            .findOneByLogin(login)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
            .map(u -> u.getApelido())
            .filter(a -> a != null && !a.isBlank())
            .map(String::trim)
            .orElse(nomeEnviado);
    }

    // Registra um aluno (ou professor) na sala quando ele se conecta.
    // Em duelos 1v1 o servidor limita a 2 jogadores: o 3º recebe erro e não entra.
    // Depois de registrar, faz broadcast do estado atual para todos na sala.
    @MessageMapping("/sala/{codigo}/entrar")
    public void entrar(@DestinationVariable String codigo, @Payload EntradaAluno entrada, Principal principal) {
        if (principal == null) return;
        String login = principal.getName();
        String nomeSala = getNomeSala(codigo);
        String nome = nomeExibicao(login, entrada.nome());
        boolean duelo = salaRepository.findByCodigo(codigo).map(s -> s.getTipo() == TipoSala.UM_V_UM).orElse(false);
        if (duelo) {
            boolean entrou = jogoService.registrarNoDuelo(codigo, login, nome);
            if (!entrou) {
                LOG.warn("Duelo {} cheio — entrada negada para {}", codigo, login);
                messaging.convertAndSendToUser(
                    login,
                    "/queue/sala/" + codigo + "/erro",
                    Map.of("tipo", "SALA_CHEIA", "mensagem", "Este duelo já está com 2 jogadores")
                );
                return;
            }
        } else {
            jogoService.registrarAluno(codigo, login, nome);
        }
        // Conquista "Bem-vindo à Turma" (primeira sala) — nunca derruba a conexão
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
            LOG.warn("Iniciar negado para sala {} — usuário: {}", codigo, principal != null ? principal.getName() : "anônimo");
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
        EstadoJogoDTO estado = jogoService.iniciar(codigo, nomeSala, payload);
        broadcast(codigo, estado);
    }

    // Avança para a próxima palavra (chamado automaticamente pelo frontend quando o tempo esgota).
    // Restrito ao professor da sala.
    @MessageMapping("/sala/{codigo}/proxima")
    public void proxima(@DestinationVariable String codigo, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn(
                "Tentativa não autorizada de avançar palavra em sala {} por {}",
                codigo,
                principal != null ? principal.getName() : "anônimo"
            );
            return;
        }
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.proximaPalavra(codigo, nomeSala, principal.getName());
        if (estado != null) broadcast(codigo, estado);
    }

    // Pausa o jogo — restrito ao professor
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

    // Encerra o jogo antes do tempo — restrito ao professor
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
        if (principal == null) return;
        String login = principal.getName();
        String nomeSala = getNomeSala(codigo);
        JogoSalaService.ResultadoResposta resultado = jogoService.responder(codigo, nomeSala, login, login, payload.respostaDigitada());
        if (resultado == null) return;

        // Feedback vai apenas para quem respondeu (via user destination privada)
        messaging.convertAndSendToUser(login, "/queue/sala/" + codigo + "/feedback", resultado.feedback());
        // Placar atualizado vai para toda a sala
        broadcast(codigo, resultado.estado());
    }

    // Envia o estado do jogo para todos os participantes inscritos no tópico da sala
    private void broadcast(String codigo, EstadoJogoDTO estado) {
        messaging.convertAndSend("/topic/sala/" + codigo, estado);
    }

    // Busca o nome da sala pelo código; usa o código como fallback se não encontrar
    private String getNomeSala(String codigo) {
        return salaRepository.findByCodigo(codigo).map(s -> s.getNome()).orElse(codigo);
    }

    // Verifica se o usuário conectado tem permissão para controlar esta sala.
    // Admin sempre pode. Para outros usuários, compara o e-mail do User autenticado
    // com o e-mail do professor cadastrado na sala.
    private boolean isProfessorDaSala(String codigoSala, Principal principal) {
        if (principal == null) return false;
        return salaRepository
            .findByCodigoWithProfessor(codigoSala)
            .map(sala ->
                userRepository
                    .findOneByLogin(principal.getName())
                    .map(user -> {
                        // Admin pode controlar qualquer sala
                        boolean isAdmin = user.getAuthorities().stream().anyMatch(a -> a.getName().equals(AuthoritiesConstants.ADMIN));
                        if (isAdmin) return true;

                        if (sala.getProfessor() == null) {
                            LOG.warn("Sala {} não tem professor definido — acesso negado para {}", codigoSala, user.getLogin());
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
