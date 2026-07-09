package br.com.digitado.web.websocket;

import br.com.digitado.domain.Sala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.service.JogoSalaService;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Reage à desconexão de um cliente WebSocket (fechar a aba, voltar ao lobby, cair a rede).
 *
 * Duas responsabilidades:
 * 1. Remover o jogador da lista de conectados das salas em que estava e avisar quem ficou;
 * 2. Quando a partida já ENCERROU e o último participante sai, fechar a sala no banco
 *    (ativo = false) — a sala não volta a aparecer como aberta nem aceita novas entradas
 *    pela listagem; o dono ainda pode reabri-la em "Minhas Salas".
 */
@Component
public class JogoSalaDisconnectListener {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaDisconnectListener.class);

    private final JogoSalaService jogoService;
    private final SalaRepository salaRepository;
    private final SimpMessagingTemplate messaging;

    public JogoSalaDisconnectListener(JogoSalaService jogoService, SalaRepository salaRepository, SimpMessagingTemplate messaging) {
        this.jogoService = jogoService;
        this.salaRepository = salaRepository;
        this.messaging = messaging;
    }

    @EventListener
    public void aoDesconectar(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }
        String login = user.getName();
        JogoSalaService.ResultadoDesconexao resultado = jogoService.aoDesconectar(login);

        // Partida encerrada + sala vazia → fecha a sala no banco
        for (String codigo : resultado.salasEncerradasVazias()) {
            try {
                salaRepository
                    .findById(codigo)
                    .filter(sala -> !Boolean.FALSE.equals(sala.getAtivo()))
                    .ifPresent(sala -> {
                        sala.setAtivo(false);
                        salaRepository.save(sala);
                        LOG.info("Sala {} fechada automaticamente: partida encerrada e todos saíram", codigo);
                    });
            } catch (Exception e) {
                // Fechar a sala é manutenção — nunca pode derrubar o tratamento da desconexão
                LOG.error("Falha ao fechar sala {} após desconexão: {}", codigo, e.getMessage(), e);
            }
        }

        // Nas salas que continuam com gente, atualiza a lista de conectados de quem ficou
        for (String codigo : resultado.salasComSaida()) {
            if (resultado.salasEncerradasVazias().contains(codigo)) {
                continue; // estado já foi descartado — não há mais ninguém para avisar
            }
            String nomeSala = salaRepository.findByCodigo(codigo).map(Sala::getNome).orElse(codigo);
            messaging.convertAndSend("/topic/sala/" + codigo, jogoService.getEstado(codigo, nomeSala));
        }
    }
}
