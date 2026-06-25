package br.com.digitado.web.websocket;

import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.security.AuthoritiesConstants;
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

@Controller
@Transactional
public class JogoSalaController {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaController.class);

    private final JogoSalaService jogoService;
    private final SalaRepository salaRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messaging;

    public JogoSalaController(
        JogoSalaService jogoService,
        SalaRepository salaRepository,
        UserRepository userRepository,
        SimpMessagingTemplate messaging
    ) {
        this.jogoService = jogoService;
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.messaging = messaging;
    }

    @MessageMapping("/sala/{codigo}/entrar")
    public void entrar(@DestinationVariable String codigo, @Payload EntradaAluno entrada, Principal principal) {
        if (principal == null) return;
        String login = principal.getName();
        String nomeSala = getNomeSala(codigo);
        jogoService.registrarAluno(codigo, login, entrada.nome());
        EstadoJogoDTO estado = jogoService.getEstado(codigo, nomeSala);
        broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/iniciar")
    public void iniciar(@DestinationVariable String codigo, @Payload IniciarPayload payload, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Iniciar negado para sala {} — usuário: {}", codigo, principal != null ? principal.getName() : "anônimo");
            if (principal != null) {
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
        EstadoJogoDTO estado = jogoService.proximaPalavra(codigo, nomeSala);
        if (estado != null) broadcast(codigo, estado);
    }

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

    @MessageMapping("/sala/{codigo}/encerrar")
    public void encerrar(@DestinationVariable String codigo, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Tentativa não autorizada de encerrar sala {} por {}", codigo, principal != null ? principal.getName() : "anônimo");
            return;
        }
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.encerrar(codigo, nomeSala);
        if (estado != null) broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/responder")
    public void responder(@DestinationVariable String codigo, @Payload RespostaPayload payload, Principal principal) {
        if (principal == null) return;
        String login = principal.getName();
        String nomeSala = getNomeSala(codigo);
        JogoSalaService.ResultadoResposta resultado = jogoService.responder(codigo, nomeSala, login, login, payload.respostaDigitada());
        if (resultado == null) return;

        messaging.convertAndSendToUser(login, "/queue/sala/" + codigo + "/feedback", resultado.feedback());
        broadcast(codigo, resultado.estado());
    }

    private void broadcast(String codigo, EstadoJogoDTO estado) {
        messaging.convertAndSend("/topic/sala/" + codigo, estado);
    }

    private String getNomeSala(String codigo) {
        return salaRepository.findByCodigo(codigo).map(s -> s.getNome()).orElse(codigo);
    }

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

                        // Compara pelo email: User.email == Usuario(professor).email
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
