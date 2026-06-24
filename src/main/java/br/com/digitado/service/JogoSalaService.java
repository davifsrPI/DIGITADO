package br.com.digitado.service;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.web.websocket.dto.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class JogoSalaService {

    private static final int[] PONTOS_BASE = { 20, 15, 12, 8 };
    private static final int[] BONUS_MAX = { 10, 7, 5, 3 };

    private final PalavraRepository palavraRepository;

    private final Map<String, EstadoJogo> jogos = new ConcurrentHashMap<>();

    public JogoSalaService(PalavraRepository palavraRepository) {
        this.palavraRepository = palavraRepository;
    }

    public void registrarAluno(String codigoSala, String login, String nome) {
        jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo()).registrarAluno(login, nome);
    }

    public void removerAluno(String codigoSala, String login) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo != null) jogo.removerAluno(login);
    }

    public EstadoJogoDTO iniciar(String codigoSala, String nomeSala, IniciarPayload payload) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        List<Palavra> palavras = new ArrayList<>();
        palavras.addAll(palavraRepository.findRandomByDificuldade(Dificuldade.FACIL.name(), payload.qtdFacil()));
        palavras.addAll(palavraRepository.findRandomByDificuldade(Dificuldade.MEDIO.name(), payload.qtdMedio()));
        palavras.addAll(palavraRepository.findRandomByDificuldade(Dificuldade.DIFICIL.name(), payload.qtdDificil()));
        if (payload.palavrasExtrasIds() != null) {
            for (Long id : payload.palavrasExtrasIds()) {
                palavraRepository.findById(id).ifPresent(palavras::add);
            }
        }
        Collections.shuffle(palavras);
        jogo.iniciar(palavras, payload.tempoLimite());
        return buildEstado(codigoSala, nomeSala, jogo, "INICIADA");
    }

    public EstadoJogoDTO proximaPalavra(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        boolean temProxima = jogo.avancar();
        String tipo = temProxima ? "NOVA_PALAVRA" : "ENCERRADA";
        return buildEstado(codigoSala, nomeSala, jogo, tipo);
    }

    public EstadoJogoDTO pausar(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        jogo.pausar();
        return buildEstado(codigoSala, nomeSala, jogo, "PAUSADA");
    }

    public EstadoJogoDTO encerrar(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        jogo.encerrar();
        return buildEstado(codigoSala, nomeSala, jogo, "ENCERRADA");
    }

    public EstadoJogoDTO getEstado(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        return buildEstado(codigoSala, nomeSala, jogo, jogo.getTipo());
    }

    public record ResultadoResposta(FeedbackAluno feedback, EstadoJogoDTO estado) {}

    public ResultadoResposta responder(String codigoSala, String nomeSala, String login, String nomeAluno, String respostaDigitada) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null || jogo.getPalavraAtual() == null) return null;
        if (jogo.jaRespondeu(login)) return null;

        String textoCorreto = jogo.getPalavraAtual().getTexto();
        String dLower = respostaDigitada.trim().toLowerCase();
        String cLower = textoCorreto.trim().toLowerCase();
        boolean correta = dLower.equals(cLower);
        String tipoErro = null;
        if (!correta) {
            tipoErro = normalizar(dLower).equals(normalizar(cLower))
                ? "ACENTUACAO"
                : classificarErro(normalizar(dLower), normalizar(cLower));
        }

        int ordem = jogo.registrarResposta(login, correta);
        int pontos = 0;
        if (correta) {
            int idx = Math.min(ordem - 1, PONTOS_BASE.length - 1);
            int base = PONTOS_BASE[idx];
            long elapsed = Instant.now().toEpochMilli() - jogo.getTimestampInicio();
            double fracao = Math.max(0, 1.0 - (double) elapsed / (jogo.getTempoLimite() * 1000L));
            int bonus = (int) Math.round(BONUS_MAX[idx] * fracao);
            pontos = base + bonus;
        }
        jogo.adicionarPontos(login, nomeAluno, pontos);

        FeedbackAluno feedback = new FeedbackAluno(correta, pontos, ordem, tipoErro, textoCorreto);
        EstadoJogoDTO estado = buildEstado(codigoSala, nomeSala, jogo, jogo.getTipo());
        return new ResultadoResposta(feedback, estado);
    }

    private String normalizar(String s) {
        return Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private String classificarErro(String digitadoNorm, String corretoNorm) {
        if (digitadoNorm.equals(corretoNorm)) return "ACENTUACAO";
        int dist = levenshtein(digitadoNorm, corretoNorm);
        if (dist == 1) {
            if (digitadoNorm.length() < corretoNorm.length()) return "LETRA_FALTANDO";
            if (digitadoNorm.length() > corretoNorm.length()) return "LETRA_EXTRA";
            return "TROCA_LETRA";
        }
        return "OUTRO";
    }

    private int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                    ? dp[i - 1][j - 1]
                    : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[m][n];
    }

    private EstadoJogoDTO buildEstado(String codigoSala, String nomeSala, EstadoJogo jogo, String tipo) {
        Palavra p = jogo.getPalavraAtual();
        PalavraDTO palavraDTO = p == null ? null : new PalavraDTO(p.getId(), p.getTexto(), p.getDificuldade().name(), p.getCategoria());
        List<PlacarEntry> placar = jogo
            .getPlacar()
            .entrySet()
            .stream()
            .sorted(
                Map.Entry.<String, EstadoJogo.AlunoInfo>comparingByValue(Comparator.comparingInt(EstadoJogo.AlunoInfo::pontos).reversed())
            )
            .map(e -> new PlacarEntry(e.getKey(), e.getValue().nome(), e.getValue().pontos(), e.getValue().statusAtual()))
            .collect(Collectors.toList());
        List<EntradaAluno> conectados = jogo
            .getAlunosConectados()
            .entrySet()
            .stream()
            .map(e -> new EntradaAluno(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
        return new EstadoJogoDTO(
            tipo,
            palavraDTO,
            jogo.getIndiceAtual(),
            jogo.getTotalPalavras(),
            jogo.getTempoLimite(),
            jogo.getTimestampInicio(),
            placar,
            nomeSala,
            codigoSala,
            conectados
        );
    }

    public static class EstadoJogo {

        private List<Palavra> palavras = new ArrayList<>();
        private int indiceAtual = -1;
        private int tempoLimite = 30;
        private long timestampInicio = 0;
        private String tipo = "AGUARDANDO";
        private final Map<String, AlunoInfo> placar = new ConcurrentHashMap<>();
        private final Map<String, String> alunosConectados = new ConcurrentHashMap<>();
        private final Set<String> respondeuNaRodada = ConcurrentHashMap.newKeySet();
        private int ordemRespostas = 0;

        public record AlunoInfo(String nome, int pontos, String statusAtual) {}

        void iniciar(List<Palavra> palavras, int tempoLimite) {
            this.palavras = palavras;
            this.tempoLimite = tempoLimite;
            this.indiceAtual = 0;
            this.tipo = "NOVA_PALAVRA";
            this.timestampInicio = Instant.now().toEpochMilli();
            respondeuNaRodada.clear();
            ordemRespostas = 0;
            placar.replaceAll((k, v) -> new AlunoInfo(v.nome(), v.pontos(), "AGUARDANDO"));
        }

        boolean avancar() {
            indiceAtual++;
            if (indiceAtual >= palavras.size()) {
                tipo = "ENCERRADA";
                return false;
            }
            tipo = "NOVA_PALAVRA";
            timestampInicio = Instant.now().toEpochMilli();
            respondeuNaRodada.clear();
            ordemRespostas = 0;
            placar.replaceAll((k, v) -> new AlunoInfo(v.nome(), v.pontos(), "AGUARDANDO"));
            return true;
        }

        void pausar() {
            tipo = "PAUSADA";
        }

        void encerrar() {
            tipo = "ENCERRADA";
            indiceAtual = palavras.size();
        }

        boolean jaRespondeu(String login) {
            return respondeuNaRodada.contains(login);
        }

        int registrarResposta(String login, boolean correta) {
            respondeuNaRodada.add(login);
            int ordem = ++ordemRespostas;
            String status = correta ? "ACERTOU" : "ERROU";
            AlunoInfo atual = placar.getOrDefault(login, new AlunoInfo(login, 0, "AGUARDANDO"));
            placar.put(login, new AlunoInfo(atual.nome(), atual.pontos(), status));
            return ordem;
        }

        void adicionarPontos(String login, String nome, int pontos) {
            AlunoInfo atual = placar.getOrDefault(login, new AlunoInfo(nome, 0, "AGUARDANDO"));
            placar.put(login, new AlunoInfo(atual.nome(), atual.pontos() + pontos, atual.statusAtual()));
        }

        void registrarAluno(String login, String nome) {
            alunosConectados.put(login, nome);
            placar.computeIfAbsent(login, k -> new AlunoInfo(nome, 0, "AGUARDANDO"));
        }

        void removerAluno(String login) {
            alunosConectados.remove(login);
        }

        Palavra getPalavraAtual() {
            return (indiceAtual >= 0 && indiceAtual < palavras.size()) ? palavras.get(indiceAtual) : null;
        }

        int getIndiceAtual() {
            return indiceAtual;
        }

        int getTotalPalavras() {
            return palavras.size();
        }

        int getTempoLimite() {
            return tempoLimite;
        }

        long getTimestampInicio() {
            return timestampInicio;
        }

        String getTipo() {
            return tipo;
        }

        Map<String, AlunoInfo> getPlacar() {
            return placar;
        }

        Map<String, String> getAlunosConectados() {
            return alunosConectados;
        }
    }
}
