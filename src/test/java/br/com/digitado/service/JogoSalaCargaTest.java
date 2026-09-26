package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.web.websocket.dto.EstadoJogoDTO;
import br.com.digitado.web.websocket.dto.IniciarPayload;
import br.com.digitado.web.websocket.dto.PlacarEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sala CHEIA: uma turma de 40 alunos respondendo ao mesmo tempo.
 *
 * O estado do jogo vive em memória e é compartilhado por todas as conexões
 * WebSocket da sala, então uma turma inteira apertando "enviar" no mesmo segundo
 * bate simultaneamente no mesmo EstadoJogo. Estes testes cobrem as duas perguntas
 * que uma turma grande levanta:
 *
 * - CORREÇÃO: ninguém some da sala, ninguém perde ponto e a ordem de acerto não
 *   se repete quando 40 respostas chegam juntas;
 * - CUSTO: quanto tempo leva montar o estado que vai para todo mundo a cada
 *   resposta - é ele que o servidor transmite 40 vezes por rodada.
 *
 * Os tempos são registrados no log e conferidos contra limites FOLGADOS. A
 * intenção não é cravar um número de máquina, é pegar regressão de ordem de
 * grandeza: se montar o placar de 40 alunos passar a custar centenas de
 * milissegundos, a partida engasga na sala de aula e o teste avisa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JogoSalaCargaTest {

    private static final Logger LOG = LoggerFactory.getLogger(JogoSalaCargaTest.class);

    private static final String SALA = "TURMA40";
    private static final String NOME_SALA = "Turma cheia";
    private static final String PROFESSOR = "prof";
    private static final int ALUNOS = 40;

    // Limites folgados: em máquina de desenvolvimento os tempos reais ficam uma
    // ordem de grandeza abaixo. Servem contra regressão, não como meta de performance.
    private static final long LIMITE_MONTAR_ESTADO_MS = 50;
    private static final long LIMITE_RODADA_INTEIRA_MS = 4000;

    @Mock
    private PalavraRepository palavraRepository;

    @Mock
    private PalavraEstatisticaService palavraEstatisticaService;

    // Audio da palavra: mock devolve "sem audio pronto", entao o texto continua sendo
    // transmitido no estado - e o que estes testes conferem
    @Mock
    private PalavraAudioService palavraAudioService;

    @Mock
    private ConquistaEngineService conquistaEngine;

    @Mock
    private EstatisticaPartidaService estatisticaPartidaService;

    @Mock
    private HistoricoRespostaService historicoRespostaService;

    private JogoSalaService service;

    @BeforeEach
    void setUp() {
        service = new JogoSalaService(
            palavraRepository,
            palavraEstatisticaService,
            palavraAudioService,
            conquistaEngine,
            estatisticaPartidaService,
            historicoRespostaService
        );
        List<Palavra> palavras = List.of(palavra(1L, "casa"), palavra(2L, "bola"), palavra(3L, "gato"));
        when(palavraRepository.findRandomByDificuldadeExcluindo(anyString(), anyInt(), anyList())).thenReturn(palavras);
        when(palavraRepository.findRandomAtivasExcluindo(anyList(), anyInt())).thenReturn(List.of());
    }

    private Palavra palavra(long id, String texto) {
        Palavra p = new Palavra();
        p.setId(id);
        p.setTexto(texto);
        p.setDificuldadeCadastrada(Dificuldade.FACIL);
        p.setAtiva(true);
        return p;
    }

    private String login(int i) {
        return String.format("aluno%02d", i);
    }

    private String nome(int i) {
        return String.format("Aluno %02d", i);
    }

    // Professor + 40 alunos, cada um com a própria sessão WebSocket
    private void turmaInteiraEntra() {
        service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "sessao-prof");
        for (int i = 1; i <= ALUNOS; i++) {
            service.registrarAluno(SALA, login(i), nome(i), "sessao-" + i);
        }
    }

    // A palavra da rodada NÃO é previsível: iniciar() embaralha a lista sorteada.
    // Quem responde certo precisa ler o que está valendo agora, igual ao aluno.
    private String palavraDaRodada() {
        return service.getEstado(SALA, NOME_SALA).palavraAtual().texto();
    }

    private void iniciarPartida() {
        service.iniciar(SALA, NOME_SALA, new IniciarPayload(30, 30, 30, 3, 0, 0, List.of(), List.of()), PROFESSOR);
    }

    /**
     * Roda uma ação para cada aluno ao MESMO tempo: todas as threads ficam
     * esperando na largada e são soltas juntas, que é o mais próximo de 40
     * celulares enviando a resposta no mesmo instante.
     */
    private <T> List<T> todosAoMesmoTempo(java.util.function.IntFunction<T> acao) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(ALUNOS, 16));
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(ALUNOS);
        ConcurrentLinkedQueue<T> resultados = new ConcurrentLinkedQueue<>();
        try {
            for (int i = 1; i <= ALUNOS; i++) {
                final int aluno = i;
                pool.submit(() -> {
                    try {
                        largada.await();
                        T r = acao.apply(aluno);
                        if (r != null) resultados.add(r);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        chegada.countDown();
                    }
                });
            }
            largada.countDown();
            assertThat(chegada.await(30, TimeUnit.SECONDS)).as("todas as respostas terminaram").isTrue();
        } finally {
            pool.shutdownNow();
        }
        return new ArrayList<>(resultados);
    }

    @Test
    @DisplayName("os 40 alunos aparecem na sala, e o professor não ocupa vaga")
    void turmaDe40EntraInteira() {
        turmaInteiraEntra();

        EstadoJogoDTO estado = service.getEstado(SALA, NOME_SALA);

        assertThat(estado.alunosConectados()).hasSize(ALUNOS);
        assertThat(estado.placar()).hasSize(ALUNOS);
        assertThat(estado.alunosConectados()).extracting("login").doesNotContain(PROFESSOR);
        // conectadosNaSala é a contagem de JOGADORES: o professor fica de fora
        assertThat(service.conectadosNaSala(SALA)).isEqualTo(ALUNOS);
    }

    @Test
    @DisplayName("40 respostas simultâneas: ninguém perde ponto e a ordem de acerto não se repete")
    void quarentaRespondendoAoMesmoTempo() throws InterruptedException {
        turmaInteiraEntra();
        iniciarPartida();

        // Alunos pares acertam, ímpares erram - assim a ordem de ACERTO tem de
        // ignorar os erros, que é a regra da pontuação
        final String certa = palavraDaRodada();
        final String errada = "z" + certa;
        long inicio = System.nanoTime();
        List<JogoSalaService.ResultadoResposta> respostas = todosAoMesmoTempo(i ->
            service.responder(SALA, NOME_SALA, login(i), nome(i), i % 2 == 0 ? certa : errada, 0)
        );
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(respostas).as("toda resposta recebeu retorno").hasSize(ALUNOS);

        List<Integer> ordensDeAcerto = respostas
            .stream()
            .filter(r -> r.feedback().correta())
            .map(r -> r.feedback().ordem())
            .sorted()
            .toList();
        List<Integer> esperado = IntStream.rangeClosed(1, ALUNOS / 2).boxed().toList();
        assertThat(ordensDeAcerto).as("ordem de acerto sem repetição nem buraco").isEqualTo(esperado);

        EstadoJogoDTO estado = service.getEstado(SALA, NOME_SALA);
        assertThat(estado.placar()).hasSize(ALUNOS);
        long comPonto = estado.placar().stream().filter(p -> p.pontos() > 0).count();
        assertThat(comPonto).as("todo acerto virou ponto").isEqualTo(ALUNOS / 2L);
        long semPonto = estado.placar().stream().filter(p -> p.pontos() == 0).count();
        assertThat(semPonto).as("nenhum erro pontuou").isEqualTo(ALUNOS / 2L);

        LOG.info("BENCHMARK · 40 respostas simultâneas processadas em {} ms", duracaoMs);
        assertThat(duracaoMs).as("rodada inteira de 40 alunos").isLessThan(LIMITE_RODADA_INTEIRA_MS);
    }

    @Test
    @DisplayName("placar de 40 sai ordenado, com a 20ª posição no lugar certo")
    void rankingDe40SaiOrdenado() {
        turmaInteiraEntra();
        iniciarPartida();
        // Todos acertam, em sequência: a ordem de chegada define quanto cada um leva
        String certa = palavraDaRodada();
        for (int i = 1; i <= ALUNOS; i++) {
            service.responder(SALA, NOME_SALA, login(i), nome(i), certa, 0);
        }

        List<PlacarEntry> placar = service.getEstado(SALA, NOME_SALA).placar();

        assertThat(placar).hasSize(ALUNOS);
        for (int i = 1; i < placar.size(); i++) {
            assertThat(placar.get(i).pontos())
                .as("posição %d não pode ter mais pontos que a %d", i + 1, i)
                .isLessThanOrEqualTo(placar.get(i - 1).pontos());
        }
        // A 20ª posição existe e é uma entrada real da turma, não um buraco
        PlacarEntry vigesimo = placar.get(19);
        assertThat(vigesimo.login()).startsWith("aluno");
        assertThat(vigesimo.nome()).isNotBlank();
        // Empate entre os que chegaram depois do 4º colocado (todos na mesma base):
        // a ordem tem de ser estável e alfabética, nunca aleatória
        List<PlacarEntry> repetido = service.getEstado(SALA, NOME_SALA).placar();
        assertThat(repetido).extracting(PlacarEntry::login).isEqualTo(placar.stream().map(PlacarEntry::login).toList());
    }

    @Test
    @DisplayName("reconexões no meio da partida não tiram ninguém da sala")
    void reconexoesDuranteAPartidaNaoDerrubamNinguem() throws InterruptedException {
        turmaInteiraEntra();
        iniciarPartida();

        // Metade da turma recarrega a página ao mesmo tempo (queda de wi-fi da escola):
        // a sessão nova entra e, logo depois, chega a desconexão da antiga
        todosAoMesmoTempo(i -> {
            if (i % 2 == 0) {
                service.registrarAluno(SALA, login(i), nome(i), "sessao-nova-" + i);
                service.aoDesconectar(login(i), "sessao-" + i);
            }
            return Boolean.TRUE;
        });

        EstadoJogoDTO estado = service.getEstado(SALA, NOME_SALA);
        assertThat(estado.alunosConectados()).as("ninguém sumiu da lista depois de reconectar").hasSize(ALUNOS);
        assertThat(service.conectadosNaSala(SALA)).isEqualTo(ALUNOS);
    }

    @Test
    @DisplayName("BENCHMARK: montar o estado que vai para os 40 a cada resposta")
    void custoDeMontarOEstadoComTurmaCheia() {
        turmaInteiraEntra();
        iniciarPartida();
        String certa = palavraDaRodada();
        for (int i = 1; i <= ALUNOS; i++) {
            service.responder(SALA, NOME_SALA, login(i), nome(i), i % 3 == 0 ? certa : "erro" + i, 0);
        }

        // aquece (primeira passada paga carregamento de classe e JIT)
        for (int i = 0; i < 50; i++) {
            service.getEstado(SALA, NOME_SALA);
        }

        int medicoes = 500;
        List<Long> temposNs = new ArrayList<>(medicoes);
        for (int i = 0; i < medicoes; i++) {
            long t0 = System.nanoTime();
            EstadoJogoDTO estado = service.getEstado(SALA, NOME_SALA);
            temposNs.add(System.nanoTime() - t0);
            assertThat(estado.placar()).hasSize(ALUNOS);
        }
        Collections.sort(temposNs);
        double medianaMs = temposNs.get(medicoes / 2) / 1_000_000.0;
        double p99Ms = temposNs.get((int) (medicoes * 0.99)) / 1_000_000.0;
        double piorMs = temposNs.get(medicoes - 1) / 1_000_000.0;

        LOG.info(
            "BENCHMARK · montar o estado com {} alunos no placar: mediana {} ms · p99 {} ms · pior {} ms",
            ALUNOS,
            String.format("%.3f", medianaMs),
            String.format("%.3f", p99Ms),
            String.format("%.3f", piorMs)
        );
        assertThat(p99Ms).as("montar o estado da sala cheia").isLessThan((double) LIMITE_MONTAR_ESTADO_MS);
    }

    /**
     * Tráfego de uma rodada com a turma cheia.
     *
     * Antes, cada resposta mandava o estado INTEIRO da sala para todo mundo: 40
     * respostas x 40 aparelhos = 1600 mensagens de 5 KB por palavra, e cada uma
     * redesenhava a tela de todos os alunos. Agora, durante a rodada, sai só um aviso
     * de algumas dezenas de bytes e só para quem comanda a sala; o placar completo vai
     * uma vez, quando a rodada fecha.
     *
     * O teste mede os dois e trava a diferença: se alguém voltar a transmitir o placar
     * a cada resposta, a conta explode e o teste avisa.
     */
    @Test
    @DisplayName("BENCHMARK: tráfego de uma rodada com turma cheia")
    void trafegoDaRodadaComTurmaCheia() throws Exception {
        turmaInteiraEntra();
        iniciarPartida();
        String certa = palavraDaRodada();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        long bytesNovos = 0;
        int mensagensNovas = 0;
        for (int i = 1; i <= ALUNOS; i++) {
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, login(i), nome(i), i % 2 == 0 ? certa : "erro" + i, 0);
            if (r.evento() != null && r.estado() == null) {
                // aviso leve: um destinatário só, o professor
                bytesNovos += mapper.writeValueAsBytes(r.evento()).length;
                mensagensNovas += 1;
            }
            if (r.estado() != null) {
                // rodada fechou: o placar completo vai para a sala inteira
                bytesNovos += (long) mapper.writeValueAsBytes(r.estado()).length * ALUNOS;
                mensagensNovas += ALUNOS;
            }
        }

        int bytesDoEstado = mapper.writeValueAsBytes(service.getEstado(SALA, NOME_SALA)).length;
        long mensagensAntigas = (long) ALUNOS * ALUNOS;
        long bytesAntigos = (long) bytesDoEstado * mensagensAntigas;

        LOG.info(
            "BENCHMARK · tráfego de UMA rodada com {} alunos — antes: {} mensagens / {} KB · agora: {} mensagens / {} KB ({}x menos)",
            ALUNOS,
            mensagensAntigas,
            bytesAntigos / 1024,
            mensagensNovas,
            bytesNovos / 1024,
            bytesAntigos / Math.max(bytesNovos, 1)
        );

        assertThat(bytesNovos).as("tráfego da rodada tem de ser uma fração do antigo").isLessThan(bytesAntigos / 10);
        // O aviso leve tem de continuar leve: se alguém pendurar o placar nele, some a economia
        assertThat(mapper.writeValueAsBytes(new br.com.digitado.web.websocket.dto.RespostaRodada(0, "aluno01", "ACERTOU", 1, 1)).length)
            .as("tamanho do aviso de resposta")
            .isLessThan(200);
    }

    @Test
    @DisplayName("BENCHMARK: partida inteira de 3 palavras com a turma cheia")
    void custoDaPartidaInteira() throws InterruptedException {
        turmaInteiraEntra();

        long t0 = System.nanoTime();
        iniciarPartida();
        for (int rodada = 0; rodada < 3; rodada++) {
            final String certa = palavraDaRodada();
            todosAoMesmoTempo(i -> service.responder(SALA, NOME_SALA, login(i), nome(i), i % 2 == 0 ? certa : "z" + certa, 0));
            service.proximaPalavra(SALA, NOME_SALA, PROFESSOR);
        }
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        LOG.info("BENCHMARK · partida de 3 palavras com {} alunos: {} ms", ALUNOS, totalMs);
        assertThat(totalMs).as("partida inteira com turma cheia").isLessThan(LIMITE_RODADA_INTEIRA_MS * 3);

        // Encerrada: o snapshot gravado tem a turma inteira, sem o professor
        EstadoJogoDTO fim = service.getEstado(SALA, NOME_SALA);
        assertThat(fim.placar()).hasSize(ALUNOS);
        assertThat(fim.placar()).extracting(PlacarEntry::login).doesNotContain(PROFESSOR);
    }
}
