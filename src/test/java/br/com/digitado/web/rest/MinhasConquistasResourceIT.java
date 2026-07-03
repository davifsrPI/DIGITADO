package br.com.digitado.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.Conquista;
import br.com.digitado.domain.User;
import br.com.digitado.domain.Usuario;
import br.com.digitado.domain.UsuarioConquista;
import br.com.digitado.domain.enumeration.TipoUsuario;
import br.com.digitado.repository.ConquistaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioConquistaRepository;
import br.com.digitado.repository.UsuarioRepository;
import java.time.Instant;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de regressão do fluxo crítico de conquistas (regra 8 de monitoramento):
 *
 * 1. O usuário autenticado vê o catálogo com o SEU estado (resolvido pelo token, não por parâmetro);
 * 2. Conquistas de um usuário nunca vazam para outro (isolamento entre contas);
 * 3. Anônimo não acessa nada (401);
 * 4. Usuário comum não consegue se auto-premiar nem alterar o catálogo (403).
 *
 * Se qualquer refactor quebrar a resolução por token ou as regras de acesso,
 * estes testes falham antes do deploy.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MinhasConquistasResourceIT {

    private static final String LOGIN_ALUNO = "aluno-conquista-it";
    private static final String EMAIL_ALUNO = "aluno-conquista-it@localhost";
    private static final String LOGIN_OUTRO = "outro-aluno-it";
    private static final String EMAIL_OUTRO = "outro-aluno-it@localhost";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConquistaRepository conquistaRepository;

    @Autowired
    private UsuarioConquistaRepository usuarioConquistaRepository;

    @Autowired
    private MockMvc restMockMvc;

    private Conquista conquistaDesbloqueada;

    @BeforeEach
    void setup() {
        criarUserEUsuario(LOGIN_ALUNO, EMAIL_ALUNO);
        criarUserEUsuario(LOGIN_OUTRO, EMAIL_OUTRO);

        conquistaDesbloqueada = conquistaRepository.saveAndFlush(
            new Conquista().nome("Conquista IT Desbloqueada").descricao("Conquista de teste").xpRecompensa(50)
        );
        conquistaRepository.saveAndFlush(new Conquista().nome("Conquista IT Bloqueada").descricao("Conquista de teste").xpRecompensa(100));

        // Somente o primeiro aluno desbloqueou a conquista
        Usuario aluno = usuarioRepository.findByEmail(EMAIL_ALUNO).orElseThrow();
        usuarioConquistaRepository.saveAndFlush(
            new UsuarioConquista().aluno(aluno).conquista(conquistaDesbloqueada).concluida(true).progresso(100).dataConquista(Instant.now())
        );
    }

    // Cria o par User (autenticação) + Usuario (domínio), ligados pelo e-mail —
    // exatamente como o endpoint resolve a identidade em produção
    private void criarUserEUsuario(String login, String email) {
        User user = new User();
        user.setLogin(login);
        user.setPassword(RandomStringUtils.insecure().nextAlphanumeric(60));
        user.setActivated(true);
        user.setEmail(email);
        userRepository.saveAndFlush(user);

        Usuario usuario = new Usuario()
            .nome("Aluno")
            .sobrenome("Teste")
            .email(email)
            .senha("senha-teste")
            .tipoUsuario(TipoUsuario.ALUNO)
            .ativo(true);
        usuarioRepository.saveAndFlush(usuario);
    }

    @Test
    @WithMockUser(value = LOGIN_ALUNO, authorities = "ROLE_USER")
    void usuarioAutenticadoVeSuasConquistas() throws Exception {
        long totalCatalogo = conquistaRepository.count();

        restMockMvc
            .perform(get("/api/conquistas/minhas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(totalCatalogo))
            .andExpect(jsonPath("$.desbloqueadas").value(1))
            .andExpect(jsonPath("$.xpGanho").value(50))
            // Ordenação: a desbloqueada vem primeiro, com data e flag corretas
            .andExpect(jsonPath("$.conquistas[0].id").value(conquistaDesbloqueada.getId()))
            .andExpect(jsonPath("$.conquistas[0].desbloqueada").value(true))
            .andExpect(jsonPath("$.conquistas[0].dataConquista").isNotEmpty());
    }

    @Test
    @WithMockUser(value = LOGIN_OUTRO, authorities = "ROLE_USER")
    void conquistasDeUmUsuarioNaoVazamParaOutro() throws Exception {
        restMockMvc
            .perform(get("/api/conquistas/minhas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.desbloqueadas").value(0))
            .andExpect(jsonPath("$.xpGanho").value(0));
    }

    @Test
    @WithAnonymousUser
    void anonimoNaoAcessaConquistas() throws Exception {
        restMockMvc.perform(get("/api/conquistas/minhas")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(value = LOGIN_ALUNO, authorities = "ROLE_USER")
    void usuarioComumNaoConsegueSeAutoPremiar() throws Exception {
        restMockMvc
            .perform(
                post("/api/usuario-conquistas").contentType(MediaType.APPLICATION_JSON).content("{\"concluida\":true,\"progresso\":100}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(value = LOGIN_ALUNO, authorities = "ROLE_USER")
    void usuarioComumNaoAlteraCatalogoDeConquistas() throws Exception {
        restMockMvc
            .perform(post("/api/conquistas").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Hack\",\"xpRecompensa\":9999}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(value = LOGIN_ALUNO, authorities = "ROLE_USER")
    void respostaCarregaRequestIdParaRastreabilidade() throws Exception {
        // Regra 1 de monitoramento: toda resposta devolve o X-Request-ID
        restMockMvc.perform(get("/api/conquistas/minhas")).andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"));
    }
}
