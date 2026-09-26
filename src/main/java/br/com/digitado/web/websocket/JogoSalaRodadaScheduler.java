package br.com.digitado.web.websocket;

import br.com.digitado.service.JogoSalaService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O relógio da partida.
 *
 * Antes o tempo era contado no NAVEGADOR de quem comandava a sala: a tela mostrava
 * o ranking por 8 segundos e então pedia a próxima palavra ao servidor. Nada no
 * servidor virava a rodada por conta própria, então bastava a aba do professor
 * fechar, a rede cair ou o celular dele suspender a aba em segundo plano (os
 * navegadores móveis congelam temporizadores de aba escondida) para a turma inteira
 * ficar parada no ranking, sem fim. No duelo era pior: quem avançava era o cliente
 * de quem criou a sala, e o oponente ficava preso se ele saísse.
 *
 * Agora quem vira a rodada é este agendador, no mesmo processo do jogo. Quem comanda
 * a sala continua podendo passar a palavra na hora pelo botão "Todos responderam" - a
 * reserva em JogoSalaService.proximaPalavra garante que a rodada vire uma única vez,
 * venha o pedido do botão ou daqui.
 *
 * Roda de segundo em segundo: é bem mais fino que o tempo de ranking, e cada volta só
 * olha o estado em memória das salas ativas.
 */
@Component
public class JogoSalaRodadaScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaRodadaScheduler.class);

    private final JogoSalaService jogoService;
    private final SimpMessagingTemplate messaging;

    public JogoSalaRodadaScheduler(JogoSalaService jogoService, SimpMessagingTemplate messaging) {
        this.jogoService = jogoService;
        this.messaging = messaging;
    }

    /**
     * fixedDelay (e não fixedRate): uma volta só começa depois que a anterior
     * terminou. Com fixedRate, uma volta lenta - uma partida encerrando, que grava
     * estatísticas e processa conquistas - se sobreporia à seguinte.
     */
    @Scheduled(fixedDelay = 1000)
    public void virarRodadasVencidas() {
        try {
            List<JogoSalaService.RodadaAvancada> avancadas = jogoService.avancarRodadasVencidas();
            for (JogoSalaService.RodadaAvancada avancada : avancadas) {
                messaging.convertAndSend("/topic/sala/" + avancada.codigoSala(), avancada.estado());
                LOG.debug("Sala {}: rodada virada pelo relógio do servidor", avancada.codigoSala());
            }
        } catch (Exception e) {
            // O relógio não pode morrer: um erro numa volta não pode impedir as próximas
            LOG.error("Falha na volta do relógio das rodadas: {}", e.getMessage(), e);
        }
    }
}
