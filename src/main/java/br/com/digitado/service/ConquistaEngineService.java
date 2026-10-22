package br.com.digitado.service;

import br.com.digitado.domain.Conquista;
import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.Usuario;
import br.com.digitado.domain.UsuarioConquista;
import br.com.digitado.repository.ConquistaRepository;
import br.com.digitado.repository.ParticipanteSalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioConquistaRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.AuthoritiesConstants;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de conquistas: transforma eventos do jogo em progresso/desbloqueio de
 * conquistas e credita o XP da recompensa (que alimenta o Ranking Mundial).
 *
 * Regras dirigidas por tabela, com a META de cada conquista derivada da
 * DESCRIÇÃO do catálogo oficial (conquista_seed.csv) - a chave é o NOME.
 *
 * O progresso fica em usuario_conquista.progresso; para "Frequência Perfeita"
 * (dias diferentes), o campo dataConquista guarda o último dia contado
 * enquanto a conquista não conclui - vira a data de desbloqueio ao concluir.
 *
 * Os métodos públicos PARTICIPAM da transação do chamador (como o
 * PalavraEstatisticaService) - transação própria (REQUIRES_NEW) causaria
 * deadlock: o chamador costuma segurar o lock da linha de usuario (crédito de
 * XP) que o desbloqueio de conquista também precisa atualizar. Os CHAMADORES
 * envolvem as chamadas em try/catch para falha de conquista não derrubar o jogo.
 *
 * "Aprendendo com os Erros"/"Superação" leem o histórico de respostas (a última
 * resposta da pessoa àquela palavra tinha sido um erro), "Explorador de Salas"
 * conta as salas de turma em que ela se identificou e "Aluno Dedicado" conta as
 * partidas de turma - a atividade de aula - que ela terminou.
 */
@Service
public class ConquistaEngineService {

    private static final Logger LOG = LoggerFactory.getLogger(ConquistaEngineService.class);

    // Resposta correta em menos de 3s conta como "rápida" (Raio Veloz etc.)
    private static final long RESPOSTA_RAPIDA_MS = 3000;

    // Metas por conquista - espelham as descrições do conquista_seed.csv
    private static final Map<String, Integer> METAS = Map.ofEntries(
        Map.entry("Primeiras Teclas", 1),
        Map.entry("Bem-vindo à Turma", 1),
        Map.entry("Aquecendo os Dedos", 10),
        Map.entry("Vocabulário em Construção", 50),
        Map.entry("Centena Digitada", 100),
        Map.entry("Dicionário Ambulante", 500),
        Map.entry("Mestre das Palavras", 1000),
        Map.entry("Sem Pressa e Sem Erro", 1),
        Map.entry("Perfeccionista", 5),
        Map.entry("Lenda da Ortografia", 20),
        Map.entry("Raio Veloz", 1),
        Map.entry("Dedos de Foguete", 10),
        Map.entry("Supersônico", 50),
        Map.entry("Primeira Vitória", 1),
        Map.entry("Campeão em Série", 5),
        Map.entry("Invencível", 10),
        Map.entry("Pódio Garantido", 10),
        Map.entry("Maratonista do Teclado", 25),
        Map.entry("Viciado em Digitar", 50),
        Map.entry("Frequência Perfeita", 7),
        Map.entry("Sequência de Ouro", 10),
        Map.entry("Sequência de Diamante", 25),
        Map.entry("Imparável", 50),
        Map.entry("Acentuação Nota 10", 20),
        Map.entry("Caçador de Cedilhas", 15),
        Map.entry("Subindo no Ranking", 1),
        Map.entry("Elite do Teclado", 1),
        Map.entry("Colecionador de XP", 1000),
        Map.entry("Milionário de XP", 5000),
        Map.entry("Madrugador", 1),
        Map.entry("Coruja do Teclado", 1),
        Map.entry("Primeiro Duelo", 1),
        Map.entry("Duelista", 5),
        Map.entry("Lenda dos Duelos", 25),
        Map.entry("Vença de um Desenvolvedor", 1),
        Map.entry("Aprendendo com os Erros", 1),
        Map.entry("Superação", 10),
        Map.entry("Explorador de Salas", 5),
        Map.entry("Aluno Dedicado", 10)
    );

    /**
     * Quanto falta para a conquista: a META dela (10 palavras, 5 vitórias...).
     *
     * A tela de conquistas usa para a porcentagem. Ela mostrava o progresso cru
     * como se já fosse porcentagem - 3 de 10 vitórias aparecia como "3%", e 150
     * de 1000 palavras como "100%" de uma conquista ainda bloqueada.
     * null quando o nome não tem regra no motor.
     */
    public static Integer metaDe(String nome) {
        return nome == null ? null : METAS.get(nome);
    }

    private static final List<String> POR_PALAVRA_CORRETA = List.of(
        "Aquecendo os Dedos",
        "Vocabulário em Construção",
        "Centena Digitada",
        "Dicionário Ambulante",
        "Mestre das Palavras"
    );
    private static final List<String> POR_RESPOSTA_RAPIDA = List.of("Raio Veloz", "Dedos de Foguete", "Supersônico");
    private static final List<String> POR_PARTIDA = List.of("Primeiras Teclas", "Maratonista do Teclado", "Viciado em Digitar");
    private static final List<String> POR_VITORIA = List.of("Primeira Vitória", "Campeão em Série", "Invencível");
    private static final List<String> POR_PARTIDA_PERFEITA = List.of("Sem Pressa e Sem Erro", "Perfeccionista", "Lenda da Ortografia");
    private static final List<String> POR_VITORIA_DUELO = List.of("Duelista", "Lenda dos Duelos");
    private static final List<String> POR_PALAVRA_RECUPERADA = List.of("Aprendendo com os Erros", "Superação");

    private final ConquistaRepository conquistaRepository;
    private final UsuarioConquistaRepository usuarioConquistaRepository;
    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;
    private final XpService xpService;
    private final ParticipanteSalaRepository participanteSalaRepository;

    public ConquistaEngineService(
        ConquistaRepository conquistaRepository,
        UsuarioConquistaRepository usuarioConquistaRepository,
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        XpService xpService,
        ParticipanteSalaRepository participanteSalaRepository
    ) {
        this.conquistaRepository = conquistaRepository;
        this.usuarioConquistaRepository = usuarioConquistaRepository;
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.xpService = xpService;
        this.participanteSalaRepository = participanteSalaRepository;
    }

    // Eventos do jogo

    /** Resposta dada numa partida (correta ou não), com o tempo gasto e a sequência atual de acertos. */
    @Transactional
    public void aoResponderNaPartida(String login, Palavra palavra, boolean correta, long tempoRespostaMs, int sequenciaAtual) {
        aoResponderNaPartida(login, palavra, correta, tempoRespostaMs, sequenciaAtual, false);
    }

    /**
     * Mesma regra, sabendo se a pessoa tinha ERRADO esta palavra da última vez que
     * a respondeu - é o que move "Aprendendo com os Erros" e "Superação". Quem
     * responde é o chamador, consultando o histórico ANTES de gravar a resposta nova.
     */
    @Transactional
    public void aoResponderNaPartida(
        String login,
        Palavra palavra,
        boolean correta,
        long tempoRespostaMs,
        int sequenciaAtual,
        boolean tinhaErradoAntes
    ) {
        if (!correta) {
            return; // conquistas de resposta só olham acertos; o erro zera a sequência no EstadoJogo
        }
        Optional<Usuario> usuarioOpt = resolverUsuario(login);
        if (usuarioOpt.isEmpty()) {
            return;
        }
        Usuario usuario = usuarioOpt.orElseThrow();

        POR_PALAVRA_CORRETA.forEach(nome -> incrementar(usuario, nome, 1));
        if (tempoRespostaMs < RESPOSTA_RAPIDA_MS) {
            POR_RESPOSTA_RAPIDA.forEach(nome -> incrementar(usuario, nome, 1));
        }
        if (Boolean.TRUE.equals(palavra.getPossuiAcento())) {
            incrementar(usuario, "Acentuação Nota 10", 1);
        }
        if (palavra.getTexto() != null && palavra.getTexto().toLowerCase().contains("ç")) {
            incrementar(usuario, "Caçador de Cedilhas", 1);
        }
        if (tinhaErradoAntes) {
            POR_PALAVRA_RECUPERADA.forEach(nome -> incrementar(usuario, nome, 1));
        }
        // Sequências: dispara exatamente quando a marca é atingida (== evita refazer a cada acerto)
        if (sequenciaAtual == 10) marco(usuario, "Sequência de Ouro");
        if (sequenciaAtual == 25) marco(usuario, "Sequência de Diamante");
        if (sequenciaAtual == 50) marco(usuario, "Imparável");

        verificarXpERankingInterno(usuario);
    }

    /** Fim de partida para um participante, com o resultado dele. */
    @Transactional
    public void aoConcluirPartida(String login, boolean venceu, boolean podio, boolean perfeita) {
        aoConcluirPartida(login, venceu, podio, perfeita, false);
    }

    /**
     * Fim de partida, sabendo se foi uma partida de TURMA - a atividade de aula,
     * que é o que "Aluno Dedicado" conta. Duelo e modo individual não entram.
     */
    @Transactional
    public void aoConcluirPartida(String login, boolean venceu, boolean podio, boolean perfeita, boolean turma) {
        Optional<Usuario> usuarioOpt = resolverUsuario(login);
        if (usuarioOpt.isEmpty()) {
            return;
        }
        Usuario usuario = usuarioOpt.orElseThrow();

        POR_PARTIDA.forEach(nome -> incrementar(usuario, nome, 1));
        incrementarDiaUnico(usuario, "Frequência Perfeita");
        if (venceu) {
            POR_VITORIA.forEach(nome -> incrementar(usuario, nome, 1));
        }
        if (podio) {
            incrementar(usuario, "Pódio Garantido", 1);
        }
        if (perfeita) {
            POR_PARTIDA_PERFEITA.forEach(nome -> incrementar(usuario, nome, 1));
        }
        if (turma) {
            incrementar(usuario, "Aluno Dedicado", 1);
        }
        int hora = LocalTime.now(PalavraDoDiaService.FUSO).getHour();
        if (hora < 8) marco(usuario, "Madrugador");
        if (hora >= 22) marco(usuario, "Coruja do Teclado");

        verificarXpERankingInterno(usuario);
    }

    /**
     * Fim de um duelo 1v1, para cada um dos dois jogadores. Além das conquistas de
     * volume/vitória do modo, valida "Vença de um Desenvolvedor": desbloqueia quando o
     * jogador VENCE um duelo cujo oponente é o usuário administrador (o desenvolvedor).
     */
    @Transactional
    public void aoConcluirDuelo(String login, boolean venceu, String oponenteLogin) {
        Optional<Usuario> usuarioOpt = resolverUsuario(login);
        if (usuarioOpt.isEmpty()) {
            return;
        }
        Usuario usuario = usuarioOpt.orElseThrow();

        incrementar(usuario, "Primeiro Duelo", 1);
        if (venceu) {
            POR_VITORIA_DUELO.forEach(nome -> incrementar(usuario, nome, 1));
            if (oponenteEhAdmin(oponenteLogin)) {
                marco(usuario, "Vença de um Desenvolvedor");
            }
        }

        verificarXpERankingInterno(usuario);
    }

    // O oponente é o usuário administrador do sistema? (checado pela authority, não pelo login)
    private boolean oponenteEhAdmin(String oponenteLogin) {
        if (oponenteLogin == null) {
            return false;
        }
        return userRepository
            .findOneWithAuthoritiesByLogin(oponenteLogin)
            .map(user -> user.getAuthorities().stream().anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName())))
            .orElse(false);
    }

    /**
     * Entrada em uma sala (chamado a cada conexão; idempotente após concluir).
     *
     * "Explorador de Salas" conta as salas de turma DIFERENTES em que a pessoa se
     * identificou - é a tabela de participantes, uma linha por sala e aluno, então
     * voltar à mesma sala não conta duas vezes.
     */
    @Transactional
    public void aoEntrarNaSala(String login) {
        resolverUsuario(login).ifPresent(usuario -> {
            marco(usuario, "Bem-vindo à Turma");
            long salas = participanteSalaRepository.countByLogin(login);
            if (salas > 0) {
                progressoAbsoluto(usuario, "Explorador de Salas", (int) Math.min(salas, Integer.MAX_VALUE));
            }
        });
    }

    /** Reavalia conquistas de XP acumulado e posição no ranking (ex.: após XP da palavra do dia). */
    @Transactional
    public void verificarXpERanking(String login) {
        resolverUsuario(login).ifPresent(this::verificarXpERankingInterno);
    }

    // Regras de XP acumulado e ranking

    // Desbloqueios creditam XP, o que pode atingir a próxima meta de XP - itera
    // até estabilizar (limitado: são poucas conquistas e todas idempotentes)
    private void verificarXpERankingInterno(Usuario usuario) {
        for (int i = 0; i < 3; i++) {
            Long xpAtual = usuarioRepository.lerXp(usuario.getId());
            long xp = xpAtual != null ? xpAtual : 0L;
            boolean desbloqueouAlgo = false;
            desbloqueouAlgo |= progressoAbsoluto(usuario, "Colecionador de XP", (int) Math.min(xp, 1000));
            desbloqueouAlgo |= progressoAbsoluto(usuario, "Milionário de XP", (int) Math.min(xp, 5000));
            long posicao = usuarioRepository.countByXpGreaterThan(xp) + 1;
            if (posicao <= 10) desbloqueouAlgo |= marco(usuario, "Subindo no Ranking");
            if (posicao <= 3) desbloqueouAlgo |= marco(usuario, "Elite do Teclado");
            if (!desbloqueouAlgo) {
                return;
            }
        }
    }

    // Mecânica de progresso

    /** Soma incremento ao progresso; desbloqueia ao atingir a meta. Retorna true se desbloqueou agora. */
    private boolean incrementar(Usuario usuario, String nome, int incremento) {
        return atualizar(usuario, nome, atual -> atual + incremento);
    }

    /** Define o progresso para um valor absoluto (ex.: XP acumulado). */
    private boolean progressoAbsoluto(Usuario usuario, String nome, int valor) {
        return atualizar(usuario, nome, atual -> Math.max(atual, valor));
    }

    /** Conquista de ocorrência única: marca a meta cheia de uma vez. */
    private boolean marco(Usuario usuario, String nome) {
        return atualizar(usuario, nome, atual -> METAS.getOrDefault(nome, 1));
    }

    /** Conta no máximo uma vez por dia (fuso do jogo) - usado por "Frequência Perfeita". */
    private void incrementarDiaUnico(Usuario usuario, String nome) {
        Optional<UsuarioConquista> ucOpt = obterRegistro(usuario, nome);
        if (ucOpt.isEmpty()) {
            return;
        }
        UsuarioConquista uc = ucOpt.orElseThrow();
        if (Boolean.TRUE.equals(uc.getConcluida())) {
            return;
        }
        LocalDate hoje = LocalDate.now(PalavraDoDiaService.FUSO);
        if (uc.getDataConquista() != null && hoje.equals(LocalDate.ofInstant(uc.getDataConquista(), PalavraDoDiaService.FUSO))) {
            return; // hoje já contou
        }
        uc.setProgresso(progressoDe(uc) + 1);
        uc.setDataConquista(Instant.now());
        concluirSeAtingiuMeta(uc, usuario, nome);
        usuarioConquistaRepository.save(uc);
    }

    private boolean atualizar(Usuario usuario, String nome, java.util.function.IntUnaryOperator novoProgresso) {
        Optional<UsuarioConquista> ucOpt = obterRegistro(usuario, nome);
        if (ucOpt.isEmpty()) {
            return false;
        }
        UsuarioConquista uc = ucOpt.orElseThrow();
        if (Boolean.TRUE.equals(uc.getConcluida())) {
            return false;
        }
        uc.setProgresso(novoProgresso.applyAsInt(progressoDe(uc)));
        boolean desbloqueou = concluirSeAtingiuMeta(uc, usuario, nome);
        usuarioConquistaRepository.save(uc);
        return desbloqueou;
    }

    private boolean concluirSeAtingiuMeta(UsuarioConquista uc, Usuario usuario, String nome) {
        int meta = METAS.getOrDefault(nome, Integer.MAX_VALUE);
        if (progressoDe(uc) < meta) {
            return false;
        }
        uc.setProgresso(meta);
        uc.setConcluida(true);
        uc.setDataConquista(Instant.now());
        Integer xp = uc.getConquista() != null ? uc.getConquista().getXpRecompensa() : null;
        xpService.creditar(usuario.getId(), xp != null ? xp : 0, "conquista \"" + nome + "\"");
        LOG.info("Conquista desbloqueada: \"{}\" para usuario {} (+{} XP)", nome, usuario.getId(), xp);
        return true;
    }

    // Busca (ou cria zerado) o registro de progresso do aluno para a conquista.
    // Se a conquista não existir no catálogo, retorna vazio e o evento é ignorado.
    private Optional<UsuarioConquista> obterRegistro(Usuario usuario, String nome) {
        Optional<Conquista> conquista = conquistaRepository.findFirstByNome(nome);
        if (conquista.isEmpty()) {
            LOG.warn("Conquista \"{}\" não encontrada no catálogo - evento ignorado", nome);
            return Optional.empty();
        }
        return Optional.of(
            usuarioConquistaRepository
                .findFirstByAlunoIdAndConquistaId(usuario.getId(), conquista.orElseThrow().getId())
                .orElseGet(() -> {
                    UsuarioConquista novo = new UsuarioConquista();
                    novo.setAluno(usuario);
                    novo.setConquista(conquista.orElseThrow());
                    novo.setProgresso(0);
                    novo.setConcluida(false);
                    return novo;
                })
        );
    }

    private int progressoDe(UsuarioConquista uc) {
        return uc.getProgresso() != null ? uc.getProgresso() : 0;
    }

    // Mesmo caminho de resolução do resto do app: login -> User -> Usuario (pelo e-mail)
    private Optional<Usuario> resolverUsuario(String login) {
        if (login == null) {
            return Optional.empty();
        }
        return userRepository.findOneByLogin(login).flatMap(user -> usuarioRepository.findByEmail(user.getEmail()));
    }
}
