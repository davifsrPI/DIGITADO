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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Serviço que gerencia o estado em memória de todos os jogos em andamento.
// Cada sala tem seu próprio EstadoJogo, identificado pelo código da sala.
// Como o estado fica em memória (não no banco), reiniciar o servidor reseta todos os jogos.
@Service
public class JogoSalaService {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaService.class);

    // Pontuação base por ordem de acerto (1º, 2º, 3º, 4º+) e bônus de velocidade
    private static final int[] PONTOS_BASE = { 20, 15, 12, 8 };
    private static final int[] BONUS_MAX = { 10, 7, 5, 3 };

    // Tolerância além do tempo da rodada para aceitar resposta (latência de rede) —
    // depois disso o servidor rejeita, mesmo que um cliente adulterado envie
    private static final long FOLGA_RESPOSTA_MS = 2000;

    // Piso de plausibilidade: nenhum humano digita mais rápido que ~80ms por letra.
    // Resposta que chega antes disso (bot, injeção via DevTools) vira alerta de burla
    private static final long MIN_MS_POR_LETRA = 80;

    private final PalavraRepository palavraRepository;
    private final PalavraEstatisticaService palavraEstatisticaService;
    private final ConquistaEngineService conquistaEngine;

    // Mapa em memória: código da sala → estado do jogo
    private final Map<String, EstadoJogo> jogos = new ConcurrentHashMap<>();

    public JogoSalaService(
        PalavraRepository palavraRepository,
        PalavraEstatisticaService palavraEstatisticaService,
        ConquistaEngineService conquistaEngine
    ) {
        this.palavraRepository = palavraRepository;
        this.palavraEstatisticaService = palavraEstatisticaService;
        this.conquistaEngine = conquistaEngine;
    }

    // Registra um participante na sala (cria o estado da sala se ainda não existir)
    public void registrarAluno(String codigoSala, String login, String nome) {
        jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo()).registrarAluno(login, nome);
    }

    // Registra um participante num duelo 1v1, respeitando o limite de 2 jogadores.
    // Retorna false se a sala já está cheia (e o login não é um dos dois que já estão nela —
    // reconexão de quem já participa é sempre aceita).
    public boolean registrarNoDuelo(String codigoSala, String login, String nome) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        synchronized (jogo) {
            jogo.marcarModo1v1();
            if (jogo.totalConectados() >= 2 && !jogo.getAlunosConectados().containsKey(login)) {
                return false;
            }
            jogo.registrarAluno(login, nome);
            return true;
        }
    }

    // Quantos jogadores estão conectados na sala agora (0 se a sala nem tem estado em memória)
    public int conectadosNaSala(String codigoSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        return jogo != null ? jogo.totalConectados() : 0;
    }

    // Remove um participante da lista de conectados (usado quando desconecta)
    public void removerAluno(String codigoSala, String login) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo != null) jogo.removerAluno(login);
    }

    // Resultado do processamento de uma desconexão:
    // salasComSaida: salas de onde o jogador foi removido (quem ficou precisa ver o estado novo);
    // salasEncerradasVazias: salas cuja partida JÁ TERMINOU e ficaram sem ninguém —
    // o chamador deve fechá-las no banco (ativo = false).
    public record ResultadoDesconexao(List<String> salasComSaida, List<String> salasEncerradasVazias) {}

    // Processa a desconexão de um usuário: remove-o de todas as salas em que estava
    // conectado e identifica as salas encerradas que ficaram vazias. O estado em memória
    // dessas salas é descartado — a partida acabou e não há mais ninguém nela.
    public ResultadoDesconexao aoDesconectar(String login) {
        List<String> salasComSaida = new ArrayList<>();
        List<String> salasEncerradasVazias = new ArrayList<>();
        jogos.forEach((codigo, jogo) -> {
            if (jogo.getAlunosConectados().containsKey(login)) {
                jogo.removerAluno(login);
                salasComSaida.add(codigo);
            }
            if ("ENCERRADA".equals(jogo.getTipo()) && jogo.totalConectados() == 0) {
                salasEncerradasVazias.add(codigo);
            }
        });
        salasEncerradasVazias.forEach(jogos::remove);
        return new ResultadoDesconexao(salasComSaida, salasEncerradasVazias);
    }

    // Inicia o jogo: sorteia as palavras conforme a configuração escolhida pelo professor,
    // adiciona quaisquer palavras extras selecionadas manualmente e embaralha tudo
    public EstadoJogoDTO iniciar(String codigoSala, String nomeSala, IniciarPayload payload) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        // Palavras da PARTIDA ANTERIOR desta sala ficam fora do sorteio — evita que
        // duas partidas seguidas repitam as mesmas palavras
        List<Long> recentes = jogo.getIdsPalavras();
        List<Long> excluir = recentes.isEmpty() ? List.of(-1L) : recentes;
        List<Palavra> palavras = new ArrayList<>();
        // Palavras já sorteadas na tela de criação da sala: a PRIMEIRA partida usa
        // exatamente essas (o professor viu a lista e pôde trocar cada uma). Numa
        // partida seguinte da mesma sala elas viram "recentes" e o sorteio por
        // quantidade assume, mantendo a regra de não repetir a partida anterior.
        List<Long> fixas = payload.palavrasIds() != null ? payload.palavrasIds() : List.of();
        boolean usarFixas = !fixas.isEmpty() && recentes.isEmpty();
        int totalPedido;
        if (usarFixas) {
            for (Long id : fixas) {
                palavraRepository.findById(id).filter(p -> Boolean.TRUE.equals(p.getAtiva())).ifPresent(palavras::add);
            }
            totalPedido = fixas.size();
        } else {
            palavras.addAll(palavraRepository.findRandomByDificuldadeExcluindo(Dificuldade.FACIL.name(), payload.qtdFacil(), excluir));
            palavras.addAll(palavraRepository.findRandomByDificuldadeExcluindo(Dificuldade.MEDIO.name(), payload.qtdMedio(), excluir));
            palavras.addAll(palavraRepository.findRandomByDificuldadeExcluindo(Dificuldade.DIFICIL.name(), payload.qtdDificil(), excluir));
            totalPedido = payload.qtdFacil() + payload.qtdMedio() + payload.qtdDificil();
        }
        // A dificuldade é uma MÉTRICA (taxa de acerto), então alguma faixa pode não ter
        // palavras suficientes (ex: banco novo, onde quase tudo ainda é MEDIO) — e uma
        // palavra fixa pode ter sido desativada entre a criação da sala e o início.
        // Completa a diferença sorteando entre as demais palavras ativas, para a
        // partida sempre ter o total de palavras que o professor pediu.
        int faltam = totalPedido - palavras.size();
        if (faltam > 0) {
            List<Long> indisponiveis = new ArrayList<>(recentes);
            palavras.forEach(p -> indisponiveis.add(p.getId()));
            palavras.addAll(palavraRepository.findRandomAtivasExcluindo(indisponiveis.isEmpty() ? List.of(-1L) : indisponiveis, faltam));
        }
        // Acervo pequeno: se ainda faltar, aceita repetir palavras da partida anterior
        faltam = totalPedido - palavras.size();
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

    // Avança para a próxima palavra; se não houver mais, encerra o jogo.
    // loginProfessor: quem comanda a sala — excluído da contagem de silenciosos.
    public EstadoJogoDTO proximaPalavra(String codigoSala, String nomeSala, String loginProfessor) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        contabilizarSilenciosos(jogo, loginProfessor);
        boolean temProxima = jogo.avancar();
        if (!temProxima) {
            premiarFimDePartida(jogo);
        }
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
    public EstadoJogoDTO encerrar(String codigoSala, String nomeSala, String loginProfessor) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        contabilizarSilenciosos(jogo, loginProfessor);
        jogo.encerrar();
        premiarFimDePartida(jogo);
        return buildEstado(codigoSala, nomeSala, jogo, "ENCERRADA");
    }

    // Quem estava conectado e NÃO respondeu a palavra da rodada conta como
    // tentativa errada nas estatísticas — mas só quando o tempo realmente esgotou
    // (protege contra avanço duplo/precoce contaminar os números). O professor
    // que comanda a sala fica de fora: ele não é obrigado a jogar.
    private void contabilizarSilenciosos(EstadoJogo jogo, String loginProfessor) {
        Palavra atual = jogo.getPalavraAtual();
        if (atual == null || !"NOVA_PALAVRA".equals(jogo.getTipo())) {
            return;
        }
        long elapsed = Instant.now().toEpochMilli() - jogo.getTimestampInicio();
        if (elapsed < jogo.getTempoLimite() * 1000L) {
            return;
        }
        for (String login : jogo.getAlunosConectados().keySet()) {
            if (!login.equals(loginProfessor) && !jogo.jaRespondeu(login)) {
                palavraEstatisticaService.registrarTentativa(atual.getId(), false);
            }
        }
    }

    // Ao terminar a partida (fim natural ou encerramento antecipado), dispara os
    // eventos de conquista para cada participante (quem respondeu ao menos uma vez):
    // partida jogada, vitória, pódio e partida perfeita. Nunca derruba o jogo.
    private void premiarFimDePartida(EstadoJogo jogo) {
        if (jogo.isFimPremiado()) {
            return; // encerramento duplo não premia duas vezes
        }
        jogo.marcarFimPremiado();
        int totalPalavras = jogo.getTotalPalavras();
        // Participantes ordenados por pontos (define vitória e pódio)
        List<String> ordenados = jogo
            .getParticipantes()
            .stream()
            .sorted(
                Comparator.comparingInt((String login) -> {
                    EstadoJogo.AlunoInfo info = jogo.getPlacar().get(login);
                    return info != null ? info.pontos() : 0;
                }).reversed()
            )
            .toList();
        for (int i = 0; i < ordenados.size(); i++) {
            String login = ordenados.get(i);
            int[] estat = jogo.getEstatisticaPartida(login);
            boolean perfeita = totalPalavras > 0 && estat[0] == totalPalavras && estat[1] == totalPalavras;
            try {
                conquistaEngine.aoConcluirPartida(login, i == 0, i < 3, perfeita);
            } catch (Exception e) {
                LOG.error("Falha ao premiar fim de partida para {}: {}", login, e.getMessage(), e);
            }
        }
        // Conquistas exclusivas do modo Duelo 1v1 — só valem com os dois oponentes
        // tendo participado ("Primeiro Duelo", "Duelista", "Vença de um Desenvolvedor"...)
        if (jogo.isModo1v1() && ordenados.size() == 2) {
            for (int i = 0; i < 2; i++) {
                String login = ordenados.get(i);
                String oponente = ordenados.get(1 - i);
                try {
                    conquistaEngine.aoConcluirDuelo(login, i == 0, oponente);
                } catch (Exception e) {
                    LOG.error("Falha ao premiar duelo 1v1 para {}: {}", login, e.getMessage(), e);
                }
            }
        }
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
    public ResultadoResposta responder(
        String codigoSala,
        String nomeSala,
        String login,
        String nomeAluno,
        String respostaDigitada,
        int tentativasBurla
    ) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null || jogo.getPalavraAtual() == null) return null;
        // Cada aluno só pode responder uma vez por palavra
        if (jogo.jaRespondeu(login)) return null;
        // O relógio é validado NO SERVIDOR: rodada precisa estar ativa e dentro do
        // tempo (com folga para latência) — um cliente adulterado não responde
        // depois que o tempo esgota nem durante pausa/encerramento
        if (!"NOVA_PALAVRA".equals(jogo.getTipo())) return null;
        long decorrido = Instant.now().toEpochMilli() - jogo.getTimestampInicio();
        if (decorrido > jogo.getTempoLimite() * 1000L + FOLGA_RESPOSTA_MS) return null;

        String textoCorreto = jogo.getPalavraAtual().getTexto();
        String dLower = respostaDigitada.trim().toLowerCase();
        String cLower = textoCorreto.trim().toLowerCase();
        boolean correta = dLower.equals(cLower);

        // Alerta de burla: o cliente reportou inserções bloqueadas (colar/corretor) ou a
        // resposta chegou rápido demais para ter sido digitada. A resposta continua valendo —
        // o alerta aparece no placar do professor, que decide o que fazer
        boolean suspeita = tentativasBurla > 0 || decorrido < dLower.length() * MIN_MS_POR_LETRA;
        if (suspeita) {
            jogo.registrarAlerta(login);
            LOG.warn(
                "Resposta suspeita de {} na sala {}: {}ms para {} letras, tentativasBurla={}",
                login,
                codigoSala,
                decorrido,
                dLower.length(),
                tentativasBurla
            );
        }

        // Classifica o tipo de erro para dar feedback mais detalhado ao aluno
        String tipoErro = null;
        if (!correta) {
            tipoErro = normalizar(dLower).equals(normalizar(cLower))
                ? "ACENTUACAO"
                : classificarErro(normalizar(dLower), normalizar(cLower));
        }

        // Contabiliza a tentativa nas colunas de estatística da tabela palavra
        // (total_tentativas/total_acertos) SOMENTE em duelos 1v1 — salas criadas por
        // professor ficam de fora para a turma não distorcer a métrica de dificuldade.
        // A Palavra do Dia contabiliza no próprio fluxo (PalavraDoDiaService.tentar).
        if (jogo.isModo1v1()) {
            palavraEstatisticaService.registrarTentativa(jogo.getPalavraAtual().getId(), correta);
        }

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

        // Motor de conquistas: acerto, rapidez, acentos/cedilha e sequência.
        // Transação própria e try/catch — conquista nunca derruba a partida.
        try {
            conquistaEngine.aoResponderNaPartida(login, jogo.getPalavraAtual(), correta, decorrido, jogo.getSequenciaAcertos(login));
        } catch (Exception e) {
            LOG.error("Falha ao processar conquistas da resposta de {}: {}", login, e.getMessage(), e);
        }

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
            .map(e ->
                new PlacarEntry(
                    e.getKey(),
                    e.getValue().nome(),
                    e.getValue().pontos(),
                    e.getValue().statusAtual(),
                    jogo.getAlertas(e.getKey())
                )
            )
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
        // Respostas suspeitas (colar/corretor bloqueado ou rápida demais) por jogador na partida
        private final Map<String, Integer> alertasBurla = new ConcurrentHashMap<>();
        private int ordemRespostas = 0;
        // Rastreamento da PARTIDA para conquistas: sequência de acertos por jogador,
        // estatística acumulada (respostas/acertos) e flag de fim já premiado
        private final Map<String, Integer> sequenciaAcertos = new ConcurrentHashMap<>();
        private final Map<String, int[]> estatisticasPartida = new ConcurrentHashMap<>();
        private volatile boolean fimPremiado = false;
        // Sala de duelo 1v1: no máximo 2 jogadores e conquistas próprias no fim
        private volatile boolean modo1v1 = false;

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
            sequenciaAcertos.clear();
            estatisticasPartida.clear();
            alertasBurla.clear();
            fimPremiado = false;
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
            // Rastreamento para conquistas: sequência de acertos e totais da partida
            sequenciaAcertos.merge(login, correta ? 1 : 0, (seq, x) -> correta ? seq + 1 : 0);
            int[] estat = estatisticasPartida.computeIfAbsent(login, k -> new int[2]);
            synchronized (estat) {
                estat[0]++;
                if (correta) estat[1]++;
            }
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

        // Ids das palavras carregadas nesta sala (da partida em curso ou da última
        // encerrada) — usados para não repetir palavras na partida seguinte
        List<Long> getIdsPalavras() {
            return palavras.stream().map(Palavra::getId).filter(Objects::nonNull).toList();
        }

        // Sequência atual de acertos consecutivos do jogador nesta partida
        int getSequenciaAcertos(String login) {
            return sequenciaAcertos.getOrDefault(login, 0);
        }

        // Contabiliza uma resposta suspeita do jogador (exibida no placar do professor)
        void registrarAlerta(String login) {
            alertasBurla.merge(login, 1, Integer::sum);
        }

        int getAlertas(String login) {
            return alertasBurla.getOrDefault(login, 0);
        }

        // {respostas, acertos} do jogador nesta partida
        int[] getEstatisticaPartida(String login) {
            int[] estat = estatisticasPartida.get(login);
            if (estat == null) {
                return new int[] { 0, 0 };
            }
            synchronized (estat) {
                return new int[] { estat[0], estat[1] };
            }
        }

        // Quem respondeu ao menos uma vez na partida (define quem "participou")
        Set<String> getParticipantes() {
            return Set.copyOf(estatisticasPartida.keySet());
        }

        boolean isModo1v1() {
            return modo1v1;
        }

        void marcarModo1v1() {
            modo1v1 = true;
        }

        boolean isFimPremiado() {
            return fimPremiado;
        }

        void marcarFimPremiado() {
            fimPremiado = true;
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
