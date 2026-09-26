package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.web.websocket.dto.EstadoJogoDTO;
import br.com.digitado.web.websocket.dto.FeedbackAluno;
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
            assertThat(noPlacar(service.getEstado(SALA, NOME_SALA), "ana").pontos()).isZero();
        }

        @Test
        void acertoSempreSomaPontoNoPlacar() {
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);

            EstadoJogoDTO estado = service.getEstado(SALA, NOME_SALA);
            assertThat(noPlacar(estado, "ana").pontos()).isEqualTo(r.feedback().pontos()).isPositive();
            assertThat(noPlacar(estado, "ana").statusAtual()).isEqualTo("ACERTOU");
        }

        @Test
        void placarUsaOnomePublicoDaEntradaNaSala() {
            service.responder(SALA, NOME_SALA, "ana", "ana", "casa", 0);

            assertThat(noPlacar(service.getEstado(SALA, NOME_SALA), "ana").nome()).isEqualTo("Ana");
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

        // A cedilha e uma letra como as outras na hora de acertar ou errar: quem
        // escreve "caca" no lugar de "caça" NAO acertou, ainda que o som bata
        @Test
        void cedilhaContaParaOAcerto() {
            comPalavras(palavra(1L, "caça"));
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            service.registrarAluno(SALA, "bia", "Bia", "s2");
            iniciarPartida(30);

            assertThat(service.responder(SALA, NOME_SALA, "ana", "Ana", "caça", 0).feedback().correta()).isTrue();
            assertThat(service.responder(SALA, NOME_SALA, "bia", "Bia", "caca", 0).feedback().correta()).isFalse();
        }

        /**
         * Responde UMA palavra numa partida recem-iniciada e devolve o feedback.
         *
         * Uma palavra por partida de proposito: iniciar() embaralha a lista, e o mock
         * do sorteio devolve o mesmo conteudo em cada faixa de dificuldade - com duas
         * palavras nao se sabe qual e a da rodada.
         */
        private FeedbackAluno feedbackDe(String palavraCorreta, String respostaDigitada) {
            comPalavras(palavra(1L, palavraCorreta));
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            iniciarPartida(30);
            return service.responder(SALA, NOME_SALA, "ana", "Ana", respostaDigitada, 0).feedback();
        }

        @Test
        void todasAsLetrasDoAlfabetoSaoAceitas() {
            // k, w e y entraram no alfabeto oficial em 2009; a cedilha e as vogais
            // acentuadas completam o conjunto que um ditado em portugues precisa
            assertThat(feedbackDe("kiwi", "kiwi").correta()).isTrue();
            assertThat(feedbackDe("coração", "coração").correta()).isTrue();
            assertThat(feedbackDe("yakisoba", "yakisoba").correta()).isTrue();
            assertThat(feedbackDe("show", "show").correta()).isTrue();
            assertThat(feedbackDe("pêssego", "pêssego").correta()).isTrue();
            assertThat(feedbackDe("guarda-chuva", "guarda-chuva").correta()).isTrue();
        }

        // Som certo, grafia errada: e o erro mais util de se registrar num app de
        // ortografia, e antes ele era gravado como OUTRO no historico
        @Test
        void grafiaDiferenteComMesmoSomEhErroFonetico() {
            assertThat(feedbackDe("chave", "xave").tipoErro()).isEqualTo("ERRO_FONETICO");

            // "cassa" por "caça" so e reconhecido porque a tabela fonetica roda ANTES
            // de tirar os acentos: a cedilha e um diacritico como outro qualquer, e o
            // NFD a removia - "caça" virava "caca" e a regra c-cedilha -> s morria
            assertThat(feedbackDe("caça", "cassa").tipoErro()).isEqualTo("ERRO_FONETICO");
        }

        @Test
        void erroQueNaoEDeSomContinuaClassificadoPelasLetras() {
            assertThat(feedbackDe("casa", "cas").tipoErro()).isEqualTo("LETRA_FALTANDO");
            assertThat(feedbackDe("casa", "casaa").tipoErro()).isEqualTo("LETRA_EXTRA");
            assertThat(feedbackDe("casa", "cada").tipoErro()).isEqualTo("TROCA_LETRA");
            assertThat(feedbackDe("paralelepipedo", "prlpp").tipoErro()).isEqualTo("OUTRO");
        }
    }

    @Nested
    @DisplayName("o texto da palavra nao vaza para o aluno")
    class TextoDaPalavra {

        @BeforeEach
        void iniciar() {
            comPalavras(palavra(1L, "casa"));
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");
            service.registrarAluno(SALA, "ana", "Ana", "s1");
        }

        /**
         * O furo que isto fecha: o estado do jogo vai para o topico da sala, que TODO
         * aluno assina, e o texto da palavra ia junto porque o aparelho dele precisava
         * dele para a sintese de voz do navegador. A resposta chegava ao aluno antes de
         * ele responder - bastava abrir o console.
         */
        @Test
        @DisplayName("com audio pronto no servidor, a rodada aberta nao transmite o texto")
        void rodadaAbertaNaoTransmiteOTexto() {
            when(palavraAudioService.temAudio(1L)).thenReturn(true);

            EstadoJogoDTO estado = iniciarPartida(30);

            assertThat(estado.palavraAtual()).isNotNull();
            assertThat(estado.palavraAtual().texto()).as("o texto da palavra nao pode ir no ar durante a rodada").isNull();
            // O resto continua: o aluno precisa saber a dificuldade e o id para pedir o audio
            assertThat(estado.palavraAtual().id()).isEqualTo(1L);
            assertThat(estado.palavraAtual().dificuldade()).isNotNull();
        }

        @Test
        @DisplayName("rodada fechada revela o texto, que e o que a tela de correcao mostra")
        void rodadaFechadaRevelaOTexto() {
            when(palavraAudioService.temAudio(1L)).thenReturn(true);
            iniciarPartida(30);

            // Unico jogador respondeu: a rodada fecha na hora
            service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);
            EstadoJogoDTO fechada = service.getEstado(SALA, NOME_SALA);

            assertThat(fechada.palavraAtual().texto()).isEqualTo("casa");
        }

        /**
         * Degradacao consciente: sem sintetizador no servidor o aluno precisa do texto
         * para o navegador dele falar. Ficar sem audio nenhum pararia a aula, o que e
         * pior que o risco de alguem espiar o console.
         */
        @Test
        @DisplayName("sem audio pronto, o texto continua sendo transmitido")
        void semAudioOTextoContinuaIndo() {
            when(palavraAudioService.temAudio(1L)).thenReturn(false);

            EstadoJogoDTO estado = iniciarPartida(30);

            assertThat(estado.palavraAtual().texto()).isEqualTo("casa");
        }

        @Test
        @DisplayName("o audio da palavra e preparado quando ela entra em jogo")
        void audioEPreparadoNaEntradaDaPalavra() {
            iniciarPartida(30);

            // Uma vez por rodada, nao a cada mensagem: sintetizar e processo externo
            verify(palavraAudioService).prepararAudio(argThat(pal -> pal != null && "casa".equals(pal.getTexto())));
        }

        @Test
        @DisplayName("so quem esta na sala alcanca o audio")
        void somenteParticipanteAlcancaOAudio() {
            iniciarPartida(30);

            assertThat(service.estaNaSala(SALA, "ana")).isTrue();
            assertThat(service.estaNaSala(SALA, "estranho")).isFalse();
            assertThat(service.estaNaSala("OUTRA1", "ana")).isFalse();
        }

        @Test
        @DisplayName("a palavra da rodada fica disponivel para os endpoints do servidor")
        void palavraDaRodadaDisponivelNoServidor() {
            iniciarPartida(30);

            assertThat(service.palavraAtualDaSala(SALA)).isPresent().get().extracting("texto").isEqualTo("casa");
            assertThat(service.palavraAtualDaSala("NAOEXISTE")).isEmpty();
        }
    }

    @Nested
    @DisplayName("o relogio da partida vive no servidor")
    class RelogioDoServidor {

        @BeforeEach
        void iniciar() {
            comPalavras(palavra(1L, "casa"));
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");
            service.registrarAluno(SALA, "ana", "Ana", "s1");
        }

        @Test
        @DisplayName("rodada recem-aberta nao e virada pelo relogio")
        void rodadaRecemAbertaNaoEVirada() {
            iniciarPartida(30);

            assertThat(service.avancarRodadasVencidas()).isEmpty();
            assertThat(service.getEstado(SALA, NOME_SALA).indiceAtual()).isZero();
        }

        @Test
        @DisplayName("sala parada, sem partida, nunca vence")
        void salaSemPartidaNuncaVence() {
            assertThat(service.avancarRodadasVencidas()).isEmpty();
        }

        /**
         * O teste que justifica a mudanca: NINGUEM pede a proxima palavra aqui - nem
         * professor, nem aluno, nem duelista. Antes o tempo era contado no navegador
         * de quem comandava a sala, e a aba dele fechando deixava a turma parada no
         * ranking para sempre.
         *
         * Espera real de alguns segundos porque o marco e o relogio: rodada de tempo
         * zero, mais o tempo de ranking, mais a folga do avanco automatico.
         */
        @Test
        @DisplayName("o servidor vira a rodada sozinho, sem nenhum cliente pedir")
        void servidorViraARodadaSozinho() throws InterruptedException {
            comPalavras(palavra(1L, "casa"));
            // tempo de rodada 0: o que sobra para esperar e so o tempo de ranking
            iniciarPartida(0);
            long limite = System.currentTimeMillis() + (JogoSalaService.TEMPO_RANKING_SEGUNDOS + 6) * 1000L;

            List<JogoSalaService.RodadaAvancada> viradas = List.of();
            while (viradas.isEmpty() && System.currentTimeMillis() < limite) {
                viradas = service.avancarRodadasVencidas();
                if (viradas.isEmpty()) {
                    Thread.sleep(250);
                }
            }

            assertThat(viradas).as("o relogio do servidor precisa virar a rodada sozinho").hasSize(1);
            assertThat(viradas.get(0).codigoSala()).isEqualTo(SALA);
            // A rodada andou: saiu da palavra 0 sem ninguem pedir. Pode ter ido para a
            // proxima ou encerrado a partida, conforme quantas palavras foram sorteadas
            EstadoJogoDTO depois = viradas.get(0).estado();
            assertThat(depois.indiceAtual()).as("a rodada tem de ter saido da palavra 0").isPositive();
            assertThat(depois.tipo()).isIn("NOVA_PALAVRA", "ENCERRADA");
        }

        @Test
        @DisplayName("botao de quem comanda e relogio do servidor nao viram a mesma rodada duas vezes")
        void rodadaNaoViraDuasVezes() {
            comPalavras(palavra(1L, "casa"), palavra(2L, "bola"));
            iniciarPartida(30);
            int rodadaAberta = service.getEstado(SALA, NOME_SALA).indiceAtual();

            // Os dois caminhos enxergaram a MESMA rodada: so o primeiro vira
            EstadoJogoDTO primeiro = service.proximaPalavra(SALA, NOME_SALA, PROFESSOR, rodadaAberta);
            EstadoJogoDTO segundo = service.proximaPalavra(SALA, NOME_SALA, PROFESSOR, rodadaAberta);

            assertThat(primeiro).isNotNull();
            assertThat(segundo).as("o segundo pedido da mesma rodada nao pode virar nada").isNull();
        }

        @Test
        @DisplayName("pedido atrasado, de uma rodada que ja passou, nao pula palavra")
        void pedidoAtrasadoNaoPulaPalavra() {
            comPalavras(palavra(1L, "casa"), palavra(2L, "bola"));
            iniciarPartida(30);

            service.proximaPalavra(SALA, NOME_SALA, PROFESSOR, 0);
            int depois = service.getEstado(SALA, NOME_SALA).indiceAtual();
            // Chega o clique que o professor deu na rodada 0, ja virada
            EstadoJogoDTO atrasado = service.proximaPalavra(SALA, NOME_SALA, PROFESSOR, 0);

            assertThat(atrasado).isNull();
            assertThat(service.getEstado(SALA, NOME_SALA).indiceAtual()).isEqualTo(depois);
        }

        @Test
        @DisplayName("rodada em que todos responderam fecha na hora, sem esperar o tempo")
        void fechamentoAntecipadoQuandoTodosRespondem() {
            EstadoJogoDTO aberta = iniciarPartida(30);
            long fimDoTempo = aberta.timestampInicio() + 30_000L;

            service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);
            EstadoJogoDTO fechada = service.getEstado(SALA, NOME_SALA);

            // O ranking passa a contar de AGORA, nao do fim do tempo: a turma que
            // termina em 10s uma rodada de 45 nao fica olhando os 35 restantes
            assertThat(fechada.timestampFechamento()).isLessThan(fimDoTempo);
            assertThat(fechada.timestampFechamento()).isCloseTo(System.currentTimeMillis(), within(5_000L));
        }

        @Test
        @DisplayName("o estado leva o tempo de ranking, para as telas nao o inventarem")
        void estadoLevaOTempoDeRanking() {
            EstadoJogoDTO estado = iniciarPartida(30);

            assertThat(estado.tempoRanking()).isEqualTo(JogoSalaService.TEMPO_RANKING_SEGUNDOS);
            assertThat(estado.timestampFechamento()).isEqualTo(estado.timestampInicio() + 30_000L);
        }
    }

    @Nested
    @DisplayName("o que sai no ar a cada resposta")
    class TransmissaoDaRodada {

        @BeforeEach
        void iniciar() {
            comPalavras(palavra(1L, "casa"));
            service.registrarProfessor(SALA, PROFESSOR, "Professora Ana", "s-prof");
            service.registrarAluno(SALA, "ana", "Ana", "s1");
            service.registrarAluno(SALA, "bruno", "Bruno", "s2");
            service.registrarAluno(SALA, "carla", "Carla", "s3");
            iniciarPartida(30);
        }

        // O placar inteiro para todo mundo a cada resposta era 5 KB vezes o tamanho da
        // turma, a cada colega que respondia
        @Test
        void respostaNoMeioDaRodadaNaoCarregaOplacarInteiro() {
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);

            assertThat(r.estado()).as("nada a retransmitir para a sala ainda").isNull();
            assertThat(r.evento()).isNotNull();
            assertThat(r.evento().login()).isEqualTo("ana");
            assertThat(r.evento().statusAtual()).isEqualTo("ACERTOU");
            assertThat(r.evento().totalRespostas()).isEqualTo(1);
            assertThat(r.evento().totalAcertos()).isEqualTo(1);
        }

        @Test
        void contadoresDoEventoAcompanhamARodada() {
            service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "bruno", "Bruno", "kasa", 0);

            assertThat(r.evento().statusAtual()).isEqualTo("ERROU");
            assertThat(r.evento().totalRespostas()).isEqualTo(2);
            assertThat(r.evento().totalAcertos()).as("o erro nao conta como acerto").isEqualTo(1);
        }

        @Test
        void aUltimaRespostaDaRodadaLevaOplacarParaTodaASala() {
            service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);
            service.responder(SALA, NOME_SALA, "bruno", "Bruno", "casa", 0);
            JogoSalaService.ResultadoResposta ultima = service.responder(SALA, NOME_SALA, "carla", "Carla", "casa", 0);

            assertThat(ultima.estado()).as("rodada fechou: a sala precisa do placar novo").isNotNull();
            assertThat(ultima.estado().placar()).hasSize(3);
        }

        // O professor nao joga: a rodada fecha quando os ALUNOS respondem, sem esperar por ele
        @Test
        void oProfessorNaoSeguraOfechamentoDaRodada() {
            service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);
            service.responder(SALA, NOME_SALA, "bruno", "Bruno", "casa", 0);
            JogoSalaService.ResultadoResposta ultima = service.responder(SALA, NOME_SALA, "carla", "Carla", "casa", 0);

            assertThat(ultima.estado()).isNotNull();
        }

        @Test
        void oEventoSabeAqueRodadaPertence() {
            JogoSalaService.ResultadoResposta r = service.responder(SALA, NOME_SALA, "ana", "Ana", "casa", 0);

            assertThat(r.evento().indiceAtual()).isEqualTo(service.getEstado(SALA, NOME_SALA).indiceAtual());
        }

        // Duelo 1v1 sao dois aparelhos: transmitir o estado inteiro nao custa nada e
        // mantem a tela dos dois sempre em dia
        @Test
        void dueloContinuaRecebendoOplacarInteiro() {
            JogoSalaService serv2 = new JogoSalaService(
                palavraRepository,
                palavraEstatisticaService,
                palavraAudioService,
                conquistaEngine,
                estatisticaPartidaService,
                historicoRespostaService
            );
            serv2.registrarNoDuelo("DUELO1", "ana", "Ana", "d1");
            serv2.registrarNoDuelo("DUELO1", "bruno", "Bruno", "d2");
            serv2.iniciar("DUELO1", "Duelo", new IniciarPayload(30, 30, 30, 1, 0, 0, List.of(), List.of()), "ana");

            JogoSalaService.ResultadoResposta r = serv2.responder("DUELO1", "Duelo", "ana", "Ana", "casa", 0);

            assertThat(r.estado()).as("no duelo o placar segue indo inteiro").isNotNull();
        }

        @Test
        void oProfessorEhOdestinoDoAvisoLeve() {
            assertThat(service.loginProfessorDaSala(SALA)).isEqualTo(PROFESSOR);
        }
    }
}
