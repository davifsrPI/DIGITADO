package br.com.digitado.service;

import br.com.digitado.domain.Usuario;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concessão de XP ao usuário — alimenta o Ranking Mundial.
 *
 * O XP fica numa coluna persistente (usuario.xp) em vez de ser calculado das
 * tentativas, porque os logins de palavra_do_dia_tentativa são anonimizados
 * após o prazo de retenção (LGPD) — o XP precisa sobreviver a isso.
 *
 * Chamado apenas pelo backend, nos pontos onde o acerto já foi validado.
 */
@Service
public class XpService {

    // Recompensa por acertar a palavra do dia
    public static final long XP_ACERTO_PALAVRA_DIA = 300;

    private static final Logger LOG = LoggerFactory.getLogger(XpService.class);

    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;

    public XpService(UserRepository userRepository, UsuarioRepository usuarioRepository) {
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Credita o XP da palavra do dia ao usuário do login informado.
     * Retorna o XP concedido (0 se o login não tiver Usuario correspondente —
     * ex.: conta de admin sem cadastro de aluno). Falha aqui não pode derrubar
     * o fluxo da tentativa — XP é acessório, então o erro é só logado.
     */
    @Transactional
    public long premiarAcertoPalavraDoDia(String login) {
        try {
            // Mesmo caminho de resolução usado nas conquistas: login -> User -> Usuario (pelo e-mail)
            Optional<Usuario> usuario = userRepository.findOneByLogin(login).flatMap(u -> usuarioRepository.findByEmail(u.getEmail()));
            if (usuario.isEmpty()) {
                LOG.warn("XP não creditado: login {} não tem Usuario correspondente", login);
                return 0;
            }
            usuarioRepository.incrementarXp(usuario.orElseThrow().getId(), XP_ACERTO_PALAVRA_DIA);
            LOG.info("XP: +{} para {} (acerto na palavra do dia)", XP_ACERTO_PALAVRA_DIA, login);
            return XP_ACERTO_PALAVRA_DIA;
        } catch (Exception e) {
            LOG.error("Falha ao creditar XP da palavra do dia para {}: {}", login, e.getMessage(), e);
            return 0;
        }
    }
}
