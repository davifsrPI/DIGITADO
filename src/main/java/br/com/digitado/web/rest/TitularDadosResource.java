package br.com.digitado.web.rest;

import br.com.digitado.security.SecurityUtils;
import br.com.digitado.service.TitularDadosService;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Direitos do titular (LGPD art. 18) — endpoints de autoatendimento.
 *
 * Segurança:
 * - a identidade vem sempre do token, nenhum id vem do frontend;
 * - pra excluir a conta precisa da senha atual no corpo, então um token roubado
 *   sozinho não destrói a conta;
 * - a exportação nunca inclui a senha nem o hash.
 */
@RestController
@RequestMapping("/api/account")
public class TitularDadosResource {

    private static final Logger LOG = LoggerFactory.getLogger(TitularDadosResource.class);

    private final TitularDadosService titularDadosService;

    public TitularDadosResource(TitularDadosService titularDadosService) {
        this.titularDadosService = titularDadosService;
    }

    public record ExclusaoPayload(String senha) {}

    /**
     * {@code GET /api/account/export} : portabilidade (art. 18, V) — devolve todos
     * os dados pessoais do titular autenticado em JSON, para download no navegador.
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportar() {
        String login = loginAutenticado();
        LOG.info("LGPD: exportação de dados solicitada pelo titular '{}'", login);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados-digitado.json\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(titularDadosService.exportarDados(login));
    }

    /**
     * {@code DELETE /api/account} : exclusão da conta e de todos os dados pessoais
     * (art. 18, VI). Irreversível — por isso exige a senha atual como confirmação.
     */
    @DeleteMapping("")
    public ResponseEntity<Void> excluirConta(@RequestBody ExclusaoPayload payload) {
        String login = loginAutenticado();
        if (payload == null || !titularDadosService.senhaConfere(login, payload.senha())) {
            throw new BadRequestAlertException("Senha incorreta", "conta", "senhaincorreta");
        }
        titularDadosService.excluirConta(login);
        return ResponseEntity.noContent().build();
    }

    private String loginAutenticado() {
        return SecurityUtils.getCurrentUserLogin()
            .filter(l -> !"anonymousUser".equals(l))
            .orElseThrow(() -> new BadRequestAlertException("Não autenticado", "conta", "naoautenticado"));
    }
}
