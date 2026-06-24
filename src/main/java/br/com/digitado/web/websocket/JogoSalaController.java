package br.com.digitado.web.websocket;

import br.com.digitado.repository.SalaRepository;
import br.com.digitado.service.JogoSalaService;
import br.com.digitado.web.websocket.dto.*;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class JogoSalaController {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaController.class);

    private final JogoSalaService jogoService;
    private final SalaRepository salaRepository;
    private final SimpMessagingTemplate messaging;

    public JogoSalaController(JogoSalaService jogoService, SalaRepository salaRepository, SimpMessagingTemplate messaging) {
        this.jogoService = jogoService;
        this.salaRepository = salaRepository;
        this.messaging = messaging;
    }

    @MessageMapping("/sala/{codigo}/entrar")
    public void entrar(@DestinationVariable String codigo, @Payload EntradaAluno entrada, Principal principal) {
        String nomeSala = getNomeSala(codigo);
        String login = principal != null ? principal.getName() : entrada.login();
        jogoService.registrarAluno(codigo, login, entrada.nome());
        EstadoJogoDTO estado = jogoService.getEstado(codigo, nomeSala);
        broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/iniciar")
    public void iniciar(@DestinationVariable String codigo, @Payload IniciarPayload payload, Principal principal) {
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.iniciar(codigo, nomeSala, payload);
        broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/proxima")
    public void proxima(@DestinationVariable String codigo, Principal principal) {
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.proximaPalavra(codigo, nomeSala);
        if (estado != null) broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/pausar")
    public void pausar(@DestinationVariable String codigo, Principal principal) {
        String nomeSala = getNomeSala(codigo);
        EstadoJogoDTO estado = jogoService.pausar(codigo, nomeSala);
        if (estado != null) broadcast(codigo, estado);
    }

    @MessageMapping("/sala/{codigo}/encerrar")
    public void encerrar(@DestinationVariable String codigo, Principal principal) {
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
}
