package br.com.digitado.service;

import br.com.digitado.domain.User;
import br.com.digitado.domain.Usuario;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Direitos do titular (LGPD art. 18): exportação (portabilidade) e exclusão da conta.
 *
 * Tudo aqui parte do LOGIN AUTENTICADO (token) — o frontend não envia id nenhum,
 * então é impossível exportar/excluir dados de outra pessoa.
 *
 * Estratégia de exclusão:
 * - APAGA tudo que é pessoal e individual: respostas, erros ortográficos, ranking,
 *   conquistas, tentativas da palavra do dia, vínculos com salas, perfil e conta;
 * - ANONIMIZA o que é conteúdo compartilhado criado pelo titular (salas, listas,
 *   palavras): o conteúdo continua servindo à turma, mas sem vínculo com a pessoa
 *   (art. 12: dado anonimizado deixa de ser pessoal).
 */
@Service
public class TitularDadosService {

    private static final Logger LOG = LoggerFactory.getLogger(TitularDadosService.class);

    private final UserRepository userRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    public TitularDadosService(
        UserRepository userRepository,
        UsuarioRepository usuarioRepository,
        PasswordEncoder passwordEncoder,
        EntityManager em
    ) {
        this.userRepository = userRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.em = em;
    }

    /** Confere a senha atual do titular — exigida antes da exclusão (evita que um token roubado apague a conta). */
    @Transactional(readOnly = true)
    public boolean senhaConfere(String login, String senhaInformada) {
        return userRepository
            .findOneByLogin(login)
            .map(user -> senhaInformada != null && passwordEncoder.matches(senhaInformada, user.getPassword()))
            .orElse(false);
    }

    /**
     * Exporta todos os dados pessoais do titular em estrutura simples (JSON no endpoint).
     * Nunca inclui senha/hash. Montado inteiramente no backend a partir do login do token.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportarDados(String login) {
        Map<String, Object> dados = new LinkedHashMap<>();

        userRepository
            .findOneByLogin(login)
            .ifPresent(user ->
                dados.put(
                    "conta",
                    Map.of(
                        "login",
                        user.getLogin(),
                        "nome",
                        user.getFirstName() != null ? user.getFirstName() : "",
                        "sobrenome",
                        user.getLastName() != null ? user.getLastName() : "",
                        "email",
                        user.getEmail() != null ? user.getEmail() : ""
                    )
                )
            );

        buscarUsuario(login).ifPresent(u -> {
            dados.put("perfil", Map.of("nome", u.getNome(), "sobrenome", u.getSobrenome(), "tipo", u.getTipoUsuario().name()));

            dados.put(
                "conquistas",
                em
                    .createQuery(
                        "select c.nome, uc.dataConquista, uc.concluida from UsuarioConquista uc join uc.conquista c where uc.aluno = :u",
                        Object[].class
                    )
                    .setParameter("u", u)
                    .getResultList()
                    .stream()
                    .map(l -> Map.of("conquista", l[0], "data", String.valueOf(l[1]), "concluida", l[2]))
                    .toList()
            );

            dados.put(
                "respostas",
                em
                    .createQuery("select r.dataResposta, r.correta, r.pontuacao from Resposta r where r.aluno = :u", Object[].class)
                    .setParameter("u", u)
                    .getResultList()
                    .stream()
                    .map(l -> Map.of("data", String.valueOf(l[0]), "correta", String.valueOf(l[1]), "pontuacao", String.valueOf(l[2])))
                    .toList()
            );

            dados.put(
                "ranking",
                em.createQuery("select r.id from Ranking r where r.aluno = :u", Long.class).setParameter("u", u).getResultList().size() +
                " registro(s) de pontuação"
            );
        });

        dados.put(
            "palavraDoDia",
            em
                .createQuery("select t.data, t.acertou from PalavraDoDiaTentativa t where t.login = :login", Object[].class)
                .setParameter("login", login)
                .getResultList()
                .stream()
                .map(l -> Map.of("data", String.valueOf(l[0]), "acertou", l[1]))
                .toList()
        );

        return dados;
    }

    /**
     * Exclui a conta e todos os dados pessoais do titular (art. 18, VI).
     * Ordem respeita as FKs; conteúdo compartilhado é anonimizado, não apagado.
     */
    @Transactional
    public void excluirConta(String login) {
        Optional<Usuario> usuarioOpt = buscarUsuario(login);

        usuarioOpt.ifPresent(u -> {
            // 1. Dados individuais de jogo (na ordem das FKs)
            em
                .createQuery("delete from ErroOrtografico e where e.resposta in (select r from Resposta r where r.aluno = :u)")
                .setParameter("u", u)
                .executeUpdate();
            em.createQuery("delete from Resposta r where r.aluno = :u").setParameter("u", u).executeUpdate();
            em.createQuery("delete from Ranking r where r.aluno = :u").setParameter("u", u).executeUpdate();
            em.createQuery("delete from UsuarioConquista uc where uc.aluno = :u").setParameter("u", u).executeUpdate();

            // 2. Vínculo aluno-sala (tabela de junção)
            em
                .createNativeQuery("delete from rel_usuario__salas_aluno where usuario_id = :id")
                .setParameter("id", u.getId())
                .executeUpdate();

            // 3. Conteúdo compartilhado criado pelo titular: fica, mas ANONIMIZADO
            em.createQuery("update Sala s set s.professor = null where s.professor = :u").setParameter("u", u).executeUpdate();
            em.createQuery("update ListaPalavras l set l.professor = null where l.professor = :u").setParameter("u", u).executeUpdate();
            em.createQuery("update Palavra p set p.criador = null where p.criador = :u").setParameter("u", u).executeUpdate();

            // 4. Perfil do domínio
            usuarioRepository.delete(u);
        });

        // 5. Tentativas da palavra do dia (chaveadas por login) e a conta de autenticação
        em.createQuery("delete from PalavraDoDiaTentativa t where t.login = :login").setParameter("login", login).executeUpdate();
        userRepository.findOneByLogin(login).ifPresent(userRepository::delete);

        // Log SEM dados além do login — trilha de auditoria da exclusão (art. 37)
        LOG.info("LGPD: conta e dados pessoais do titular '{}' excluídos a pedido do próprio", login);
    }

    // Resolve o Usuario do domínio pelo e-mail do User autenticado (mesma ponte do resto do sistema)
    private Optional<Usuario> buscarUsuario(String login) {
        return userRepository.findOneByLogin(login).map(User::getEmail).flatMap(usuarioRepository::findByEmail);
    }
}
