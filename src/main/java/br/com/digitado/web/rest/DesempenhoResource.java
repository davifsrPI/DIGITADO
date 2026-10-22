package br.com.digitado.web.rest;

import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.security.SecurityUtils;
import br.com.digitado.service.ComparadorPalavras;
import br.com.digitado.service.HistoricoRespostaService;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import br.com.digitado.web.rest.vm.DesempenhoVM.DesempenhoGeralVM;
import br.com.digitado.web.rest.vm.DesempenhoVM.MeuDesempenhoVM;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Painéis de desempenho.
 *
 * Duas visões e uma fronteira rígida entre elas:
 *
 * - {@code GET /api/meu-desempenho} devolve o histórico do PRÓPRIO usuário. Não
 *   existe parâmetro de login, id ou filtro: a identidade sai do token JWT e só
 *   dela. Não há endpoint algum que aceite "me dê o desempenho do fulano" - por
 *   construção, um aluno não consegue ver o de outro nem trocando a URL.
 *
 * - {@code GET /api/admin/desempenho} é o retrato agregado da base. Fica sob
 *   /api/admin/** de propósito: além do @Secured, a própria regra de URL da
 *   SecurityConfiguration já exige a role ADMIN - duas barreiras independentes.
 *   Traz números, nomes e o relatório do mês; nunca as respostas digitadas de
 *   ninguém.
 */
@RestController
@RequestMapping("/api")
public class DesempenhoResource {

    private static final Logger LOG = LoggerFactory.getLogger(DesempenhoResource.class);

    private final HistoricoRespostaService historicoRespostaService;

    public DesempenhoResource(HistoricoRespostaService historicoRespostaService) {
        this.historicoRespostaService = historicoRespostaService;
    }

    /**
     * {@code GET /api/meu-desempenho} : evolução do usuário autenticado desde a
     * primeira resposta - taxa de acerto, acelerador, palavras que mais erra e
     * que mais acerta.
     *
     * Sem histórico ainda, devolve o painel zerado (com a mensagem de boas-vindas)
     * em vez de erro - quem nunca jogou não é um caso de falha.
     */
    @GetMapping("/meu-desempenho")
    public ResponseEntity<MeuDesempenhoVM> getMeuDesempenho() {
        LOG.debug("REST request to get desempenho do usuário autenticado");
        // Único ponto de entrada da identidade: o token. Sessão sem login não passa
        // do PrivateRoute/filtro, mas se chegar aqui recebe 401 em vez de dados alheios.
        return SecurityUtils.getCurrentUserLogin()
            .map(login -> ResponseEntity.ok(historicoRespostaService.meuDesempenho(login)))
            .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /**
     * {@code GET /api/admin/desempenho} : painel do administrador - como está o
     * desempenho da base inteira e se o mês corrente teve mais ou menos acertos
     * que o anterior.
     */
    @Secured(AuthoritiesConstants.ADMIN)
    @GetMapping("/admin/desempenho")
    public DesempenhoGeralVM getDesempenhoGeral(@RequestParam(name = "tipoErro", required = false) String tipoErro) {
        LOG.debug("REST request to get desempenho geral (admin), tipoErro={}", tipoErro);
        // O filtro vai para SQL como parâmetro, nunca concatenado - mas um tipo fora
        // da lista é erro de quem chamou, e não um recorte vazio que pareça verdade
        if (tipoErro != null && !tipoErro.isBlank() && !TIPOS_DE_ERRO.contains(tipoErro.trim())) {
            throw new BadRequestAlertException("Tipo de erro desconhecido: " + tipoErro, "desempenho", "tipoerroinvalido");
        }
        return historicoRespostaService.desempenhoGeral(tipoErro);
    }

    /** Os tipos que o histórico grava (ComparadorPalavras), mais a rodada sem resposta. */
    private static final Set<String> TIPOS_DE_ERRO = Set.of(
        "ACENTUACAO",
        "TROCA_LETRA",
        "LETRA_FALTANDO",
        "LETRA_EXTRA",
        "ERRO_FONETICO",
        "OUTRO",
        ComparadorPalavras.NAO_RESPONDEU
    );
}
