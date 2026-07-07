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

// Serviço que gerencia o estado em memória de todos os jogos em andamento.
// Cada sala tem seu próprio EstadoJogo, identificado pelo código da sala.
// Como o estado fica em memória (não no banco), reiniciar o servidor reseta todos os jogos.
@Service
public class JogoSalaService {

    // Pontuação base por ordem de acerto (1º, 2º, 3º, 4º+) e bônus de velocidade
    private static final int[] PONTOS_BASE = { 20, 15, 12, 8 };
    private static final int[] BONUS_MAX = { 10, 7, 5, 3 };

    private final PalavraRepository palavraRepository;
    private final PalavraEstatisticaService palavraEstatisticaService;

    // Mapa em memória: código da sala → estado do jogo
    private final Map<String, EstadoJogo> jogos = new ConcurrentHashMap<>();

    public JogoSalaService(PalavraRepository palavraRepository, PalavraEstatisticaService palavraEstatisticaService) {
        this.palavraRepository = palavraRepository;
        this.palavraEstatisticaService = palavraEstatisticaService;
    }

    // Registra um participante na sala (cria o estado da sala se ainda não existir)
    public void registrarAluno(String codigoSala, String login, String nome) {
        jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo()).registrarAluno(login, nome);
    }

    // Remove um participante da lista de conectados (usado quando desconecta)
    public void removerAluno(String codigoSala, String login) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo != null) jogo.removerAluno(login);
    }

    // Inicia o jogo: sorteia as palavras conforme a configuração escolhida pelo professor,
    // adiciona quaisquer palavras extras selecionadas manualmente e embaralha tudo
    public EstadoJogoDTO iniciar(String codigoSala, String nomeSala, IniciarPayload payload) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        List<Palavra> palavras = new ArrayList<>();
        palavras.addAll(palavraRepository.findRandomByDificuldade(Dificuldade.FACIL.name(), payload.qtdFacil()));
        palavras.addAll(palavraRepository.findRandomByDificuldade(Dificuldade.MEDIO.name(), payload.qtdMedio()));
        palavras.addAll(palavraRepository.findRandomByDificuldade(Dificuldade.DIFICIL.name(), payload.qtdDificil()));
        // A dificuldade é uma MÉTRICA (taxa de acerto), então alguma faixa pode não ter
        // palavras suficientes (ex: banco novo, onde quase tudo ainda é MEDIO).
        // Completa a diferença sorteando entre as demais palavras ativas, para a
        // partida sempre ter o total de palavras que o professor pediu.
        int totalPedido = payload.qtdFacil() + payload.qtdMedio() + payload.qtdDificil();
        int faltam = totalPedido - palavras.size();
        if (faltam > 0) {
            List<Long> jaEscolhidas = palavras.isEmpty() ? List.of(-1L) : palavras.stream().map(Palavra::getId).toList();
            palavras.addAll(palavraRepository.findRandomAtivasExcluindo(jaEscolhidas, faltam));
        }
        // Adiciona as palavras extras escolhidas pelo professor na tela de criação da sala,
        // sem duplicar alguma que já tenha sido sorteada
        if (payload.palavrasExtrasIds() != null) {
            for (Long id : payload.palavrasExtrasIds()) {
                if (palavras.stream().noneMatch(p -> p.getId().equals(id))) {
                    palavraRepository.findById(id).ifPresent(palavras::add);
                }
            }
        }
        Collections.shuffle(palavras);
        jogo.iniciar(palavras, payload.tempoFacil(), payload.tempoMedio(), payload.tempoDificil());
        return buildEstado(codigoSala, nomeSala, jogo, "INICIADA");
    }

    // Avança para a próxima palavra; se não houver mais, encerra o jogo
    public EstadoJogoDTO proximaPalavra(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        boolean temProxima = jogo.avancar();
        String tipo = temProxima ? "NOVA_PALAVRA" : "ENCERRADA";
        return buildEstado(codigoSala, nomeSala, jogo, tipo);
    }

    // Pausa o jogo (o timer para de correr no frontend)
    public EstadoJogoDTO pausar(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        jogo.pausar();
        return buildEstado(codigoSala, nomeSala, jogo, "PAUSADA");
    }

    // Encerra o jogo antecipadamente
    public EstadoJogoDTO encerrar(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        jogo.encerrar();
        return buildEstado(codigoSala, nomeSala, jogo, "ENCERRADA");
    }

    // Retorna o estado atual da sala (usado logo após o entrar para sincronizar o cliente)
    public EstadoJogoDTO getEstado(String codigoSala, String nomeSala) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        return buildEstado(codigoSala, nomeSala, jogo, jogo.getTipo());
    }

    // Resultado de uma resposta: contém o feedback individual + o estado atualizado da sala
    public record ResultadoResposta(FeedbackAluno feedback, EstadoJogoDTO estado) {}

    // Processa a resposta de um aluno:
    // compara com a palavra correta, calcula pontos (com bônus de velocidade) e registra no placar
    public ResultadoResposta responder(String codigoSala, String nomeSala, String login, String nomeAluno, String respostaDigitada) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null || jogo.getPalavraAtual() == null) return null;
        // Cada aluno só pode responder uma vez por palavra
        if (jogo.jaRespondeu(login)) return null;

        String textoCorreto = jogo.getPalavraAtual().getTexto();
        String dLower = respostaDigitada.trim().toLowerCase();
        String cLower = textoCorreto.trim().toLowerCase();
        boolean correta = dLower.equals(cLower);

        // Classifica o tipo de erro para dar feedback mais detalhado ao aluno
        String tipoErro = null;
        if (!correta) {
            tipoErro = normalizar(dLower).equals(normalizar(cLower))
                ? "ACENTUACAO"
                : classificarErro(normalizar(dLower), normalizar(cLower));
        }

        // Contabiliza a tentativa nas colunas de estatística da tabela palavra:
        // toda pessoa que respondeu conta em total_tentativas; acertos somam em total_acertos.
        // Feito aqui no backend, onde a resposta é validada — o front não envia contadores.
        palavraEstatisticaService.registrarTentativa(jogo.getPalavraAtual().getId(), correta);

        // Registra a resposta e guarda a ordem de acerto (1º, 2º, 3º...)
        int ordem = jogo.registrarResposta(login, correta);
        int pontos = 0;
        if (correta) {
            // Pontuação = base (por ordem de acerto) + bônus de velocidade proporcional ao tempo restante
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

    // Remove acentos para comparação sem diferenciar versões acentuadas
    private String normalizar(String s) {
        return Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // Classifica o tipo de erro comparando as versões normalizadas com distância de Levenshtein
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

    // Algoritmo de Levenshtein: calcula o número mínimo de edições (inserção, remoção, substituição)
    // para transformar uma string na outra — usado para classificar erros de digitação
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

    // Monta o DTO que será enviado via WebSocket para todos os clientes,
    // incluindo palavra atual, placar, alunos conectados e progresso da rodada
    private EstadoJogoDTO buildEstado(String codigoSala, String nomeSala, EstadoJogo jogo, String tipo) {
        Palavra p = jogo.getPalavraAtual();
        PalavraDTO palavraDTO = p == null ? null : new PalavraDTO(p.getId(), p.getTexto(), p.getDificuldade().name(), p.getCategoria());
        // Placar ordenado do maior para o menor pontuação
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

    // ===== Contadores para monitoramento (usados pelo JogoSalaHealthIndicator) =====

    // Total de salas com estado carregado em memória
    public int totalSalasEmMemoria() {
        return jogos.size();
    }

    // Total de jogos efetivamente em andamento (rodada ativa ou pausada)
    public long totalJogosEmAndamento() {
        return jogos.values().stream().filter(j -> "NOVA_PALAVRA".equals(j.getTipo()) || "PAUSADA".equals(j.getTipo())).count();
    }

    // Total de alunos conectados somando todas as salas
    public int totalAlunosConectados() {
        return jogos.values().stream().mapToInt(EstadoJogo::totalConectados).sum();
    }

    // Estado interno de uma sala de jogo — mantido em memória enquanto o servidor está rodando.
    // Usa ConcurrentHashMap para suportar múltiplos jogadores respondendo ao mesmo tempo.
    public static class EstadoJogo {

        private List<Palavra> palavras = new ArrayList<>();
        private int indiceAtual = -1;
        // Tempo de rodada por dificuldade — o tempo efetivo depende da palavra atual
        private int tempoFacil = 30;
        private int tempoMedio = 30;
        private int tempoDificil = 30;
        private long timestampInicio = 0;
        private String tipo = "AGUARDANDO";
        private final Map<String, AlunoInfo> placar = new ConcurrentHashMap<>();
        private final Map<String, String> alunosConectados = new ConcurrentHashMap<>();
        // Conjunto dos logins que já responderam na rodada atual (evita resposta dupla)
        private final Set<String> respondeuNaRodada = ConcurrentHashMap.newKeySet();
        private int ordemRespostas = 0;

        public record AlunoInfo(String nome, int pontos, String statusAtual) {}

        // Começa o jogo: define as palavras, redefine o índice para 0 e registra o timestamp de início
        void iniciar(List<Palavra> palavras, int tempoFacil, int tempoMedio, int tempoDificil) {
            this.palavras = palavras;
            this.tempoFacil = tempoFacil;
            this.tempoMedio = tempoMedio;
            this.tempoDificil = tempoDificil;
            this.indiceAtual = 0;
            this.tipo = "NOVA_PALAVRA";
            this.timestampInicio = Instant.now().toEpochMilli();
            respondeuNaRodada.clear();
            ordemRespostas = 0;
            // Reseta o status de todos para "AGUARDANDO" ao começar
            placar.replaceAll((k, v) -> new AlunoInfo(v.nome(), v.pontos(), "AGUARDANDO"));
        }

        // Avança para a próxima palavra; retorna false se chegou ao fim da lista
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

        // Marca que o aluno respondeu e retorna sua posição de chegada (1º, 2º, 3º...)
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

        // Garante que o aluno apareça na lista de conectados e no placar (com 0 pontos)
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

        // Tempo da rodada ATUAL: depende da dificuldade (calculada) da palavra em jogo.
        // Assim cada palavra pode ter um tempo diferente sem mudar nada no frontend.
        int getTempoLimite() {
            Palavra atual = getPalavraAtual();
            if (atual == null || atual.getDificuldade() == null) {
                return tempoMedio;
            }
            return switch (atual.getDificuldade()) {
                case FACIL -> tempoFacil;
                case DIFICIL -> tempoDificil;
                default -> tempoMedio;
            };
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

        // Quantidade de alunos conectados nesta sala (para monitoramento)
        int totalConectados() {
            return alunosConectados.size();
        }
    }
}
