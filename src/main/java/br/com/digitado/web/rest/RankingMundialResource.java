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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ranking Mundial: classificação de todos os usuários pelo XP acumulado
 * (usuario.xp — hoje alimentado pelos acertos na Palavra do Dia).
 *
 * Endpoint PÚBLICO (/api/public/** é permitAll): visitantes sem conta podem
 * ver o ranking na tela inicial. Para quem está logado, a identidade de
 * "minha posição" vem exclusivamente do token JWT, como nas conquistas;
 * anônimo recebe meuXp 0 e minhaPosicao null. Só o nome de exibição vai para
 * o frontend (primeiro nome + inicial do sobrenome) — nunca e-mail ou login.
 */
@RestController
@RequestMapping("/api/public/ranking-mundial")
@Transactional(readOnly = true)
public class RankingMundialResource {

    private static final Logger LOG = LoggerFactory.getLogger(RankingMundialResource.class);

    private final UsuarioRepository usuarioRepository;
    private final UserRepository userRepository;

    public RankingMundialResource(UsuarioRepository usuarioRepository, UserRepository userRepository) {
        this.usuarioRepository = usuarioRepository;
        this.userRepository = userRepository;
    }

    // Tamanho de página: quantas posições vêm por requisição (o front pede mais com "Carregar mais")
    private static final int TAMANHO_PAGINA = 50;

    // Uma linha do ranking; "eu" marca a linha do usuário autenticado para destaque na UI
    public record RankingEntryVM(int posicao, String nome, long xp, boolean eu) {}

    // Página do ranking + resumo do próprio usuário (posição real mesmo fora da página).
    // total: quantas pessoas existem no ranking; temMais: há mais páginas para carregar.
    public record RankingMundialVM(List<RankingEntryVM> top, long meuXp, Integer minhaPosicao, long total, boolean temMais) {}

    /**
     * {@code GET /api/ranking-mundial?page=0} : página do ranking completo.
     * Todos os usuários aparecem — o front vai pedindo página a página até o fim.
     */
    @GetMapping("")
    public RankingMundialVM getRankingMundial(@RequestParam(name = "page", defaultValue = "0") int page) {
        LOG.debug("REST request to get Ranking Mundial, page {}", page);

        // Resolve o Usuario do autenticado (login -> User -> Usuario pelo e-mail)
        Optional<Usuario> eu = SecurityUtils.getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .flatMap(user -> usuarioRepository.findByEmail(user.getEmail()));

        Page<Usuario> pagina = usuarioRepository.findAllByOrderByXpDescIdAsc(PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA));
        List<RankingEntryVM> entries = new ArrayList<>(pagina.getNumberOfElements());
        int posicaoInicial = pagina.getNumber() * TAMANHO_PAGINA;
        List<Usuario> usuarios = pagina.getContent();
        for (int i = 0; i < usuarios.size(); i++) {
            Usuario u = usuarios.get(i);
            boolean souEu = eu.isPresent() && eu.orElseThrow().getId().equals(u.getId());
            entries.add(new RankingEntryVM(posicaoInicial + i + 1, nomeExibicao(u), u.getXp(), souEu));
        }

        long meuXp = eu.map(Usuario::getXp).orElse(0L);
        Integer minhaPosicao = eu.map(u -> (int) usuarioRepository.countByXpGreaterThan(u.getXp()) + 1).orElse(null);
        return new RankingMundialVM(entries, meuXp, minhaPosicao, pagina.getTotalElements(), pagina.hasNext());
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
