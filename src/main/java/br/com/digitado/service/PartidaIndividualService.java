package br.com.digitado.service;

import br.com.digitado.domain.HistoricoResposta;
import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.PartidaIndividual;
import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.repository.PartidaIndividualRepository;
import br.com.digitado.service.EquacaoPalavraIndividual.Candidata;
import br.com.digitado.service.HistoricoRespostaService.EstatisticaPalavraJogador;
import br.com.digitado.web.rest.vm.IndividualVM.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * O modo individual: o jogador sozinho, palavra após palavra, até onde conseguir
 * chegar.
 *
 * Regras:
 * - começa com 3 vidas; cada erro ou tempo esgotado tira uma; sem vidas, acabou;
 * - a próxima palavra é escolhida pela EquacaoPalavraIndividual, com o histórico
 *   do próprio jogador (tentativas, acertos, tempo e similaridade): ela fica mais
 *   difícil para ELE a cada acerto, e as que ele já errou voltam para revisão;
 * - o tempo da rodada segue a dificuldade da palavra (20/30/45 s, os mesmos
 *   padrões da criação de sala), validado aqui no servidor;
 * - cada acerto vale XP na hora (XpService.xpAcertoIndividual) - fechar a aba no
 *   meio não apaga o que foi conquistado.
 *
 * A partida em andamento vive na memória, uma por login, como as salas no
 * JogoSalaService; o resultado vai para partida_individual quando ela acaba e
 * cada resposta vai para o histórico (origem INDIVIDUAL). A palavra nunca sai em
 * texto enquanto a rodada está aberta: o áudio vem do servidor, como nas salas.
 */
@Service
public class PartidaIndividualService {

    private static final Logger LOG = LoggerFactory.getLogger(PartidaIndividualService.class);

    public static final int VIDAS = 3;

    // Mesma folga de rede e mesmo piso de digitação das salas (JogoSalaService)
    private static final long FOLGA_RESPOSTA_MS = 2000;
    private static final long MIN_MS_POR_LETRA = 80;

    // Partida sem nenhuma ação por este tempo é dada como abandonada
    private static final long INATIVIDADE_MAXIMA_MS = 15 * 60 * 1000L;

    /** Tempo da rodada por dificuldade - os padrões da tela de criação de sala. */
    static int tempoDaRodada(Dificuldade dificuldade) {
        return switch (dificuldade == null ? Dificuldade.MEDIO : dificuldade) {
            case FACIL -> 20;
            case DIFICIL -> 45;
            default -> 30;
        };
    }

    /** Pedido fora de ordem: responder duas vezes, pedir a próxima sem responder... */
    public static class RodadaInvalidaException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public RodadaInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    private final PalavraRepository palavraRepository;
    private final PartidaIndividualRepository partidaIndividualRepository;
    private final HistoricoRespostaService historicoRespostaService;
    private final PalavraAudioService palavraAudioService;
    private final PalavraEstatisticaService palavraEstatisticaService;
    private final ConquistaEngineService conquistaEngine;
    private final XpService xpService;

    private final Map<String, Corrida> corridas = new ConcurrentHashMap<>();
    private final Random aleatorio = new Random();

    public PartidaIndividualService(
        PalavraRepository palavraRepository,
        PartidaIndividualRepository partidaIndividualRepository,
        HistoricoRespostaService historicoRespostaService,
        PalavraAudioService palavraAudioService,
        PalavraEstatisticaService palavraEstatisticaService,
        ConquistaEngineService conquistaEngine,
        XpService xpService
    ) {
        this.palavraRepository = palavraRepository;
        this.partidaIndividualRepository = partidaIndividualRepository;
        this.historicoRespostaService = historicoRespostaService;
        this.palavraAudioService = palavraAudioService;
        this.palavraEstatisticaService = palavraEstatisticaService;
        this.conquistaEngine = conquistaEngine;
        this.xpService = xpService;
    }

    // ===== Ações do jogador =====

    /**
     * Começa uma partida nova. Uma partida que estava aberta é encerrada como
     * abandonada antes - o que ela já rendeu (XP, histórico) continua valendo.
     */
    public EstadoIndividualVM iniciar(String login) {
        Corrida anterior = corridas.get(login);
        if (anterior != null) {
            synchronized (anterior) {
                if (!anterior.encerrada) {
                    finalizar(anterior, PartidaIndividual.Encerramento.ABANDONOU);
                }
            }
        }
        List<Candidata> candidatas = palavraRepository
            .findAllByAtivaTrue()
            .stream()
            .map(p ->
                new Candidata(p.getId(), p.getDificuldade(), p.getTotalTentativas(), p.getTotalAcertos(), tempoDaRodada(p.getDificuldade()))
            )
            .toList();
        if (candidatas.isEmpty()) {
            throw new RodadaInvalidaException("O acervo não tem palavras ativas para o modo individual.");
        }
        int recorde = partidaIndividualRepository
            .findFirstByLoginOrderByAcertosDescDataFimAsc(login)
            .map(PartidaIndividual::getAcertos)
            .orElse(0);
        Corrida corrida = new Corrida(login, recorde, candidatas, new HashMap<>(historicoRespostaService.estatisticasPorPalavra(login)));
        synchronized (corrida) {
            corridas.put(login, corrida);
            sortearProxima(corrida);
            return estado(corrida);
        }
    }

    /** A partida em andamento (para a tela voltar ao lugar depois de recarregar). */
    public Optional<EstadoIndividualVM> estado(String login) {
        Corrida c = corridas.get(login);
        if (c == null) {
            return Optional.empty();
        }
        synchronized (c) {
            return c.encerrada ? Optional.empty() : Optional.of(estado(c));
        }
    }

    /** O áudio da palavra da rodada - som, nunca texto. */
    public Optional<byte[]> audio(String login) {
        Corrida c = corridas.get(login);
        if (c == null || c.encerrada || c.palavraAtual == null) {
            return Optional.empty();
        }
        return palavraAudioService.audio(c.palavraAtual.getId());
    }

    /**
     * Confere a resposta da rodada. Resposta vazia é "não respondeu" (o tempo
     * acabou ou o jogador pulou); resposta que chega depois do tempo também.
     */
    public ResultadoIndividualVM responder(String login, String resposta, int tentativasBurla) {
        Corrida c = corridaAberta(login);
        synchronized (c) {
            if (c.encerrada || c.palavraAtual == null) {
                throw new RodadaInvalidaException("Não há palavra em jogo nesta partida.");
            }
            if (c.respondida) {
                throw new RodadaInvalidaException("Esta palavra já foi respondida.");
            }
            Palavra palavra = c.palavraAtual;
            long agora = Instant.now().toEpochMilli();
            long decorrido = agora - c.inicioRodada;
            boolean foraDoTempo = decorrido > c.tempoLimite * 1000L + FOLGA_RESPOSTA_MS;
            ComparadorPalavras.Conferencia conferencia = foraDoTempo
                ? new ComparadorPalavras.Conferencia(false, ComparadorPalavras.NAO_RESPONDEU, 0)
                : ComparadorPalavras.conferir(resposta, palavra.getTexto());
            boolean correta = conferencia.correta();
            boolean naoRespondeu = ComparadorPalavras.NAO_RESPONDEU.equals(conferencia.tipoErro());

            c.respondida = true;
            c.ultimaAtividade = agora;

            // Lido ANTES de gravar: acerto depois de um erro na mesma palavra
            boolean tinhaErrado = correta && historicoRespostaService.tinhaErrado(login, palavra.getId());
            historicoRespostaService.registrar(
                login,
                palavra,
                correta,
                conferencia.tipoErro(),
                naoRespondeu ? null : (int) decorrido,
                conferencia.similaridade(),
                HistoricoResposta.Origem.INDIVIDUAL
            );
            palavraEstatisticaService.registrarTentativa(palavra.getId(), correta);
            c.aprender(palavra.getId(), correta, naoRespondeu ? null : decorrido, conferencia.similaridade());

            long xp = 0;
            if (correta) {
                c.acertos++;
                c.sequencia++;
                c.melhorSequencia = Math.max(c.melhorSequencia, c.sequencia);
                // Colar, corretor ou resposta rápida demais: o acerto conta, o XP não
                String digitado = ComparadorPalavras.canonico(resposta);
                boolean suspeita = tentativasBurla > 0 || decorrido < digitado.length() * MIN_MS_POR_LETRA;
                if (!suspeita) {
                    xp = xpService.creditarPorLogin(login, XpService.xpAcertoIndividual(c.dificuldadeAtual, c.acertos), "modo individual");
                    c.xpGanho += xp;
                }
            } else {
                c.vidas--;
                c.sequencia = 0;
                if (naoRespondeu) {
                    c.naoRespondidas++;
                } else {
                    c.erros++;
                }
            }
            try {
                conquistaEngine.aoResponderNaPartida(login, palavra, correta, decorrido, c.sequencia, tinhaErrado);
            } catch (Exception e) {
                LOG.error("Falha ao processar conquistas do modo individual de {}: {}", login, e.getMessage(), e);
            }
            c.respostas.add(
                new RespostaIndividualVM(
                    c.rodada,
                    palavra.getTexto(),
                    naoRespondeu ? null : (resposta == null ? null : resposta.trim()),
                    correta,
                    conferencia.tipoErro(),
                    conferencia.similaridade(),
                    xp
                )
            );

            ResumoIndividualVM resumo = c.vidas <= 0 ? finalizar(c, PartidaIndividual.Encerramento.SEM_VIDAS) : null;
            return new ResultadoIndividualVM(
                correta,
                foraDoTempo,
                palavra.getTexto(),
                conferencia.tipoErro(),
                conferencia.similaridade(),
                xp,
                estado(c),
                resumo
            );
        }
    }

    /**
     * Passa para a próxima palavra. Só depois de responder a atual: pular é mandar
     * a resposta vazia, que conta como não respondida. Com o acervo inteiro já
     * ditado, volta sem palavra - e a tela encerra a partida.
     */
    public EstadoIndividualVM proxima(String login) {
        Corrida c = corridaAberta(login);
        synchronized (c) {
            if (c.encerrada) {
                throw new RodadaInvalidaException("A partida já acabou.");
            }
            if (!c.respondida) {
                throw new RodadaInvalidaException("Responda a palavra atual antes de passar para a próxima.");
            }
            c.ultimaAtividade = Instant.now().toEpochMilli();
            sortearProxima(c);
            return estado(c);
        }
    }

    /** Para por conta própria. A palavra em aberto, se houver, não conta. */
    public ResumoIndividualVM encerrar(String login) {
        Corrida c = corridaAberta(login);
        synchronized (c) {
            if (c.encerrada) {
                throw new RodadaInvalidaException("A partida já acabou.");
            }
            return finalizar(c, PartidaIndividual.Encerramento.DESISTIU);
        }
    }

    /** Recorde, partida aberta e as últimas partidas - a tela de entrada do modo. */
    public RecordesVM recordes(String login) {
        Optional<PartidaIndividual> recorde = partidaIndividualRepository.findFirstByLoginOrderByAcertosDescDataFimAsc(login);
        List<PartidaAnteriorVM> ultimas = partidaIndividualRepository
            .findTop5ByLoginOrderByDataFimDesc(login)
            .stream()
            .map(p ->
                new PartidaAnteriorVM(
                    p.getDataFim(),
                    p.getPalavras(),
                    p.getAcertos(),
                    p.getMelhorSequencia(),
                    p.getXpGanho(),
                    p.getEncerramento().name()
                )
            )
            .toList();
        Corrida aberta = corridas.get(login);
        return new RecordesVM(
            recorde.map(PartidaIndividual::getAcertos).orElse(0),
            recorde.map(PartidaIndividual::getDataFim).orElse(null),
            aberta != null && !aberta.encerrada,
            ultimas
        );
    }

    /**
     * Encerra as partidas paradas há mais de 15 minutos: quem fechou a aba não
     * volta, e a partida dele não pode ficar na memória até o servidor reiniciar.
     */
    @Scheduled(fixedDelay = 60_000)
    public void encerrarAbandonadas() {
        long agora = Instant.now().toEpochMilli();
        corridas
            .values()
            .forEach(c -> {
                synchronized (c) {
                    if (!c.encerrada && agora - c.ultimaAtividade > INATIVIDADE_MAXIMA_MS) {
                        try {
                            finalizar(c, PartidaIndividual.Encerramento.ABANDONOU);
                        } catch (Exception e) {
                            LOG.error("Falha ao encerrar a partida individual abandonada de {}: {}", c.login, e.getMessage(), e);
                        }
                    }
                }
            });
    }

    /** Quantas partidas individuais estão abertas agora (monitoramento). */
    public int partidasEmAndamento() {
        return (int) corridas.values().stream().filter(c -> !c.encerrada).count();
    }

    // ===== Regras internas =====

    private Corrida corridaAberta(String login) {
        Corrida c = corridas.get(login);
        if (c == null || c.encerrada) {
            throw new RodadaInvalidaException("Você não tem uma partida individual em andamento.");
        }
        return c;
    }

    // Escolhe e prepara a próxima palavra pela equação. Palavra que sumiu do banco
    // no meio da partida é marcada como ditada e a equação escolhe outra.
    private void sortearProxima(Corrida c) {
        while (true) {
            Optional<Candidata> escolhida = EquacaoPalavraIndividual.escolher(c.candidatas, c.historico, c.ditadas, c.acertos, aleatorio);
            if (escolhida.isEmpty()) {
                // Acervo inteiro ditado: não há próxima, a tela encerra a partida
                c.palavraAtual = null;
                c.respondida = true;
                return;
            }
            Candidata candidata = escolhida.orElseThrow();
            c.ditadas.add(candidata.palavraId());
            Optional<Palavra> palavra = palavraRepository.findById(candidata.palavraId()).filter(p -> Boolean.TRUE.equals(p.getAtiva()));
            if (palavra.isEmpty()) {
                continue;
            }
            c.palavraAtual = palavra.orElseThrow();
            c.dificuldadeAtual = EquacaoPalavraIndividual.dificuldadeParaJogador(candidata, c.historico.get(candidata.palavraId()));
            c.tempoLimite = candidata.tempoLimiteSegundos();
            c.rodada++;
            c.respondida = false;
            // Áudio pronto ANTES de responder à tela: é o que permite omitir o texto
            palavraAudioService.prepararAudio(c.palavraAtual);
            c.inicioRodada = Instant.now().toEpochMilli();
            return;
        }
    }

    private ResumoIndividualVM finalizar(Corrida c, PartidaIndividual.Encerramento motivo) {
        c.encerrada = true;
        corridas.remove(c.login, c);
        int respondidas = c.respostas.size();
        if (respondidas > 0) {
            PartidaIndividual registro = new PartidaIndividual();
            registro.setLogin(c.login);
            registro.setDataInicio(c.inicio);
            registro.setDataFim(Instant.now());
            registro.setPalavras(respondidas);
            registro.setAcertos(c.acertos);
            registro.setMelhorSequencia(c.melhorSequencia);
            registro.setXpGanho((int) Math.min(Integer.MAX_VALUE, c.xpGanho));
            registro.setEncerramento(motivo);
            try {
                partidaIndividualRepository.save(registro);
            } catch (Exception e) {
                LOG.error("Falha ao gravar a partida individual de {}: {}", c.login, e.getMessage(), e);
            }
            // Partida perfeita no individual exige parar por conta própria, sem erro
            // nenhum e com ao menos 10 acertos - senão bastaria acertar uma e sair
            boolean perfeita =
                motivo == PartidaIndividual.Encerramento.DESISTIU && c.erros == 0 && c.naoRespondidas == 0 && c.acertos >= 10;
            try {
                conquistaEngine.aoConcluirPartida(c.login, false, false, perfeita, false);
            } catch (Exception e) {
                LOG.error("Falha ao premiar o fim da partida individual de {}: {}", c.login, e.getMessage(), e);
            }
        }
        return new ResumoIndividualVM(
            motivo.name(),
            respondidas,
            c.acertos,
            c.erros,
            c.naoRespondidas,
            c.melhorSequencia,
            c.xpGanho,
            c.recordeAnterior,
            c.acertos > c.recordeAnterior,
            List.copyOf(c.respostas)
        );
    }

    private EstadoIndividualVM estado(Corrida c) {
        PalavraIndividualVM palavra = null;
        if (c.palavraAtual != null) {
            Palavra p = c.palavraAtual;
            // Texto só depois de respondida, ou quando não há áudio no servidor (aí o
            // navegador precisa dele para falar - a mesma degradação das salas)
            boolean esconder = !c.respondida && palavraAudioService.temAudio(p.getId());
            palavra = new PalavraIndividualVM(
                p.getId(),
                esconder ? null : p.getTexto(),
                p.getDificuldade() != null ? p.getDificuldade().name() : null,
                c.tempoLimite,
                c.inicioRodada,
                Instant.now().toEpochMilli()
            );
        }
        return new EstadoIndividualVM(
            c.rodada,
            Math.max(0, c.vidas),
            VIDAS,
            c.acertos,
            c.sequencia,
            c.melhorSequencia,
            c.xpGanho,
            c.recordeAnterior,
            c.respondida,
            palavra
        );
    }

    /** Uma partida em andamento. Todo acesso é sincronizado nela mesma. */
    static final class Corrida {

        final String login;
        final Instant inicio = Instant.now();
        final int recordeAnterior;
        final List<Candidata> candidatas;
        final Map<Long, EstatisticaPalavraJogador> historico;
        final Set<Long> ditadas = new HashSet<>();
        final List<RespostaIndividualVM> respostas = new ArrayList<>();
        volatile long ultimaAtividade = Instant.now().toEpochMilli();
        volatile boolean encerrada = false;
        int vidas = VIDAS;
        int rodada = 0;
        int acertos;
        int erros;
        int naoRespondidas;
        int sequencia;
        int melhorSequencia;
        long xpGanho;
        Palavra palavraAtual;
        double dificuldadeAtual;
        long inicioRodada;
        int tempoLimite;
        boolean respondida = true;

        Corrida(String login, int recordeAnterior, List<Candidata> candidatas, Map<Long, EstatisticaPalavraJogador> historico) {
            this.login = login;
            this.recordeAnterior = recordeAnterior;
            this.candidatas = candidatas;
            this.historico = historico;
        }

        /**
         * Soma a resposta ao histórico usado pela equação, sem voltar ao banco: a
         * palavra que ele acabou de errar já pesa na escolha da próxima.
         */
        void aprender(Long palavraId, boolean correta, Long tempoMs, int similaridade) {
            EstatisticaPalavraJogador antes = historico.get(palavraId);
            long t = antes == null ? 0 : antes.tentativas();
            long a = antes == null ? 0 : antes.acertos();
            Double tempo = mediaComNovo(antes == null ? null : antes.tempoMedioMs(), t, tempoMs == null ? null : tempoMs.doubleValue());
            Double sim = mediaComNovo(antes == null ? null : antes.similaridadeMedia(), t, (double) similaridade);
            historico.put(palavraId, new EstatisticaPalavraJogador(t + 1, a + (correta ? 1 : 0), tempo, sim));
        }

        private static Double mediaComNovo(Double media, long n, Double novo) {
            if (novo == null) {
                return media;
            }
            if (media == null || n <= 0) {
                return novo;
            }
            return (media * n + novo) / (n + 1);
        }
    }
}
