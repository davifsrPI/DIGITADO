package br.com.digitado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.digitado.domain.HistoricoResposta;
import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.PartidaIndividual;
import br.com.digitado.domain.enumeration.Dificuldade;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.repository.PartidaIndividualRepository;
import br.com.digitado.web.rest.vm.IndividualVM.EstadoIndividualVM;
import br.com.digitado.web.rest.vm.IndividualVM.ResultadoIndividualVM;
import br.com.digitado.web.rest.vm.IndividualVM.ResumoIndividualVM;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Regras do modo individual em memória: vidas, XP por acerto, resposta vazia,
 * pedidos fora de ordem e o resultado gravado no fim. Colaboradores são mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartidaIndividualServiceTest {

    private static final String LOGIN = "ana";

    @Mock
    private PalavraRepository palavraRepository;

    @Mock
    private PartidaIndividualRepository partidaIndividualRepository;

    @Mock
    private HistoricoRespostaService historicoRespostaService;

    @Mock
    private PalavraAudioService palavraAudioService;

    @Mock
    private PalavraEstatisticaService palavraEstatisticaService;

    @Mock
    private ConquistaEngineService conquistaEngine;

    @Mock
    private XpService xpService;

    private PartidaIndividualService service;

    private final List<Palavra> acervo = List.of(palavra(1L, "casa"), palavra(2L, "bola"), palavra(3L, "gato"));

    private static Palavra palavra(long id, String texto) {
        Palavra p = new Palavra();
        p.setId(id);
        p.setTexto(texto);
        p.setDificuldadeCadastrada(Dificuldade.FACIL);
        p.setAtiva(true);
        return p;
    }

    @BeforeEach
    void setUp() {
        service = new PartidaIndividualService(
            palavraRepository,
            partidaIndividualRepository,
            historicoRespostaService,
            palavraAudioService,
            palavraEstatisticaService,
            conquistaEngine,
            xpService
        );
        when(palavraRepository.findAllByAtivaTrue()).thenReturn(acervo);
        acervo.forEach(p -> when(palavraRepository.findById(p.getId())).thenReturn(Optional.of(p)));
        when(historicoRespostaService.estatisticasPorPalavra(LOGIN)).thenReturn(Map.of());
        when(partidaIndividualRepository.findFirstByLoginOrderByAcertosDescDataFimAsc(LOGIN)).thenReturn(Optional.empty());
        when(palavraAudioService.temAudio(any())).thenReturn(true);
        when(xpService.creditarPorLogin(anyString(), anyLong(), anyString())).thenAnswer(i -> i.getArgument(1));
    }

    private String textoDa(EstadoIndividualVM estado) {
        long id = estado.palavra().id();
        return acervo.stream().filter(p -> p.getId() == id).findFirst().orElseThrow().getTexto();
    }

    @Test
    @DisplayName("a palavra não sai em texto enquanto a rodada está aberta (há áudio)")
    void iniciarEscondeOTexto() {
        EstadoIndividualVM estado = service.iniciar(LOGIN);

        assertThat(estado.rodada()).isEqualTo(1);
        assertThat(estado.vidas()).isEqualTo(3);
        assertThat(estado.respondida()).isFalse();
        assertThat(estado.palavra()).isNotNull();
        assertThat(estado.palavra().texto()).isNull();
        verify(palavraAudioService).prepararAudio(any(Palavra.class));
    }

    @Test
    void semSintetizadorOTextoVaiParaONavegadorFalar() {
        when(palavraAudioService.temAudio(any())).thenReturn(false);
        assertThat(service.iniciar(LOGIN).palavra().texto()).isNotNull();
    }

    @Test
    void acertoCreditaXpGravaHistoricoESegue() throws InterruptedException {
        EstadoIndividualVM estado = service.iniciar(LOGIN);
        // Mais de 80 ms por letra: resposta de gente, não de robô (senão o XP não vale)
        Thread.sleep(400);

        ResultadoIndividualVM r = service.responder(LOGIN, textoDa(estado), 0);

        assertThat(r.correta()).isTrue();
        assertThat(r.estado().vidas()).isEqualTo(3);
        assertThat(r.estado().acertos()).isEqualTo(1);
        assertThat(r.xp()).isBetween(5L, 15L);
        assertThat(r.resumo()).isNull();
        verify(xpService).creditarPorLogin(eq(LOGIN), eq(r.xp()), anyString());
        verify(historicoRespostaService).registrar(
            eq(LOGIN),
            any(Palavra.class),
            eq(true),
            isNull(),
            isNull(),
            isNull(),
            any(),
            eq(100),
            eq(HistoricoResposta.Origem.INDIVIDUAL)
        );
    }

    @Test
    void respostaRapidaDemaisContaMasNaoDaXp() {
        EstadoIndividualVM estado = service.iniciar(LOGIN);

        ResultadoIndividualVM r = service.responder(LOGIN, textoDa(estado), 0);

        assertThat(r.correta()).isTrue();
        assertThat(r.xp()).isZero();
        verify(xpService, never()).creditarPorLogin(anyString(), anyLong(), anyString());
    }

    @Test
    void erroTiraUmaVidaENaoDaXp() {
        service.iniciar(LOGIN);

        ResultadoIndividualVM r = service.responder(LOGIN, "zzzz", 0);

        assertThat(r.correta()).isFalse();
        assertThat(r.estado().vidas()).isEqualTo(2);
        assertThat(r.estado().sequencia()).isZero();
        assertThat(r.textoCorreto()).isNotBlank();
        verify(xpService, never()).creditarPorLogin(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("resposta vazia é 'não respondeu': tira vida e vai assim para o histórico")
    void respostaVaziaEhNaoRespondeu() {
        service.iniciar(LOGIN);

        ResultadoIndividualVM r = service.responder(LOGIN, "", 0);

        assertThat(r.tipoErro()).isEqualTo(ComparadorPalavras.NAO_RESPONDEU);
        assertThat(r.estado().vidas()).isEqualTo(2);
        verify(historicoRespostaService).registrar(
            eq(LOGIN),
            any(Palavra.class),
            eq(false),
            eq(ComparadorPalavras.NAO_RESPONDEU),
            isNull(),
            isNull(),
            isNull(),
            eq(0),
            eq(HistoricoResposta.Origem.INDIVIDUAL)
        );
    }

    @Test
    @DisplayName("três erros acabam com as vidas: a partida termina e o resultado é gravado")
    void tresErrosEncerramAPartida() {
        service.iniciar(LOGIN);
        service.responder(LOGIN, "zzzz", 0);
        service.proxima(LOGIN);
        service.responder(LOGIN, "zzzz", 0);
        service.proxima(LOGIN);

        ResultadoIndividualVM ultima = service.responder(LOGIN, "", 0);

        assertThat(ultima.resumo()).isNotNull();
        assertThat(ultima.resumo().encerramento()).isEqualTo("SEM_VIDAS");
        assertThat(ultima.resumo().palavras()).isEqualTo(3);
        assertThat(ultima.resumo().erros()).isEqualTo(2);
        assertThat(ultima.resumo().naoRespondidas()).isEqualTo(1);
        verify(partidaIndividualRepository).save(
            argThat(
                (PartidaIndividual p) ->
                    p.getEncerramento() == PartidaIndividual.Encerramento.SEM_VIDAS && p.getPalavras() == 3 && p.getAcertos() == 0
            )
        );
        verify(conquistaEngine).aoConcluirPartida(LOGIN, false, false, false, false);
        // A partida acabou: não há mais o que responder nem estado aberto
        assertThat(service.estado(LOGIN)).isEmpty();
        assertThatThrownBy(() -> service.proxima(LOGIN)).isInstanceOf(PartidaIndividualService.RodadaInvalidaException.class);
    }

    @Test
    void naoDaParaResponderDuasVezesAMesmaPalavra() {
        service.iniciar(LOGIN);
        service.responder(LOGIN, "zzzz", 0);

        assertThatThrownBy(() -> service.responder(LOGIN, "casa", 0)).isInstanceOf(PartidaIndividualService.RodadaInvalidaException.class);
    }

    @Test
    void naoDaParaPularAPalavraSemResponder() {
        service.iniciar(LOGIN);

        assertThatThrownBy(() -> service.proxima(LOGIN)).isInstanceOf(PartidaIndividualService.RodadaInvalidaException.class);
    }

    @Test
    void semPartidaAbertaNaoHaOQueResponder() {
        assertThatThrownBy(() -> service.responder(LOGIN, "casa", 0)).isInstanceOf(PartidaIndividualService.RodadaInvalidaException.class);
    }

    @Test
    void palavraJaDitadaNaoVoltaEOAcervoAcaba() {
        EstadoIndividualVM e1 = service.iniciar(LOGIN);
        service.responder(LOGIN, textoDa(e1), 0);
        EstadoIndividualVM e2 = service.proxima(LOGIN);
        service.responder(LOGIN, textoDa(e2), 0);
        EstadoIndividualVM e3 = service.proxima(LOGIN);
        service.responder(LOGIN, textoDa(e3), 0);

        assertThat(List.of(e1.palavra().id(), e2.palavra().id(), e3.palavra().id())).doesNotHaveDuplicates();
        // As três já foram ditadas: a próxima vem sem palavra, e a tela encerra
        assertThat(service.proxima(LOGIN).palavra()).isNull();
    }

    @Test
    void encerrarSemResponderNadaNaoGravaPartida() {
        service.iniciar(LOGIN);

        ResumoIndividualVM resumo = service.encerrar(LOGIN);

        assertThat(resumo.palavras()).isZero();
        verify(partidaIndividualRepository, never()).save(any());
    }

    @Test
    void acertarMaisQueORecordeAvisaNovoRecorde() {
        EstadoIndividualVM estado = service.iniciar(LOGIN);
        service.responder(LOGIN, textoDa(estado), 0);

        ResumoIndividualVM resumo = service.encerrar(LOGIN);

        assertThat(resumo.encerramento()).isEqualTo("DESISTIU");
        assertThat(resumo.acertos()).isEqualTo(1);
        assertThat(resumo.novoRecorde()).isTrue();
    }

    @Test
    void comecarDeNovoEncerraAPartidaAbertaComoAbandonada() {
        EstadoIndividualVM estado = service.iniciar(LOGIN);
        service.responder(LOGIN, textoDa(estado), 0);

        service.iniciar(LOGIN);

        verify(partidaIndividualRepository).save(
            argThat((PartidaIndividual p) -> p.getEncerramento() == PartidaIndividual.Encerramento.ABANDONOU)
        );
    }
}
