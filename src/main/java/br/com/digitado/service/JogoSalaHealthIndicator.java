package br.com.digitado.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health check com status detalhado do domínio do jogo.
 *
 * Aparece em GET /management/health (componente "jogoSala") junto com os checks
 * padrão (db, diskSpace, ping). Os detalhes só são exibidos para ADMIN
 * (management.endpoint.health.show-details: when_authorized).
 *
 * Como o estado dos jogos vive em memória, estes números também servem de
 * contexto em incidentes: um restart com jogosEmAndamento > 0 derruba partidas.
 */
@Component
public class JogoSalaHealthIndicator implements HealthIndicator {

    private final JogoSalaService jogoSalaService;

    public JogoSalaHealthIndicator(JogoSalaService jogoSalaService) {
        this.jogoSalaService = jogoSalaService;
    }

    @Override
    public Health health() {
        return Health.up()
            .withDetail("salasEmMemoria", jogoSalaService.totalSalasEmMemoria())
            .withDetail("jogosEmAndamento", jogoSalaService.totalJogosEmAndamento())
            .withDetail("alunosConectados", jogoSalaService.totalAlunosConectados())
            .build();
    }
}
