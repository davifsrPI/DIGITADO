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
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Regras do jogo em memoria: quem aparece na sala, como a saida de uma sessao e
 * tratada, como os pontos sao distribuidos e o que acontece com uma resposta fora do
 * tempo. Teste de unidade puro - os colaboradores sao mocks, nada de banco.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JogoSalaServiceTest {

    private static final String SALA = "ABC123";
    private static final String NOME_SALA = "Turma 5A";
    private static final String PROFESSOR = "prof";

    @Mock
    private PalavraRepository palavraRepository;

    @Mock
    private PalavraEstatisticaService palavraEstatisticaService;

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
            conquistaEngine,
            estatisticaPartidaService,
            historicoRespostaService
        );
    }

    private Palavra palavra(long id, String texto) {
        Palavra p = new Palavra();
        p.setId(id);
        p.setTexto(texto);
        p.setDificuldadeCadastrada(Dificuldade.FACIL);
        p.setAtiva(true);
        return p;
    }

    // Sorteio sempre com as mesmas palavras, na ordem pedida
    private void comPalavras(Palavra... palavras) {
        when(palavraRepository.findRandomByDificuldadeExcluindo(anyString(), anyInt(), anyList())).thenReturn(List.of(palavras));
        when(palavraRepository.findRandomAtivasExcluindo(anyList(), anyInt())).thenReturn(List.of());
    }

    private EstadoJogoDTO iniciarPartida(int tempoLimite) {
        IniciarPayload payload = new IniciarPayload(tempoLimite, tempoLimite, tempoLimite, 1, 0, 0, List.of(), List.of());
        return service.iniciar(SALA, NOME_SALA, payload, PROFESSOR);
    }

    private PlacarEntry noPlacar(EstadoJogoDTO estado, String login) {
        return estado.placar().stream().filter(e -> e.login().equals(login)).findFirst().orElse(null);
    }

    @Nested
    @DisplayName("quem aparece na sala")
    class ParticipantesDaSala {

        @Test
        void professorNaoEntraNoPlacarNemNaListaDeAlunos() {
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            service.registrarAluno(SALA, "bruno", "Bruno", "s2");

            EstadoJogoDTO estado = service.getEstado(SALA, NOME_SALA);

            assertThat(estado.alunosConectados()).extracting("login").containsExactly("ana", "bruno");
            assertThat(estado.placar()).extracting("login").containsExactlyInAnyOrder("ana", "bruno");
        }

        @Test
        void professorQueJaTinhaEntradoComoAlunoSaiDoPlacar() {
            // sala que ficou com o registro antigo do professor como aluno
            service.registrarAluno(SALA, PROFESSOR, "Professora Ana", "s-antiga");
            assertThat(service.getEstado(SALA, NOME_SALA).placar()).extracting("login").contains(PROFESSOR);

            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");

            assertThat(service.getEstado(SALA, NOME_SALA).placar()).isEmpty();
        }

        @Test
        void professorNaoContaComoJogadorConectado() {
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");
            assertThat(service.conectadosNaSala(SALA)).isZero();

            service.registrarAluno(SALA, "ana", "Ana", "s1");
            assertThat(service.conectadosNaSala(SALA)).isEqualTo(1);
        }

        @Test
        void listaDeAlunosVemOrdenadaPeloNome() {
            service.registrarAluno(SALA, "z", "Bruno", "s1");
            service.registrarAluno(SALA, "a", "Ana", "s2");
            service.registrarAluno(SALA, "m", "carla", "s3");

            assertThat(service.getEstado(SALA, NOME_SALA).alunosConectados()).extracting("nome").containsExactly("Ana", "Bruno", "carla");
        }
    }

    @Nested
    @DisplayName("saida da sala por sessao")
    class SaidaDaSala {

        @Test
        void alunoQueReconectouContinuaNaSalaQuandoAsessaoAntigaCai() {
            service.registrarAluno(SALA, "ana", "Ana", "sessao-antiga");
            // recarregou a pagina: a sessao nova entra antes de a antiga ser encerrada
            service.registrarAluno(SALA, "ana", "Ana", "sessao-nova");

            JogoSalaService.ResultadoDesconexao r = service.aoDesconectar("ana", "sessao-antiga");

            assertThat(r.salasComSaida()).isEmpty();
            assertThat(r.salasVazias()).isEmpty();
            assertThat(service.getEstado(SALA, NOME_SALA).alunosConectados()).extracting("login").containsExactly("ana");
        }

        @Test
        void alunoSaiQuandoAultimaSessaoDeleCai() {
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            service.registrarAluno(SALA, "ana", "Ana", "s2");

            service.aoDesconectar("ana", "s1");
            JogoSalaService.ResultadoDesconexao r = service.aoDesconectar("ana", "s2");

            assertThat(r.salasComSaida()).containsExactly(SALA);
            assertThat(r.salasVazias()).containsExactly(SALA);
        }

        // O professor sozinho na tela de espera recarregando a página fechava a sala
        // que ele tinha acabado de abrir: a conexão dele caía, a sala ficava com zero
        // participantes e o listener marcava ativo=false no banco
        @Test
        void saidaDoProfessorSozinhoNaoFechaAsala() {
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");

            JogoSalaService.ResultadoDesconexao r = service.aoDesconectar(PROFESSOR, "s-prof");

            assertThat(r.salasComSaida()).containsExactly(SALA);
            assertThat(r.salasVazias()).isEmpty();
        }

        @Test
        void ultimoAlunoSaindoComProfessorForaFechaAsala() {
            service.registrarAluno(SALA, "ana", "Ana", "s1");

            JogoSalaService.ResultadoDesconexao r = service.aoDesconectar("ana", "s1");

            assertThat(r.salasVazias()).containsExactly(SALA);
        }

        @Test
        void salaNaoFicaVaziaEnquantoOprofessorEstaNela() {
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");
            service.registrarAluno(SALA, "ana", "Ana", "s1");

            JogoSalaService.ResultadoDesconexao r = service.aoDesconectar("ana", "s1");

            assertThat(r.salasComSaida()).containsExactly(SALA);
            assertThat(r.salasVazias()).isEmpty();
        }
    }

    @Nested
    @DisplayName("pontuacao da rodada")
    class Pontuacao {

        @BeforeEach
        void iniciar() {
            comPalavras(palavra(1L, "casa"));
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            service.registrarAluno(SALA, "bruno", "Bruno", "s2");
            service.registrarAluno(SALA, "carla", "Carla", "s3");
            iniciarPartida(30);
        }

        @Test
        void quemAcertaPrimeiroLevaAPontuacaoDePrimeiroMesmoDepoisDeErrosDosColegas() {
            service.responder(SALA, NOME_SALA, "ana", "Ana", "cassa", 0);
            service.responder(SALA, NOME_SALA, "bruno", "Bruno", "caza", 0);
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "carla", "Carla", "casa", 0);

            // base de 1o colocado (20) + bonus de velocidade, nunca a base de 3o (12)
            assertThat(r.feedback().correta()).isTrue();
            assertThat(r.feedback().ordem()).isEqualTo(1);
            assertThat(r.feedback().pontos()).isGreaterThanOrEqualTo(20);
            assertThat(r.feedback().registrada()).isTrue();
        }

        @Test
        void segundoAacertarLevaAbaseDeSegundo() {
            service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "bruno", "Bruno", "casa", 0);

            assertThat(r.feedback().ordem()).isEqualTo(2);
            assertThat(r.feedback().pontos()).isBetween(15, 22);
        }

        @Test
        void quemErraNaoGanhaPontoEnemConsomeAposicaoDeAcerto() {
            JogoSalaService.ResultadoResposta erro = service.responder(SALA, NOME_SALA, "ana", "Ana", "kasa", 0);

            assertThat(erro.feedback().correta()).isFalse();
            assertThat(erro.feedback().pontos()).isZero();
            assertThat(erro.feedback().ordem()).isZero();
            assertThat(noPlacar(erro.estado(), "ana").pontos()).isZero();
        }

        @Test
        void acertoSempreSomaPontoNoPlacar() {
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);

            assertThat(noPlacar(r.estado(), "ana").pontos()).isEqualTo(r.feedback().pontos()).isPositive();
            assertThat(noPlacar(r.estado(), "ana").statusAtual()).isEqualTo("ACERTOU");
        }

        @Test
        void placarUsaOnomePublicoDaEntradaNaSala() {
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "ana", "casa", 0);

            assertThat(noPlacar(r.estado(), "ana").nome()).isEqualTo("Ana");
        }

        @Test
        void placarDesempataPeloNomeEmVezDeOrdemArbitraria() {
            // ninguem pontuou: a ordem tem de ser estavel e alfabetica
            EstadoJogoDTO estado = service.getEstado(SALA, NOME_SALA);
            assertThat(estado.placar()).extracting("nome").containsExactly("Ana", "Bruno", "Carla");
        }

        @Test
        void segundaRespostaDoMesmoAlunoNaoEhProcessada() {
            service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);
            assertThat(service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0)).isNull();
        }
    }

    @Nested
    @DisplayName("resposta recusada")
    class RespostaRecusada {

        @Test
        void respostaForaDoTempoVoltaComoNaoRegistrada() throws InterruptedException {
            comPalavras(palavra(1L, "casa"));
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            // rodada de 0s: qualquer resposta ja chega depois do tempo + folga de 2s
            iniciarPartida(0);
            Thread.sleep(2100);

            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);

            assertThat(r).isNotNull();
            assertThat(r.feedback().registrada()).isFalse();
            assertThat(r.feedback().pontos()).isZero();
            // nada mudou no placar, entao nao ha estado para retransmitir a sala
            assertThat(r.estado()).isNull();
            assertThat(noPlacar(service.getEstado(SALA, NOME_SALA), "ana").pontos()).isZero();
        }

        @Test
        void respostaComAPartidaPausadaVoltaComoNaoRegistrada() {
            comPalavras(palavra(1L, "casa"));
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            iniciarPartida(30);
            service.pausar(SALA, NOME_SALA);

            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);

            assertThat(r.feedback().registrada()).isFalse();
            assertThat(r.feedback().tipoErro()).isEqualTo("RODADA_ENCERRADA");
        }
    }

    @Nested
    @DisplayName("comparacao da palavra")
    class ComparacaoDaPalavra {

        // Mesma palavra na forma DECOMPOSTA (cada acento vira um caractere combinante
        // depois da letra), como costuma chegar uma palavra cadastrada por copia de
        // documento - visualmente identica a digitada pelo aluno
        private static final String ACAO_DECOMPOSTA = Normalizer.normalize("ação", Normalizer.Form.NFD);

        @Test
        void palavraCadastradaDecompostaAceitaARespostaDigitadaNormalmente() {
            comPalavras(palavra(1L, ACAO_DECOMPOSTA));
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            iniciarPartida(30);

            // o teclado do aluno produz a forma composta: um caractere por letra acentuada
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "ação", 0);

            assertThat(ACAO_DECOMPOSTA).isNotEqualTo("ação"); // guarda: as duas formas sao mesmo diferentes
            assertThat(r.feedback().correta()).isTrue();
            assertThat(r.feedback().pontos()).isPositive();
        }

        @Test
        void acentoErradoContinuaSendoErro() {
            comPalavras(palavra(1L, "café"));
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            iniciarPartida(30);

            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "cafe", 0);

            assertThat(r.feedback().correta()).isFalse();
            assertThat(r.feedback().tipoErro()).isEqualTo("ACENTUACAO");
        }
    }
}
