package br.com.digitado.service;

import br.com.digitado.domain.HistoricoResposta;
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

    // folga de rede: aceita resposta até 2s depois do tempo da rodada
    private static final long FOLGA_RESPOSTA_MS = 2000;

    // Espaço fixo (U+00A0): vem em texto colado de documento e sobrevive ao trim,
    // que só apara caracteres de controle e o espaço comum
    private static final char ESPACO_FIXO = (char) 0x00A0;

    // ninguém digita mais rápido que ~80ms por letra; abaixo disso trato como bot
    private static final long MIN_MS_POR_LETRA = 80;

    private final PalavraRepository palavraRepository;
    private final PalavraEstatisticaService palavraEstatisticaService;
    private final ConquistaEngineService conquistaEngine;
    private final EstatisticaPartidaService estatisticaPartidaService;
    private final HistoricoRespostaService historicoRespostaService;

    // Mapa em memória: código da sala → estado do jogo
    private final Map<String, EstadoJogo> jogos = new ConcurrentHashMap<>();

    /**
     * Ordem do placar: mais pontos primeiro e, no empate, nome e login em ordem
     * alfabética.
     *
     * O desempate importa: o placar vive num ConcurrentHashMap, cuja ordem de
     * iteração é arbitrária e muda conforme entram participantes. Sem um critério
     * fixo, quem estava empatado trocava de lugar a cada mensagem do WebSocket e o
     * ranking ao vivo parecia embaralhar sozinho entre uma resposta e outra.
     */
    private static final Comparator<Map.Entry<String, EstadoJogo.AlunoInfo>> ORDEM_PLACAR = Comparator.comparingInt(
        (Map.Entry<String, EstadoJogo.AlunoInfo> e) -> e.getValue().pontos()
    )
        .reversed()
        .thenComparing(e -> e.getValue().nome() != null ? e.getValue().nome() : "", String.CASE_INSENSITIVE_ORDER)
        .thenComparing(Map.Entry::getKey);

    public JogoSalaService(
        PalavraRepository palavraRepository,
        PalavraEstatisticaService palavraEstatisticaService,
        ConquistaEngineService conquistaEngine,
        EstatisticaPartidaService estatisticaPartidaService,
        HistoricoRespostaService historicoRespostaService
    ) {
        this.palavraRepository = palavraRepository;
        this.palavraEstatisticaService = palavraEstatisticaService;
        this.conquistaEngine = conquistaEngine;
        this.estatisticaPartidaService = estatisticaPartidaService;
        this.historicoRespostaService = historicoRespostaService;
    }

    // Registra um participante na sala (cria o estado da sala se ainda não existir).
    // sessaoId: id da sessão WebSocket desta conexão - o mesmo aluno pode ter mais de
    // uma aberta (recarregou a página, reconectou, abriu outra aba) e só sai da sala
    // quando a ÚLTIMA delas cai.
    public void registrarAluno(String codigoSala, String login, String nome, String sessaoId) {
        jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo()).registrarAluno(login, nome, sessaoId);
    }

    /**
     * Registra quem COMANDA a sala de turma. Ele conecta como todo mundo (a sala não
     * pode ser considerada vazia enquanto o professor está nela), mas não entra no
     * placar nem na lista de alunos: comanda a partida, não compete.
     *
     * Também limpa um placar herdado: se em algum momento este login foi registrado
     * como aluno da sala, a entrada some daqui - era ela que fazia o professor
     * aparecer na tela de espera e ocupar uma posição no ranking da turma.
     */
    public void registrarProfessor(String codigoSala, String login, String nome, String sessaoId) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        jogo.registrarProfessor(login, nome, sessaoId);
    }

    // Registra um participante num duelo 1v1, respeitando o limite de 2 jogadores.
    // Retorna false se a sala já está cheia (e o login não é um dos dois que já estão nela -
    // reconexão de quem já participa é sempre aceita).
    public boolean registrarNoDuelo(String codigoSala, String login, String nome, String sessaoId) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        synchronized (jogo) {
            jogo.marcarModo1v1();
            if (jogo.totalConectados() >= 2 && !jogo.getAlunosConectados().containsKey(login)) {
                return false;
            }
            jogo.registrarAluno(login, nome, sessaoId);
            return true;
        }
    }

    // Quantos JOGADORES estão conectados na sala agora (0 se a sala nem tem estado em
    // memória). O professor da turma não conta: ele comanda a sala, não joga - a
    // listagem mostraria "1 conectado" numa sala em que ninguém entrou ainda.
    public int conectadosNaSala(String codigoSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        return jogo != null ? jogo.totalJogadores() : 0;
    }

    // salasComSaida: salas de onde o jogador saiu; salasVazias: as que ficaram sem ninguém
    // (o chamador fecha essas no banco)
    public record ResultadoDesconexao(List<String> salasComSaida, List<String> salasVazias) {}

    /**
     * Fecha UMA sessão WebSocket do usuário em todas as salas em que ela estava e
     * devolve quais ficaram vazias, pra fechar depois.
     *
     * A saída é por SESSÃO, não por login: recarregar a página (ou uma reconexão de
     * rede) abre a sessão nova antes de a antiga ser encerrada, e remover pelo login
     * apagava da sala um aluno que continuava conectado - a tela de espera do
     * professor mostrava menos gente do que realmente tinha entrado.
     */
    public ResultadoDesconexao aoDesconectar(String login, String sessaoId) {
        List<String> salasComSaida = new ArrayList<>();
        List<String> salasVazias = new ArrayList<>();
        jogos.forEach((codigo, jogo) -> {
            // ehJogador ANTES de remover: depois da saída o estado já não sabe quem era
            boolean eraJogador = jogo.ehJogador(login);
            if (jogo.removerSessao(login, sessaoId)) {
                salasComSaida.add(codigo);
                // Saiu o último JOGADOR e não ficou mais ninguém: sala abandonada.
                // A saída do professor não fecha a sala por si só - ele recarregar a
                // tela de espera, sozinho, fechava a sala que ele acabou de abrir.
                if (eraJogador && jogo.totalConectados() == 0) {
                    salasVazias.add(codigo);
                }
            }
        });
        // sala vazia = estado descartado, mas o que foi jogado é gravado antes
        salasVazias.forEach(this::descartarSala);
        return new ResultadoDesconexao(salasComSaida, salasVazias);
    }

    /**
     * Tira a sala do mapa quando o professor a fecha ou o último participante
     * sai - senão o estado ficaria na memória até reiniciar o servidor.
     *
     * ANTES de soltar o estado, grava o snapshot do que foi jogado. Sem isto, só
     * a partida levada até o fim (botão "Encerrar" ou última palavra) era salva:
     * fechar a sala no meio do jogo, ou todo mundo sair da tela, apagava o
     * desempenho da turma - e a sala reaberta voltava zerada, sem nada para ver.
     */
    public void descartarSala(String codigoSala) {
        EstadoJogo jogo = jogos.remove(codigoSala);
        // Só grava o que tem conteúdo: uma partida recém-iniciada, sem nenhuma
        // resposta, não pode substituir o snapshot da partida anterior - que é
        // justamente o que o professor quer rever. O caminho do encerramento
        // normal (botão "Encerrar" / última palavra) grava sempre: ali o
        // professor decidiu que a partida acabou.
        if (jogo != null && jogo.temPartida() && jogo.temRespostas()) {
            salvarEstatisticas(codigoSala, jogo, jogo.getLoginProfessor());
        }
    }

    // Descarta o estado SEM gravar nada: usado quando a sala é excluída do banco.
    // Salvar aqui criaria um snapshot órfão (a FK aponta para uma sala que não
    // existe mais) logo depois do DELETE.
    public void descartarSalaSemSalvar(String codigoSala) {
        jogos.remove(codigoSala);
    }

    // guarda o nome da sala em cache pra não fazer um SELECT a cada mensagem do
    // WebSocket. A primeira chamada busca no banco e guarda, as próximas leem da memória.
    public String nomeSalaCacheado(String codigoSala, java.util.function.Supplier<String> resolver) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        String nome = jogo.getNomeSala();
        if (nome == null) {
            nome = resolver.get();
            jogo.setNomeSala(nome);
        }
        return nome;
    }

    // Inicia o jogo: sorteia as palavras conforme a configuração escolhida pelo professor,
    // adiciona quaisquer palavras extras selecionadas manualmente e embaralha tudo.
    // loginProfessor: quem comanda a sala - guardado no estado para que o snapshot
    // saiba quem excluir do ranking mesmo se a sala for fechada no meio da partida.
    public EstadoJogoDTO iniciar(String codigoSala, String nomeSala, IniciarPayload payload, String loginProfessor) {
        EstadoJogo jogo = jogos.computeIfAbsent(codigoSala, k -> new EstadoJogo());
        jogo.setLoginProfessor(loginProfessor);
        // Palavras da PARTIDA ANTERIOR desta sala ficam fora do sorteio - evita que
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
        // pode faltar palavra numa faixa de dificuldade, então completo o que faltou
        // sorteando entre as outras ativas pra bater o total que o professor pediu
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
    // loginProfessor: quem comanda a sala - excluído da contagem de silenciosos.
    public EstadoJogoDTO proximaPalavra(String codigoSala, String nomeSala, String loginProfessor) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) return null;
        contabilizarSilenciosos(jogo, loginProfessor);
        boolean temProxima = jogo.avancar();
        if (!temProxima) {
            premiarFimDePartida(jogo);
            salvarEstatisticas(codigoSala, jogo, loginProfessor);
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
        salvarEstatisticas(codigoSala, jogo, loginProfessor);
        return buildEstado(codigoSala, nomeSala, jogo, "ENCERRADA");
    }

    /**
     * Grava o consolidado da partida que acabou de encerrar (ranking final +
     * relatório por palavra) para a tela "Ver estatísticas" do professor.
     *
     * Sem este snapshot o desempenho da turma vivia só no EstadoJogo em memória:
     * fechar a sala chamava descartarSala e apagava tudo, e reabri-la devolvia
     * uma sala zerada.
     *
     * O professor fica FORA do ranking gravado - ele comanda a partida, não
     * compete (mesma regra da tela e de contabilizarSilenciosos). No duelo 1v1
     * não há isenção: o criador joga como qualquer participante.
     */
    private void salvarEstatisticas(String codigoSala, EstadoJogo jogo, String loginProfessor) {
        // Fixa quem comanda esta sala: se ela for fechada depois (sem passar por
        // aqui de novo), o descarte ainda sabe quem deixar fora do ranking
        jogo.setLoginProfessor(loginProfessor);
        List<EstatisticaPartidaService.RankingEntrada> ranking = jogo
            .getPlacar()
            .entrySet()
            .stream()
            .filter(e -> jogo.isModo1v1() || !e.getKey().equals(loginProfessor))
            .sorted(ORDEM_PLACAR)
            .map(e ->
                new EstatisticaPartidaService.RankingEntrada(
                    e.getKey(),
                    e.getValue().nome(),
                    e.getValue().pontos(),
                    jogo.getAlertas(e.getKey())
                )
            )
            .toList();
        estatisticaPartidaService.salvar(codigoSala, jogo.getTotalPalavras(), ranking, jogo.gerarRelatorio());
    }

    // Quem estava conectado e NÃO respondeu a palavra da rodada conta como
    // tentativa errada nas estatísticas - mas só quando o tempo realmente esgotou
    // (protege contra avanço duplo/precoce contaminar os números). O professor
    // que comanda a sala fica de fora: ele não é obrigado a jogar. No duelo 1v1
    // não há isenção: o criador joga como qualquer participante.
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
            // ehJogador já conhece o dono da sala (gravado na entrada); o loginProfessor
            // da mensagem cobre a sala cujo estado nasceu antes desta correção
            boolean isento = !jogo.ehJogador(login) || (!jogo.isModo1v1() && login.equals(loginProfessor));
            if (!isento && !jogo.jaRespondeu(login)) {
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
        // Conquistas exclusivas do modo Duelo 1v1 - só valem com os dois oponentes
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

    // ===== Relatório da partida (tela do professor) =====

    /**
     * Uma resposta digitada por um jogador em uma rodada, exatamente como chegou:
     * quem respondeu (login + nome de exibição), o texto digitado, se acertou e a
     * ordem de chegada (1º, 2º...). Alimenta o relatório "quem escreveu o quê".
     */
    public record RespostaDetalhe(String login, String nome, String texto, boolean correta, int ordem) {}

    /**
     * Consolidado de UMA palavra da partida para o relatório do professor:
     * o texto e a dificuldade da palavra, os totais (quantos responderam e
     * quantos acertaram - o % é calculado no frontend) e a lista de respostas.
     */
    public record RelatorioPalavra(
        int indice,
        String texto,
        String dificuldade,
        int totalRespostas,
        int totalAcertos,
        List<RespostaDetalhe> respostas
    ) {}

    /**
     * Monta o relatório da partida em andamento (ou recém-encerrada) da sala:
     * uma entrada por palavra JÁ JOGADA, com as respostas de cada jogador.
     *
     * Exposto via GET /api/salas/{codigo}/relatorio, restrito ao professor dono
     * (ou admin) - é ele quem vê o texto das palavras e as respostas dos alunos.
     * Sala sem estado em memória (servidor reiniciado / partida não iniciada)
     * devolve lista vazia, e o frontend trata como "sem dados ainda".
     */
    public List<RelatorioPalavra> gerarRelatorio(String codigoSala) {
        EstadoJogo jogo = jogos.get(codigoSala);
        if (jogo == null) {
            return List.of();
        }
        return jogo.gerarRelatorio();
    }

    // Resultado de uma resposta: contém o feedback individual + o estado atualizado da
    // sala. Numa resposta RECUSADA o estado vem null - nada mudou no placar, então não
    // há o que retransmitir para a sala inteira, só o aviso para quem respondeu.
    public record ResultadoResposta(FeedbackAluno feedback, EstadoJogoDTO estado) {}

    // Aviso de resposta não contabilizada. Não devolve o texto da palavra: a rodada
    // pode continuar aberta para os outros e ninguém recebe a resposta antes da hora.
    private FeedbackAluno recusa(String motivo) {
        return new FeedbackAluno(false, 0, 0, motivo, null, false);
    }

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
        // Cada aluno só pode responder uma vez por palavra. Aqui não mandamos recusa:
        // o feedback da primeira resposta já chegou e uma segunda mensagem só faria a
        // tela do aluno trocar o resultado bom por um aviso.
        if (jogo.jaRespondeu(login)) return null;
        // O relógio é validado NO SERVIDOR: rodada precisa estar ativa e dentro do
        // tempo (com folga para latência) - um cliente adulterado não responde
        // depois que o tempo esgota nem durante pausa/encerramento.
        // A recusa VOLTA para o aluno (registrada = false): sem ela a tela mantinha o
        // "você acertou" da conferência local, e ele não entendia por que ficou sem ponto.
        if (!"NOVA_PALAVRA".equals(jogo.getTipo())) {
            return new ResultadoResposta(recusa("RODADA_ENCERRADA"), null);
        }
        long decorrido = Instant.now().toEpochMilli() - jogo.getTimestampInicio();
        if (decorrido > jogo.getTempoLimite() * 1000L + FOLGA_RESPOSTA_MS) {
            return new ResultadoResposta(recusa("TEMPO_ESGOTADO"), null);
        }

        String textoCorreto = jogo.getPalavraAtual().getTexto();
        String dLower = canonico(respostaDigitada);
        String cLower = canonico(textoCorreto);
        boolean correta = dLower.equals(cLower);

        // marca como suspeita se tentou colar/corretor ou respondeu rápido demais.
        // a resposta ainda vale, só mostra um alerta no placar do professor
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

        // só conta a estatística da palavra em duelo 1v1; sala de professor fica de
        // fora pra turma não distorcer a dificuldade
        if (jogo.isModo1v1()) {
            palavraEstatisticaService.registrarTentativa(jogo.getPalavraAtual().getId(), correta);
        }

        // Histórico PESSOAL da resposta - é o que alimenta o painel "Meu Desempenho"
        // do jogador (quais palavras ele mais erra, como a taxa dele evoluiu). Os
        // contadores da palavra são do acervo inteiro e não sabem quem respondeu.
        // Grava sempre, inclusive em sala de professor: aqui não há risco de
        // distorcer a dificuldade da palavra - o número é só do jogador.
        historicoRespostaService.registrar(
            login,
            jogo.getPalavraAtual(),
            correta,
            tipoErro,
            (int) decorrido,
            jogo.isModo1v1() ? HistoricoResposta.Origem.DUELO : HistoricoResposta.Origem.PARTIDA
        );

        // Registra a resposta: ordem de chegada (relatório) e ordem entre os acertos (pontuação)
        EstadoJogo.Chegada chegada = jogo.registrarResposta(login, correta);
        // guarda o que o aluno digitou de fato, pro relatório do professor - com o nome
        // público já resolvido na entrada, não com o login
        String nomePublico = jogo.nomeNoPlacar(login, nomeAluno);
        jogo.registrarRespostaDetalhada(login, nomePublico, respostaDigitada.trim(), correta, chegada.ordem());
        int pontos = 0;
        if (correta) {
            // Pontuação = base (pela ordem entre os ACERTOS) + bônus de velocidade
            // proporcional ao tempo restante. Quem erra não "gasta" as posições de
            // pontuação: o 1º a acertar leva os 20 pontos ainda que três colegas
            // tenham mandado a resposta errada antes dele.
            int idx = Math.min(chegada.ordemAcerto() - 1, PONTOS_BASE.length - 1);
            int base = PONTOS_BASE[idx];
            long elapsed = Instant.now().toEpochMilli() - jogo.getTimestampInicio();
            double fracao = Math.max(0, 1.0 - (double) elapsed / (jogo.getTempoLimite() * 1000L));
            int bonus = (int) Math.round(BONUS_MAX[idx] * fracao);
            pontos = base + bonus;
        }
        jogo.adicionarPontos(login, nomePublico, pontos);

        // Motor de conquistas: acerto, rapidez, acentos/cedilha e sequência.
        // Transação própria e try/catch - conquista nunca derruba a partida.
        try {
            conquistaEngine.aoResponderNaPartida(login, jogo.getPalavraAtual(), correta, decorrido, jogo.getSequenciaAcertos(login));
        } catch (Exception e) {
            LOG.error("Falha ao processar conquistas da resposta de {}: {}", login, e.getMessage(), e);
        }

        // ordem do feedback = posição entre os acertos (é o "Nº a acertar" da tela)
        FeedbackAluno feedback = new FeedbackAluno(correta, pontos, chegada.ordemAcerto(), tipoErro, textoCorreto, true);
        EstadoJogoDTO estado = buildEstado(codigoSala, nomeSala, jogo, jogo.getTipo());
        return new ResultadoResposta(feedback, estado);
    }

    /**
     * Forma canônica usada para decidir se a resposta bate com a palavra.
     *
     * NFC: "ã" pode chegar como um caractere só ou como "a" + til combinante. Para
     * quem lê é a mesma letra, mas como texto são sequências diferentes - sem
     * normalizar, uma palavra cadastrada por cópia de um documento (é comum vir
     * decomposta) marcava como ERRADA a resposta digitada exatamente igual, e o aluno
     * terminava a rodada sem ponto jurando que tinha acertado.
     *
     * O espaço fixo (NBSP) também vem nessas colagens e sobrevive ao trim comum, por
     * isso vira espaço normal antes de aparar as pontas.
     */
    private String canonico(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFC).replace(ESPACO_FIXO, ' ').trim().toLowerCase(Locale.ROOT);
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
    // para transformar uma string na outra - usado para classificar erros de digitação
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
        // Placar ordenado do maior para o menor pontuação. O professor da turma fica
        // de fora do placar e da lista de conectados: ele comanda a partida, não
        // compete - e ocupando uma linha ele empurrava a posição de todos os alunos.
        List<PlacarEntry> placar = jogo
            .getPlacar()
            .entrySet()
            .stream()
            .filter(e -> jogo.ehJogador(e.getKey()))
            .sorted(ORDEM_PLACAR)
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
            .filter(e -> jogo.ehJogador(e.getKey()))
            .sorted(Map.Entry.comparingByValue(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .map(e -> new EntradaAluno(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
        return new EstadoJogoDTO(
            tipo,
            palavraDTO,
            jogo.getIndiceAtual(),
            jogo.getTotalPalavras(),
            jogo.getTempoLimite(),
            jogo.getTimestampInicio(),
            // Hora do servidor no instante do envio: referência para o cliente
            // corrigir a diferença do próprio relógio antes de contar o tempo
            Instant.now().toEpochMilli(),
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

    // Total de alunos conectados somando todas as salas (professores de turma não
    // entram na conta: a métrica é de alunos jogando)
    public int totalAlunosConectados() {
        return jogos.values().stream().mapToInt(EstadoJogo::totalJogadores).sum();
    }

    // Estado interno de uma sala de jogo - mantido em memória enquanto o servidor está rodando.
    // Usa ConcurrentHashMap para suportar múltiplos jogadores respondendo ao mesmo tempo.
    public static class EstadoJogo {

        private List<Palavra> palavras = new ArrayList<>();
        private int indiceAtual = -1;
        // Tempo de rodada por dificuldade - o tempo efetivo depende da palavra atual
        private int tempoFacil = 30;
        private int tempoMedio = 30;
        private int tempoDificil = 30;
        private long timestampInicio = 0;
        private String tipo = "AGUARDANDO";
        private final Map<String, AlunoInfo> placar = new ConcurrentHashMap<>();
        private final Map<String, String> alunosConectados = new ConcurrentHashMap<>();
        /**
         * Sessões WebSocket abertas de cada participante (login → ids de sessão).
         *
         * Uma pessoa pode ter mais de uma ao mesmo tempo: recarregar a página abre a
         * nova antes de o servidor receber a desconexão da antiga, e uma queda de rede
         * reconecta do mesmo jeito. Só quando a ÚLTIMA sessão de um login cai é que ele
         * sai mesmo da sala.
         */
        private final Map<String, Set<String>> sessoesPorLogin = new ConcurrentHashMap<>();
        // Conjunto dos logins que já responderam na rodada atual (evita resposta dupla)
        private final Set<String> respondeuNaRodada = ConcurrentHashMap.newKeySet();
        // Respostas suspeitas (colar/corretor bloqueado ou rápida demais) por jogador na partida
        private final Map<String, Integer> alertasBurla = new ConcurrentHashMap<>();
        private int ordemRespostas = 0;
        // Ordem entre os ACERTOS da rodada: é ela que define a pontuação base e o
        // "Nº a acertar" do feedback. Separada de ordemRespostas (que conta qualquer
        // resposta, certa ou errada) - senão quem acertasse depois de três colegas
        // errarem levava a pontuação de 4º colocado.
        private int ordemAcertos = 0;
        // Rastreamento da PARTIDA para conquistas: sequência de acertos por jogador,
        // estatística acumulada (respostas/acertos) e flag de fim já premiado
        private final Map<String, Integer> sequenciaAcertos = new ConcurrentHashMap<>();
        private final Map<String, int[]> estatisticasPartida = new ConcurrentHashMap<>();
        private volatile boolean fimPremiado = false;
        // Sala de duelo 1v1: no máximo 2 jogadores e conquistas próprias no fim
        private volatile boolean modo1v1 = false;

        // Nome da sala (cache): resolvido no banco uma única vez por sala - ver
        // JogoSalaService.nomeSalaCacheado. volatile: escrito por uma mensagem
        // WebSocket e lido pelas seguintes, possivelmente em threads diferentes.
        private volatile String nomeSala;

        // Login de quem comanda a sala, guardado desde o início da partida: é
        // quem fica FORA do ranking gravado. Sem isto, o snapshot feito no
        // descarte da sala (fechamento no meio do jogo) não teria como saber
        // quem é o professor - a mensagem que fecha a sala não passa por aqui.
        private volatile String loginProfessor;

        /**
         * Registro DETALHADO da partida para o relatório do professor:
         * índice da palavra (posição na lista embaralhada) → lista de respostas
         * digitadas naquela rodada, na ordem de chegada.
         *
         * É daqui que sai o relatório "quem escreveu o quê" - tanto o painel ao
         * vivo (contagem e % de acerto por palavra) quanto o relatório final.
         * Vive apenas em memória, como todo o EstadoJogo: reiniciar o servidor
         * descarta o histórico da partida em andamento.
         */
        private final Map<Integer, List<RespostaDetalhe>> respostasDetalhadas = new ConcurrentHashMap<>();

        public record AlunoInfo(String nome, int pontos, String statusAtual) {}

        /**
         * Posição de chegada de uma resposta na rodada:
         * - ordem: entre TODAS as respostas (alimenta o relatório "quem escreveu o quê");
         * - ordemAcerto: entre os ACERTOS (define a pontuação e o "Nº a acertar"); 0 quando errou.
         */
        public record Chegada(int ordem, int ordemAcerto) {}

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
            ordemAcertos = 0;
            sequenciaAcertos.clear();
            estatisticasPartida.clear();
            alertasBurla.clear();
            // Partida nova = relatório novo: as respostas da partida anterior são descartadas
            respostasDetalhadas.clear();
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
            ordemAcertos = 0;
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
        // e, quando acertou, a posição entre os acertos da rodada
        synchronized Chegada registrarResposta(String login, boolean correta) {
            respondeuNaRodada.add(login);
            int ordem = ++ordemRespostas;
            int ordemAcerto = correta ? ++ordemAcertos : 0;
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
            return new Chegada(ordem, ordemAcerto);
        }

        /**
         * Anexa a resposta digitada ao histórico da PALAVRA ATUAL (indiceAtual).
         * Chamado logo após registrarResposta - que já garantiu resposta única por
         * jogador na rodada e definiu a ordem de chegada. A lista é sincronizada
         * porque vários alunos respondem ao mesmo tempo.
         */
        void registrarRespostaDetalhada(String login, String nome, String texto, boolean correta, int ordem) {
            respostasDetalhadas
                .computeIfAbsent(indiceAtual, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new RespostaDetalhe(login, nome, texto, correta, ordem));
        }

        /**
         * Consolida o relatório da partida: uma entrada por palavra já jogada
         * (índice 0 até o atual, inclusive - a rodada em curso entra com as
         * respostas que já chegaram, alimentando o painel ao vivo do professor).
         * Palavras ainda não sorteadas para jogo ficam de fora: o relatório nunca
         * antecipa o que vem pela frente.
         */
        List<RelatorioPalavra> gerarRelatorio() {
            List<RelatorioPalavra> relatorio = new ArrayList<>();
            // Partida encerrada: indiceAtual pode ter passado do fim da lista
            int ultimo = Math.min(indiceAtual, palavras.size() - 1);
            for (int i = 0; i <= ultimo; i++) {
                Palavra p = palavras.get(i);
                List<RespostaDetalhe> respostas = respostasDetalhadas.getOrDefault(i, List.of());
                // Cópia defensiva: a rodada atual ainda recebe respostas enquanto isto roda
                List<RespostaDetalhe> copia;
                synchronized (respostas) {
                    copia = List.copyOf(respostas);
                }
                int acertos = (int) copia.stream().filter(RespostaDetalhe::correta).count();
                relatorio.add(
                    new RelatorioPalavra(
                        i,
                        p.getTexto(),
                        p.getDificuldade() != null ? p.getDificuldade().name() : null,
                        copia.size(),
                        acertos,
                        copia
                    )
                );
            }
            return relatorio;
        }

        // Nome público do participante, como ficou registrado na entrada da sala.
        // O fallback só vale para quem, por algum motivo, ainda não está no placar.
        String nomeNoPlacar(String login, String fallback) {
            AlunoInfo info = placar.get(login);
            if (info != null && info.nome() != null && !info.nome().isBlank()) {
                return info.nome();
            }
            String conectado = alunosConectados.get(login);
            return conectado != null && !conectado.isBlank() ? conectado : fallback;
        }

        void adicionarPontos(String login, String nome, int pontos) {
            AlunoInfo atual = placar.getOrDefault(login, new AlunoInfo(nome, 0, "AGUARDANDO"));
            placar.put(login, new AlunoInfo(atual.nome(), atual.pontos() + pontos, atual.statusAtual()));
        }

        // Garante que o aluno apareça na lista de conectados e no placar (com 0 pontos).
        // synchronized com removerSessao: entrada e saída mexem nos dois mapas juntos, e
        // elas se cruzam justamente na reconexão, quando a sessão nova chega antes de a
        // antiga ser encerrada.
        synchronized void registrarAluno(String login, String nome, String sessaoId) {
            alunosConectados.put(login, nome);
            abrirSessao(login, sessaoId);
            placar.computeIfAbsent(login, k -> new AlunoInfo(nome, 0, "AGUARDANDO"));
        }

        // Professor da turma: conecta (a sala não está vazia enquanto ele está nela),
        // mas fica fora do placar e da lista de alunos. Remove também um placar herdado
        // de quando este login entrou como aluno nesta mesma sala.
        synchronized void registrarProfessor(String login, String nome, String sessaoId) {
            setLoginProfessor(login);
            alunosConectados.put(login, nome);
            abrirSessao(login, sessaoId);
            placar.remove(login);
        }

        private void abrirSessao(String login, String sessaoId) {
            if (sessaoId != null) {
                sessoesPorLogin.computeIfAbsent(login, k -> ConcurrentHashMap.newKeySet()).add(sessaoId);
            }
        }

        /**
         * Fecha uma sessão do participante. Devolve true só quando ele realmente saiu
         * da sala - ou seja, quando essa era a última sessão aberta dele.
         */
        synchronized boolean removerSessao(String login, String sessaoId) {
            Set<String> sessoes = sessoesPorLogin.get(login);
            if (sessoes != null && sessaoId != null) {
                sessoes.remove(sessaoId);
                // Ainda tem outra aba/conexão viva: continua na sala
                if (!sessoes.isEmpty()) {
                    return false;
                }
            }
            sessoesPorLogin.remove(login);
            return alunosConectados.remove(login) != null;
        }

        Palavra getPalavraAtual() {
            return (indiceAtual >= 0 && indiceAtual < palavras.size()) ? palavras.get(indiceAtual) : null;
        }

        // Ids das palavras carregadas nesta sala (da partida em curso ou da última
        // encerrada) - usados para não repetir palavras na partida seguinte
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

        String getNomeSala() {
            return nomeSala;
        }

        void setNomeSala(String nomeSala) {
            this.nomeSala = nomeSala;
        }

        String getLoginProfessor() {
            return loginProfessor;
        }

        // Nunca apaga um dono já conhecido: chamadas sem o login (descarte da sala)
        // não podem zerar quem foi gravado no início da partida
        void setLoginProfessor(String loginProfessor) {
            if (loginProfessor != null) {
                this.loginProfessor = loginProfessor;
            }
        }

        // Já houve partida nesta sala? (palavras sorteadas e pelo menos a 1ª rodada
        // aberta) - só assim o descarte da sala tem o que gravar
        boolean temPartida() {
            return !palavras.isEmpty() && indiceAtual >= 0;
        }

        // Alguém chegou a responder nesta partida?
        boolean temRespostas() {
            return !respostasDetalhadas.isEmpty();
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

        // Quantidade de participantes conectados nesta sala, professor incluído.
        // É a conta do CICLO DE VIDA: enquanto for maior que zero a sala tem gente
        // dentro e não pode ser descartada nem fechada no banco.
        int totalConectados() {
            return alunosConectados.size();
        }

        // Quantidade de JOGADORES conectados: o professor da turma fica de fora
        // (no duelo 1v1 o criador joga, então lá ninguém é descontado)
        int totalJogadores() {
            return (int) alunosConectados.keySet().stream().filter(this::ehJogador).count();
        }

        // O login joga nesta sala? Só o professor da sala de turma não joga.
        boolean ehJogador(String login) {
            return modo1v1 || !login.equals(loginProfessor);
        }
    }
}
