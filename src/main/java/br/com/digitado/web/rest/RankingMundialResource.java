package br.com.digitado.web.rest;

import br.com.digitado.domain.Usuario;
import br.com.digitado.repository.UserRepository;
import br.com.digitado.repository.UsuarioRepository;
import br.com.digitado.security.SecurityUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ranking Mundial: classificação de todos os usuários pelo XP acumulado
 * (usuario.xp — hoje alimentado pelos acertos na Palavra do Dia).
 *
 * A identidade de "minha posição" vem exclusivamente do token JWT, como nas
 * conquistas. Só o nome de exibição vai para o frontend (primeiro nome +
 * inicial do sobrenome) — nunca e-mail ou login de outros usuários.
 */
@RestController
@RequestMapping("/api/ranking-mundial")
@Transactional(readOnly = true)
public class RankingMundialResource {

    private static final Logger LOG = LoggerFactory.getLogger(RankingMundialResource.class);

    private final UsuarioRepository usuarioRepository;
    private final UserRepository userRepository;

    public RankingMundialResource(UsuarioRepository usuarioRepository, UserRepository userRepository) {
        this.usuarioRepository = usuarioRepository;
        this.userRepository = userRepository;
    }

    // Uma linha do ranking; "eu" marca a linha do usuário autenticado para destaque na UI
    public record RankingEntryVM(int posicao, String nome, long xp, boolean eu) {}

    // Top 50 + resumo do próprio usuário (posição real mesmo fora do top)
    public record RankingMundialVM(List<RankingEntryVM> top, long meuXp, Integer minhaPosicao) {}

    @GetMapping("")
    public RankingMundialVM getRankingMundial() {
        LOG.debug("REST request to get Ranking Mundial");

        // Resolve o Usuario do autenticado (login -> User -> Usuario pelo e-mail)
        Optional<Usuario> eu = SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()));

        List<Usuario> top = usuarioRepository.findTop50ByOrderByXpDescIdAsc();
        List<RankingEntryVM> entries = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            Usuario u = top.get(i);
            boolean souEu = eu.isPresent() && eu.get().getId().equals(u.getId());
            entries.add(new RankingEntryVM(i + 1, nomeExibicao(u), u.getXp(), souEu));
        }

        long meuXp = eu.map(Usuario::getXp).orElse(0L);
        Integer minhaPosicao = eu.map(u -> (int) usuarioRepository.countByXpGreaterThan(u.getXp()) + 1).orElse(null);
        return new RankingMundialVM(entries, meuXp, minhaPosicao);
    }

    // Nome público no ranking: primeiro nome + inicial do sobrenome (privacidade)
    private String nomeExibicao(Usuario u) {
        String nome = u.getNome() != null ? u.getNome().trim() : "Aluno";
        String sobrenome = u.getSobrenome() != null ? u.getSobrenome().trim() : "";
        if (!sobrenome.isEmpty()) {
            return nome + " " + sobrenome.charAt(0) + ".";
        }
        return nome;
    }
}
