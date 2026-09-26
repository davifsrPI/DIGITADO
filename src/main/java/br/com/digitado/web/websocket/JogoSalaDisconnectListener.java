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
 * 2. Quando o último participante sai, soltar o estado da sala da memória - o descarte
 *    GRAVA antes o snapshot da partida, então o desempenho da turma continua disponível.
 *
 * A sala NÃO é mais fechada no banco. Ela fechava sozinha aqui (ativo = false) assim que
 * esvaziava, e fechada sumia das listagens e não deixava ninguém entrar - nem o dono, que
 * perdia o caminho para o resumo da partida. A sala agora fica aberta para sempre; só o
 * estado em memória é liberado.
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
        // Sai por SESSÃO: recarregar a página abre a conexão nova antes de a antiga ser
        // encerrada, e remover o participante pelo login tirava da sala quem continuava
        // conectado - ver JogoSalaService.aoDesconectar
        JogoSalaService.ResultadoDesconexao resultado = jogoService.aoDesconectar(login, event.getSessionId());

        // Sala vazia: o estado em memória já foi descartado (com o snapshot gravado) dentro
        // de aoDesconectar. A sala em si continua ABERTA - o dono volta nela quando quiser.
        for (String codigo : resultado.salasVazias()) {
            LOG.info("Sala {} ficou sem participantes: estado liberado da memória, sala segue aberta", codigo);
        }

        // Nas salas que continuam com gente, atualiza a lista de conectados de quem ficou
        for (String codigo : resultado.salasComSaida()) {
            if (resultado.salasVazias().contains(codigo)) {
                continue; // estado já foi descartado - não há mais ninguém para avisar
            }
            String nomeSala = salaRepository.findByCodigo(codigo).map(Sala::getNome).orElse(codigo);
            messaging.convertAndSend("/topic/sala/" + codigo, jogoService.getEstado(codigo, nomeSala));
        }
    }
}
