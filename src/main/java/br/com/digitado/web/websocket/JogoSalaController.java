package br.com.digitado.web.websocket;

import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.service.JogoSalaService;
import br.com.digitado.web.websocket.dto.*;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final UsuarioRepository usuarioRepository;
    private final SimpMessagingTemplate messaging;

    public JogoSalaController(
        JogoSalaService jogoService,
        SalaRepository salaRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        SimpMessagingTemplate messaging
    ) {
        this.jogoService = jogoService;
        this.salaRepository = salaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.messaging = messaging;
    }

    @MessageMapping("/sala/{codigo}/entrar")
    public void entrar(String codigo, @Payload EntradaAluno entrada, Principal principal) {
        if (principal == null) return;
        String login = principal.getName();
        String nomeSala = getNomeSala(codigo);
        jogoService.registrarAluno(codigo, login, entrada.nome());
        EstadoJogoDTO estado = jogoService.getEstado(codigo, nomeSala);
        broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/iniciar")
    public void iniciar(String codigo, @Payload IniciarPayload payload, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Tentativa não autorizada de iniciar sala {} por {}", codigo, principal != null ? principal.getName() : "anônimo");
            return;
        }
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.iniciar(codigo, nomeSala, payload);
        broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/proxima")
    public void proxima(String codigo, Principal principal) {
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
    public void pausar(String codigo, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Tentativa não autorizada de pausar sala {} por {}", codigo, principal != null ? principal.getName() : "anônimo");
            return;
        }
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.pausar(codigo, nomeSala);
        if (estado != null) broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/encerrar")
    public void encerrar(String codigo, Principal principal) {
        if (!isProfessorDaSala(codigo, principal)) {
            LOG.warn("Tentativa não autorizada de encerrar sala {} por {}", codigo, principal != null ? principal.getName() : "anônimo");
            return;
        }
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.encerrar(codigo, nomeSala);
        if (estado != null) broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/responder")
    public void responder(String codigo, @Payload RespostaPayload payload, Principal principal) {
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
            .map(sala -> {
                if (sala.getProfessor() == null) return false;
                return userRepository
                    .findOneByLogin(principal.getName())
                    .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()))
                    .map(usuario -> usuario.getId().equals(sala.getProfessor().getId()))
                    .orElse(false);
            })
            .orElse(false);
    }
}
