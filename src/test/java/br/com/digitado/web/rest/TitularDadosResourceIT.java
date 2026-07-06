package br.com.digitado.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.digitado.IntegrationTest;
import br.com.digitado.domain.PalavraDoDiaTentativa;
import br.com.digitado.domain.User;
import br.com.digitado.domain.Usuario;
import br.com.digitado.domain.enumeration.TipoUsuario;
import br.com.digitado.repository.PalavraDoDiaTentativaRepository;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Testes de regressão dos direitos do titular (LGPD art. 18):
 * exportação traz só os dados do próprio titular (resolvido pelo token) e a
 * exclusão exige a senha correta e apaga conta + dados pessoais.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class TitularDadosResourceIT {

    private static final String LOGIN = "titular-lgpd-it";
    private static final String EMAIL = "titular-lgpd-it@localhost";
    private static final String SENHA = "senha-do-titular";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PalavraDoDiaTentativaRepository tentativaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc restMockMvc;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setLogin(LOGIN);
        user.setPassword(passwordEncoder.encode(SENHA));
        user.setActivated(true);
        user.setEmail(EMAIL);
        user.setFirstName("Titular");
        userRepository.saveAndFlush(user);

        usuarioRepository.saveAndFlush(
            new Usuario().nome("Titular").sobrenome("LGPD").email(EMAIL).senha("hash").tipoUsuario(TipoUsuario.ALUNO).ativo(true)
        );

        // Uma tentativa de palavra do dia vinculada ao titular (dado pessoal a exportar/excluir)
        Long palavraId = palavraRepository.saveAndFlush(new br.com.digitado.domain.Palavra().texto("titular-teste").ativa(false)).getId();
        tentativaRepository.saveAndFlush(new PalavraDoDiaTentativa().data(LocalDate.now()).login(LOGIN).acertou(true).palavraId(palavraId));
    }

    @Autowired
    private br.com.digitado.repository.PalavraRepository palavraRepository;

    @Test
    @WithMockUser(value = LOGIN, authorities = "ROLE_USER")
    void exportacaoTrazApenasDadosDoProprioTitular() throws Exception {
        restMockMvc
            .perform(get("/api/account/export"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conta.login").value(LOGIN))
            .andExpect(jsonPath("$.conta.email").value(EMAIL))
            .andExpect(jsonPath("$.palavraDoDia[0].acertou").value(true));
    }

    @Test
    @WithMockUser(value = LOGIN, authorities = "ROLE_USER")
    void exclusaoComSenhaErradaERecusada() throws Exception {
        restMockMvc
            .perform(delete("/api/account").contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"errada\"}"))
            .andExpect(status().isBadRequest());

        assertThat(userRepository.findOneByLogin(LOGIN)).isPresent();
    }

    @Test
    @WithMockUser(value = LOGIN, authorities = "ROLE_USER")
    void exclusaoComSenhaCorretaApagaContaEDadosPessoais() throws Exception {
        restMockMvc
            .perform(delete("/api/account").contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"" + SENHA + "\"}"))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findOneByLogin(LOGIN)).isEmpty();
        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(tentativaRepository.findByDataAndLogin(LocalDate.now(), LOGIN)).isEmpty();
    }
}
