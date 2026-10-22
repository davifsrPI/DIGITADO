package br.com.digitado.web.rest;

import br.com.digitado.security.SecurityUtils;
import br.com.digitado.service.PalavraAudioService;
import br.com.digitado.service.PartidaIndividualService;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import br.com.digitado.web.rest.vm.IndividualVM.EstadoIndividualVM;
import br.com.digitado.web.rest.vm.IndividualVM.RecordesVM;
import br.com.digitado.web.rest.vm.IndividualVM.ResultadoIndividualVM;
import br.com.digitado.web.rest.vm.IndividualVM.ResumoIndividualVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Modo individual: o jogador sozinho, até onde conseguir chegar.
 *
 * A identidade sai SEMPRE do token: não há parâmetro de login em rota nenhuma, e
 * cada um só enxerga e responde a própria partida. Como no ditado das salas, o
 * texto da palavra não sai enquanto a rodada está aberta - o jogador ouve o áudio
 * em GET /audio. As regras ficam no PartidaIndividualService.
 */
@RestController
@RequestMapping("/api/individual")
public class PartidaIndividualResource {

    private static final Logger LOG = LoggerFactory.getLogger(PartidaIndividualResource.class);

    private static final String ENTITY_NAME = "partidaIndividual";

    /** Corpo do POST /responder. resposta vazia = não respondeu (tempo esgotado ou pulou). */
    public record RespostaIndividualPayload(String resposta, Integer tentativasBurla) {}

    private final PartidaIndividualService partidaIndividualService;

    public PartidaIndividualResource(PartidaIndividualService partidaIndividualService) {
        this.partidaIndividualService = partidaIndividualService;
    }

    /** Recorde pessoal, partida em aberto e as últimas partidas. */
    @GetMapping("/recordes")
    public RecordesVM recordes() {
        return partidaIndividualService.recordes(login());
    }

    /** Começa uma partida nova (a que estava aberta é encerrada como abandonada). */
    @PostMapping("/iniciar")
    public EstadoIndividualVM iniciar() {
        String login = login();
        LOG.debug("REST request to start individual game for {}", login);
        return executar(() -> partidaIndividualService.iniciar(login));
    }

    /** A partida em andamento - 404 quando não há nenhuma. */
    @GetMapping("/estado")
    public ResponseEntity<EstadoIndividualVM> estado() {
        return partidaIndividualService.estado(login()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** O som da palavra da rodada. 404 quando não há áudio: aí o texto vem no estado. */
    @GetMapping("/audio")
    public ResponseEntity<byte[]> audio() {
        return partidaIndividualService
            .audio(login())
            .map(audio ->
                ResponseEntity.ok()
                    .header("Content-Type", PalavraAudioService.TIPO_AUDIO)
                    // A palavra muda a cada rodada: o navegador não pode guardar a anterior
                    .header("Cache-Control", "no-store")
                    .body(audio)
            )
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/responder")
    public ResultadoIndividualVM responder(@RequestBody(required = false) RespostaIndividualPayload payload) {
        String login = login();
        String resposta = payload != null ? payload.resposta() : null;
        int burlas = payload != null && payload.tentativasBurla() != null ? Math.max(0, payload.tentativasBurla()) : 0;
        return executar(() -> partidaIndividualService.responder(login, resposta, burlas));
    }

    @PostMapping("/proxima")
    public EstadoIndividualVM proxima() {
        String login = login();
        return executar(() -> partidaIndividualService.proxima(login));
    }

    @PostMapping("/encerrar")
    public ResumoIndividualVM encerrar() {
        String login = login();
        return executar(() -> partidaIndividualService.encerrar(login));
    }

    private String login() {
        return SecurityUtils.getCurrentUserLogin()
            .orElseThrow(() -> new BadRequestAlertException("Usuário não autenticado", ENTITY_NAME, "naoautenticado"));
    }

    // Pedido fora de ordem vira 400 com a mensagem para a tela, e não um 500
    private static <T> T executar(java.util.function.Supplier<T> acao) {
        try {
            return acao.get();
        } catch (PartidaIndividualService.RodadaInvalidaException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "rodadainvalida");
        }
    }
}
