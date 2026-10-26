package br.com.digitado.management;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Os últimos erros HTTP (status 4xx/5xx ou exceção), com o máximo de detalhe que dá
 * para guardar sem expor o que não deve: quando, método, rota, caminho, status,
 * tempo, a exceção com a causa raiz e o começo da pilha de chamadas.
 *
 * O Micrometer só CONTA os erros por rota e status (é o que a tabela de
 * requisições do painel usa); a pergunta "qual foi o erro e onde estourou" precisa
 * do erro em si. Ele vem da mesma observação que gera o http.server.requests: o
 * Spring Boot registra todo ObservationHandler que for bean, e este só olha as
 * requisições de servlet.
 *
 * Fora de propósito: query string, corpo e cabeçalhos - é onde moram token, senha e
 * dado pessoal. Só a memória guarda os erros (os últimos CAPACIDADE), e só o ADMIN
 * os lê, por /management/requisicoes.
 */
@Component
public class RegistroErrosHttp implements ObservationHandler<ServerRequestObservationContext>, HandlerExceptionResolver, Ordered {

    /** Quantos erros ficam guardados; o mais antigo sai quando chega um novo. */
    static final int CAPACIDADE = 100;

    /** Linhas da pilha de chamadas guardadas por erro. */
    static final int LINHAS_DA_PILHA = 12;

    private static final int TAMANHO_MAXIMO_MENSAGEM = 500;

    // As perguntas dos painéis não entram: o 401 de uma sessão expirada a cada meio
    // segundo empurraria os erros de verdade para fora da lista
    private static final Set<String> CAMINHOS_IGNORADOS = Set.of(
        MonitorDesempenhoEndpoint.URI_DO_PAINEL,
        MonitorRequisicoesEndpoint.URI_DO_PAINEL
    );

    // Chave da hora de início na observação (onStart -> onStop)
    private static final Object INICIO = new Object();

    // A exceção e a mensagem dela no instante em que estourou (ver resolveException)
    static final String ATRIBUTO_EXCECAO = RegistroErrosHttp.class.getName() + ".EXCECAO";
    static final String ATRIBUTO_MENSAGEM = RegistroErrosHttp.class.getName() + ".MENSAGEM";

    private final Deque<MonitorRequisicoes.ErroHttp> erros = new ArrayDeque<>();
    private final AtomicLong totalRegistrado = new AtomicLong();

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStart(ServerRequestObservationContext context) {
        context.put(INICIO, System.nanoTime());
    }

    /**
     * Primeiro da fila dos tratadores de exceção do Spring MVC: só ANOTA a exceção e a
     * mensagem dela no request e devolve null, para o ExceptionTranslator seguir
     * fazendo a resposta. É preciso anotar aqui porque o ExceptionTranslator reescreve
     * o corpo das ErrorResponseException - quando a observação termina, a mensagem
     * original ("Tipo de erro desconhecido: X") já virou "400 BAD_REQUEST, ProblemDetail[...]".
     */
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        request.setAttribute(ATRIBUTO_EXCECAO, ex);
        request.setAttribute(ATRIBUTO_MENSAGEM, mensagemLegivel(ex));
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        HttpServletRequest request = context.getCarrier();
        HttpServletResponse response = context.getResponse();
        int status = response == null ? 0 : response.getStatus();
        Throwable erro = context.getError();
        if (erro == null && request != null && request.getAttribute(ATRIBUTO_EXCECAO) instanceof Throwable t) {
            erro = t;
        }
        if (erro == null && request != null && request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE) instanceof Throwable t) {
            // Exceção que um @ExceptionHandler (ExceptionTranslator) já transformou em
            // resposta: o DispatcherServlet a deixa no request
            erro = t;
        }
        String mensagem = request != null && request.getAttribute(ATRIBUTO_MENSAGEM) instanceof String m ? m : mensagemLegivel(erro);
        if (status < 400 && erro == null) {
            return;
        }
        String caminho = request == null ? null : request.getRequestURI();
        if (caminho != null && CAMINHOS_IGNORADOS.contains(caminho)) {
            return;
        }
        Long inicio = context.get(INICIO);
        registrar(
            new MonitorRequisicoes.ErroHttp(
                System.currentTimeMillis(),
                request == null ? null : request.getMethod(),
                context.getPathPattern(),
                caminho,
                status,
                inicio == null ? null : (System.nanoTime() - inicio) / 1_000_000.0,
                erro == null ? null : erro.getClass().getName(),
                erro == null ? null : resumir(mensagem),
                erro == null ? null : causaRaiz(erro),
                erro == null ? List.of() : pilha(erro)
            )
        );
    }

    void registrar(MonitorRequisicoes.ErroHttp erro) {
        totalRegistrado.incrementAndGet();
        synchronized (erros) {
            erros.addFirst(erro);
            while (erros.size() > CAPACIDADE) {
                erros.removeLast();
            }
        }
    }

    /** Do mais novo para o mais antigo. */
    public List<MonitorRequisicoes.ErroHttp> recentes() {
        synchronized (erros) {
            return new ArrayList<>(erros);
        }
    }

    /** Quantos erros passaram por aqui desde que o servidor subiu (inclusive os que já saíram da lista). */
    public long totalRegistrado() {
        return totalRegistrado.get();
    }

    // A exceção que de fato começou tudo, quando ela vem embrulhada em outras
    private static String causaRaiz(Throwable erro) {
        Throwable raiz = erro;
        int voltas = 0;
        while (raiz.getCause() != null && raiz.getCause() != raiz && voltas++ < 20) {
            raiz = raiz.getCause();
        }
        if (raiz == erro) {
            return null;
        }
        String mensagem = resumir(raiz.getMessage());
        return raiz.getClass().getName() + (mensagem == null ? "" : ": " + mensagem);
    }

    /**
     * O começo da pilha da causa raiz - é lá que o problema nasceu. As linhas do
     * próprio DIGITADO vêm marcadas com "»" para achar o ponto no código de cara.
     */
    static List<String> pilha(Throwable erro) {
        Throwable raiz = erro;
        int voltas = 0;
        while (raiz.getCause() != null && raiz.getCause() != raiz && voltas++ < 20) {
            raiz = raiz.getCause();
        }
        List<String> linhas = new ArrayList<>();
        for (StackTraceElement e : raiz.getStackTrace()) {
            if (linhas.size() >= LINHAS_DA_PILHA) {
                break;
            }
            String linha = e.toString();
            linhas.add(linha.startsWith("br.com.digitado.") ? "» " + linha : linha);
        }
        return linhas;
    }

    /**
     * A mensagem que uma pessoa lê. As ErrorResponseException (BadRequestAlertException
     * e afins) trazem o texto no título do ProblemDetail, e o getMessage() delas é o
     * objeto inteiro serializado.
     */
    static String mensagemLegivel(Throwable erro) {
        if (erro == null) {
            return null;
        }
        if (erro instanceof ErrorResponse resposta && resposta.getBody() != null) {
            String titulo = resposta.getBody().getTitle();
            String detalhe = resposta.getBody().getDetail();
            if (titulo != null && detalhe != null && !detalhe.equals(titulo)) {
                return titulo + ": " + detalhe;
            }
            if (titulo != null || detalhe != null) {
                return titulo != null ? titulo : detalhe;
            }
        }
        return erro.getMessage();
    }

    private static String resumir(String mensagem) {
        if (mensagem == null) {
            return null;
        }
        return mensagem.length() <= TAMANHO_MAXIMO_MENSAGEM ? mensagem : mensagem.substring(0, TAMANHO_MAXIMO_MENSAGEM) + "…";
    }
}
