package br.com.digitado.security;

import br.com.digitado.config.ApplicationProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Quantos códigos de sala INEXISTENTES cada conta tentou no minuto corrente.
 *
 * O código de 6 caracteres é a credencial de entrada na sala. Sem um teto próprio,
 * quem quisesse achar a sala de outra turma podia chutar códigos em série: pela API
 * (GET /api/salas/{codigo} responde 404 para o que não existe) e pelo WebSocket
 * (entrar numa sala que não existe). O limite geral de 100 requisições por minuto
 * ainda deixava 6 mil chutes por hora por conta.
 *
 * Só a FALHA conta, como na senha errada do login: o aluno que digita o código
 * certo de primeira nunca é afetado, e quem errou uma letra corrige em duas ou três
 * tentativas. Estourou o teto, a conta fica sem conferir código nenhum (nem o
 * certo) até o minuto virar - senão o bloqueio diria qual chute acertou.
 *
 * Um contador só para a API e o WebSocket: a chave é o login.
 */
@Component
public class TentativasCodigoSala {

    private record Janela(long minuto, AtomicInteger falhas) {}

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    private final ApplicationProperties applicationProperties;

    public TentativasCodigoSala(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    /** A conta já esgotou os códigos errados deste minuto? */
    public boolean bloqueado(String login) {
        if (login == null || !applicationProperties.getRateLimit().isEnabled()) {
            return false;
        }
        Janela janela = janelas.get(login);
        return janela != null && janela.minuto() == minutoAtual() && janela.falhas().get() >= limite();
    }

    /** Mais um código inexistente tentado por esta conta. */
    public void registrarFalha(String login) {
        if (login == null) {
            return;
        }
        long minuto = minutoAtual();
        // Limpeza oportunista, como no RateLimitFilter: janelas de minutos passados saem
        if (janelas.size() > 10_000) {
            janelas.entrySet().removeIf(e -> e.getValue().minuto() != minuto);
        }
        janelas
            .compute(login, (k, atual) -> atual == null || atual.minuto() != minuto ? new Janela(minuto, new AtomicInteger()) : atual)
            .falhas()
            .incrementAndGet();
    }

    public int limite() {
        return applicationProperties.getRateLimit().getCodigosErradosPorMinuto();
    }

    private static long minutoAtual() {
        return System.currentTimeMillis() / 60_000;
    }
}
