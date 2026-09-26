package br.com.digitado.web.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.domain.Sala;
import br.com.digitado.repository.SalaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.service.CodigoSalaService;
import br.com.digitado.service.ConfiguracaoPartidaService;
import br.com.digitado.service.EstatisticaPartidaService;
import br.com.digitado.service.JogoSalaService;
import br.com.digitado.service.PalavraAudioService;
import br.com.digitado.service.ParticipanteSalaService;
import br.com.digitado.service.ResumoPartidaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * A rota do codigo novo nao pode ser confundida com a busca de uma sala.
 *
 * GET /api/salas/{codigo} casa qualquer segmento, e a geracao do codigo passou a
 * morar num endpoint do mesmo controller. Se o roteamento escorregasse, pedir um
 * codigo cairia em "sala nao encontrada" e NINGUEM conseguiria criar sala - por
 * isso este teste existe, e roda sem banco e sem Docker (standaloneSetup levanta
 * so o mapeamento de rotas do controller).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalaCodigoRotaTest {

    @Mock
    private SalaRepository salaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JogoSalaService jogoSalaService;

    @Mock
    private EstatisticaPartidaService estatisticaPartidaService;

    @Mock
    private ParticipanteSalaService participanteSalaService;

    @Mock
    private ResumoPartidaService resumoPartidaService;

    @Mock
    private CodigoSalaService codigoSalaService;

    @Mock
    private ConfiguracaoPartidaService configuracaoPartidaService;

    @Mock
    private PalavraAudioService palavraAudioService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SalaResource resource = new SalaResource(
            salaRepository,
            userRepository,
            usuarioRepository,
            jogoSalaService,
            estatisticaPartidaService,
            participanteSalaService,
            resumoPartidaService,
            codigoSalaService,
            configuracaoPartidaService,
            palavraAudioService,
            new ObjectMapper()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
    }

    @Test
    @DisplayName("GET /api/salas/codigo/novo devolve o codigo do servico, nao uma sala")
    void rotaDoCodigoNovo() throws Exception {
        when(codigoSalaService.gerarDisponivel()).thenReturn("H4XEZ7");

        mockMvc.perform(get("/api/salas/codigo/novo")).andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("H4XEZ7"));

        // A busca de sala nao pode nem ter sido consultada nesse caminho
        verify(salaRepository, never()).findById(anyString());
    }

    @Test
    @DisplayName("GET /api/salas/{codigo} continua buscando a sala daquele codigo")
    void rotaDaSalaContinuaValendo() throws Exception {
        Sala sala = new Sala().codigo("ABC234").nome("Turma 5A");
        when(salaRepository.findById("ABC234")).thenReturn(Optional.of(sala));

        mockMvc.perform(get("/api/salas/ABC234")).andExpect(status().isOk()).andExpect(jsonPath("$.codigo").value("ABC234"));

        // E a sala nunca passa pelo gerador de codigo
        verify(codigoSalaService, never()).gerarDisponivel();
    }

    @Test
    @DisplayName("uma sala que por acaso se chame CODIGO nao e capturada pela rota nova")
    void salaChamadaCodigoNaoColide() throws Exception {
        Sala sala = new Sala().codigo("CODIGO").nome("Sala de teste");
        when(salaRepository.findById("CODIGO")).thenReturn(Optional.of(sala));

        // /api/salas/CODIGO tem UM segmento; a rota nova tem dois (codigo/novo)
        mockMvc.perform(get("/api/salas/CODIGO")).andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Sala de teste"));

        verify(codigoSalaService, never()).gerarDisponivel();
    }
}
