package br.com.digitado.web.rest;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.PalavraDoDiaTentativa;
import br.com.digitado.security.SecurityUtils;
import br.com.digitado.service.PalavraDoDiaService;
import br.com.digitado.service.XpService;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import br.com.digitado.web.rest.vm.PalavraDoDiaVM;
import br.com.digitado.web.rest.vm.ResultadoPalavraDoDiaVM;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint PÚBLICO da Palavra do Dia (/api/public/** é permitAll no SecurityConfiguration).
 *
 * Regras de segurança, todas aplicadas no backend:
 * - A validação da resposta é feita aqui (o front só envia o palpite);
 * - O texto viaja no GET apenas para a síntese de voz do navegador (o desafio é
 *   um ditado) - quem inspecionar a rede consegue lê-lo, mas a chance única e a
 *   validação continuam no servidor;
 * - Uma chance por pessoa: conta logada é controlada pelo banco (dia + login);
 *   visitante anônimo é controlado por cookie httpOnly emitido pelo servidor
 *   (JavaScript do front não lê nem escreve esse cookie);
 * - Se o visitante fizer login, o cookie anônimo passa a ser ignorado e vale a
 *   chance da conta - exatamente o comportamento pedido.
 */
@RestController
@RequestMapping("/api/public/palavra-do-dia")
public class PalavraDoDiaResource {

    private static final Logger LOG = LoggerFactory.getLogger(PalavraDoDiaResource.class);

    // Cookie httpOnly que marca a tentativa de visitantes anônimos
    private static final String COOKIE_TENTATIVA = "pddTentativa";

    private final PalavraDoDiaService palavraDoDiaService;

    public PalavraDoDiaResource(PalavraDoDiaService palavraDoDiaService) {
        this.palavraDoDiaService = palavraDoDiaService;
    }

    // Corpo da tentativa: apenas o palpite digitado
    public record TentativaPayload(String resposta) {}

    /**
     * {@code GET /api/public/palavra-do-dia} : estado do desafio de hoje.
     * Retorna o texto para o áudio do ditado e se quem chamou já usou a chance
     * (banco para logado, cookie para anônimo) - o front apenas renderiza o que vier daqui.
     */
    @GetMapping("")
    public PalavraDoDiaVM getPalavraDoDia(@CookieValue(name = COOKIE_TENTATIVA, required = false) String cookie) {
        Optional<Palavra> palavraOpt = palavraDoDiaService.palavraDeHoje();
        if (palavraOpt.isEmpty()) {
            return new PalavraDoDiaVM(false, palavraDoDiaService.hoje(), 0, null, null, null, null, false, null);
        }
        Palavra palavra = palavraOpt.orElseThrow();

        Optional<String> login = loginAutenticado();
        boolean jaTentou;
        ResultadoPalavraDoDiaVM resultado = null;

        if (login.isPresent()) {
            // Logado: a fonte da verdade é o banco (cookie anônimo é ignorado -
            // entrar na conta dá direito à chance da conta)
            Optional<PalavraDoDiaTentativa> tentativa = palavraDoDiaService.tentativaDoUsuario(login.orElseThrow());
            jaTentou = tentativa.isPresent();
            if (jaTentou) {
                boolean acertou = tentativa.orElseThrow().getAcertou();
                // Acerto de logado rendeu XP hoje - reexibe o valor ao recarregar a página
                resultado = montarResultado(palavra, acertou, acertou ? XpService.XP_ACERTO_PALAVRA_DIA : 0);
            }
        } else {
            // Anônimo: vale o cookie httpOnly emitido na tentativa (anônimo não ganha XP)
            Optional<Boolean> acertoCookie = lerCookieDeHoje(cookie);
            jaTentou = acertoCookie.isPresent();
            if (jaTentou) {
                resultado = montarResultado(palavra, acertoCookie.orElseThrow(), 0);
            }
        }

        return new PalavraDoDiaVM(
            true,
            palavraDoDiaService.hoje(),
            palavra.getTexto().length(),
            palavra.getTexto(),
            palavra.getDificuldade() != null ? palavra.getDificuldade().name() : null,
            palavra.getCategoria(),
            // Dica cadastrada no banco (coluna dica da tabela palavra) - pista para o jogador
            palavra.getDica(),
            jaTentou,
            resultado
        );
    }

    /**
     * {@code POST /api/public/palavra-do-dia/tentar} : consome a chance única.
     * Valida no servidor, registra estatística/tentativa no banco e emite o
     * cookie httpOnly para bloquear repetição de anônimos.
     */
    @PostMapping("/tentar")
    public ResultadoPalavraDoDiaVM tentar(
        @RequestBody TentativaPayload payload,
        @CookieValue(name = COOKIE_TENTATIVA, required = false) String cookie,
        HttpServletResponse response
    ) {
        if (payload == null || payload.resposta() == null || payload.resposta().isBlank()) {
            throw new BadRequestAlertException("Resposta vazia", "palavraDoDia", "respostavazia");
        }

        Palavra palavra = palavraDoDiaService
            .palavraDeHoje()
            .orElseThrow(() -> new BadRequestAlertException("Não há palavra do dia disponível", "palavraDoDia", "indisponivel"));

        Optional<String> login = loginAutenticado();

        // Bloqueio de segunda tentativa - sempre validado no servidor
        if (login.isPresent()) {
            if (palavraDoDiaService.tentativaDoUsuario(login.orElseThrow()).isPresent()) {
                throw new BadRequestAlertException("Você já usou sua chance de hoje", "palavraDoDia", "jatentou");
            }
        } else if (lerCookieDeHoje(cookie).isPresent()) {
            throw new BadRequestAlertException("Você já usou sua chance de hoje", "palavraDoDia", "jatentou");
        }

        boolean acertou = palavraDoDiaService.tentar(palavra, login.orElse(null), payload.resposta());
        LOG.info("Palavra do dia: tentativa de {} - {}", login.orElse("anônimo"), acertou ? "acerto" : "erro");

        // Marca a tentativa no navegador via cookie httpOnly (anônimos); para logados
        // também não faz mal - mas quem manda é o registro no banco
        response.addHeader(HttpHeaders.SET_COOKIE, cookieDeTentativa(acertou).toString());

        // O XP foi creditado dentro de tentar(); aqui só informamos o valor para a UI
        long xpGanho = acertou && login.isPresent() ? XpService.XP_ACERTO_PALAVRA_DIA : 0;
        return montarResultado(palavra, acertou, xpGanho);
    }

    // Login autenticado de verdade (descarta o principal "anonymousUser" do Spring)
    private Optional<String> loginAutenticado() {
        return SecurityUtils.getCurrentUserLogin().filter(l -> !"anonymousUser".equals(l));
    }

    // Cookie no formato "yyyy-MM-dd_1|0"; só vale se for do dia de hoje
    private Optional<Boolean> lerCookieDeHoje(String cookie) {
        if (cookie == null || !cookie.contains("_")) {
            return Optional.empty();
        }
        String[] partes = cookie.split("_", 2);
        try {
            if (LocalDate.parse(partes[0]).equals(palavraDoDiaService.hoje())) {
                return Optional.of("1".equals(partes[1]));
            }
        } catch (Exception e) {
            // cookie corrompido - trata como inexistente
        }
        return Optional.empty();
    }

    // Cookie httpOnly válido até a meia-noite de Brasília (quando nasce a nova palavra)
    private ResponseCookie cookieDeTentativa(boolean acertou) {
        ZonedDateTime agora = ZonedDateTime.now(PalavraDoDiaService.FUSO);
        long segundosAteMeiaNoite = Duration.between(
            agora,
            agora.toLocalDate().plusDays(1).atStartOfDay(PalavraDoDiaService.FUSO)
        ).getSeconds();
        return ResponseCookie.from(COOKIE_TENTATIVA, palavraDoDiaService.hoje() + "_" + (acertou ? "1" : "0"))
            .httpOnly(true)
            .path("/")
            .sameSite("Lax")
            .maxAge(Math.max(segundosAteMeiaNoite, 60))
            .build();
    }

    private ResultadoPalavraDoDiaVM montarResultado(Palavra palavra, boolean acertou, long xpGanho) {
        long[] stats = palavraDoDiaService.estatisticasDaPalavra(palavra.getId());
        return new ResultadoPalavraDoDiaVM(acertou, palavra.getTexto(), stats[0], stats[1], xpGanho);
    }
}
