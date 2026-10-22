package br.com.digitado.web.rest;

import br.com.digitado.repository.EstatisticaPartidaRepository;
import br.com.digitado.repository.HistoricoRespostaRepository;
import br.com.digitado.repository.PalavraDoDiaTentativaRepository;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.repository.ParticipanteSalaRepository;
import br.com.digitado.repository.PartidaIndividualRepository;
import br.com.digitado.security.AuthoritiesConstants;
import br.com.digitado.web.rest.errors.BadRequestAlertException;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Consulta, SÓ PARA O ADMIN, das tabelas que o jogo grava e que não tinham tela
 * nenhuma: histórico de respostas, estatísticas de partida, participantes de sala,
 * tentativas da palavra do dia e partidas do modo individual.
 *
 * São registros do jogo, não cadastros: a tela é de leitura - editar o histórico
 * de alguém mudaria o painel de desempenho dele sem ele ter jogado.
 *
 * Duas barreiras independentes: a URL /api/admin/** já exige ROLE_ADMIN na
 * SecurityConfiguration e a classe inteira tem @Secured(ADMIN).
 */
@RestController
@RequestMapping("/api/admin/tabelas")
@Secured(AuthoritiesConstants.ADMIN)
@Transactional(readOnly = true)
public class AdminTabelasResource {

    private static final int TAMANHO_MAXIMO = 100;

    /** Uma tabela disponível na tela. */
    public record TabelaInfoVM(String nome, String titulo, String descricao, String filtro) {}

    /** Uma página de uma tabela, já em linhas e colunas prontas para desenhar. */
    public record TabelaVM(
        String nome,
        String titulo,
        List<String> colunas,
        List<List<Object>> linhas,
        long total,
        int pagina,
        int tamanho
    ) {}

    private record Definicao(TabelaInfoVM info, List<String> colunas, BiFunction<String, Pageable, Page<List<Object>>> consulta) {}

    private final Map<String, Definicao> tabelas = new LinkedHashMap<>();
    private final PalavraRepository palavraRepository;

    public AdminTabelasResource(
        HistoricoRespostaRepository historicoRespostaRepository,
        EstatisticaPartidaRepository estatisticaPartidaRepository,
        ParticipanteSalaRepository participanteSalaRepository,
        PalavraDoDiaTentativaRepository palavraDoDiaTentativaRepository,
        PartidaIndividualRepository partidaIndividualRepository,
        PalavraRepository palavraRepository
    ) {
        this.palavraRepository = palavraRepository;

        registrar(
            new TabelaInfoVM(
                "historico-respostas",
                "Histórico de respostas",
                "Cada resposta de jogador logado, em todos os modos.",
                "login"
            ),
            List.of("ID", "Data", "Login", "Palavra", "Correta", "Tipo de erro", "Similaridade (%)", "Tempo (ms)", "Dificuldade", "Origem"),
            (busca, pagina) -> {
                Page<br.com.digitado.domain.HistoricoResposta> page = busca.isEmpty()
                    ? historicoRespostaRepository.findAll(pagina)
                    : historicoRespostaRepository.findByLoginContainingIgnoreCase(busca, pagina);
                Map<Long, String> textos = textosDasPalavras(page.getContent().stream().map(h -> h.getPalavraId()).toList());
                return page.map(h ->
                    linha(
                        h.getId(),
                        h.getDataResposta(),
                        h.getLogin(),
                        textos.getOrDefault(h.getPalavraId(), "#" + h.getPalavraId()),
                        h.getCorreta(),
                        h.getTipoErro(),
                        h.getSimilaridade(),
                        h.getTempoMs(),
                        h.getDificuldade(),
                        h.getOrigem()
                    )
                );
            }
        );
        registrar(
            new TabelaInfoVM(
                "estatisticas-partida",
                "Estatísticas de partida",
                "O resultado guardado da última partida encerrada de cada sala.",
                "sala"
            ),
            List.of("ID", "Sala", "Encerrada em", "Palavras"),
            (busca, pagina) ->
                (busca.isEmpty()
                        ? estatisticaPartidaRepository.findAll(pagina)
                        : estatisticaPartidaRepository.findBySalaCodigoContainingIgnoreCase(busca, pagina)).map(e ->
                        linha(e.getId(), e.getSalaCodigo(), e.getDataEncerramento(), e.getTotalPalavras())
                    )
        );
        registrar(
            new TabelaInfoVM(
                "participantes-sala",
                "Participantes de sala",
                "Nome, turma e apelido que cada aluno informou ao entrar.",
                "login"
            ),
            List.of("ID", "Sala", "Login", "Nome", "Turma", "Usa apelido", "Apelido", "Entrada"),
            (busca, pagina) ->
                (busca.isEmpty()
                        ? participanteSalaRepository.findAll(pagina)
                        : participanteSalaRepository.findByLoginContainingIgnoreCase(busca, pagina)).map(p ->
                        linha(
                            p.getId(),
                            p.getSalaCodigo(),
                            p.getLogin(),
                            p.getNome(),
                            p.getTurma(),
                            p.isUsarApelido(),
                            p.getApelido(),
                            p.getDataEntrada()
                        )
                    )
        );
        registrar(
            new TabelaInfoVM(
                "palavra-do-dia-tentativas",
                "Tentativas da palavra do dia",
                "Uma linha por tentativa. Login vazio: tentativa anônima ou já anonimizada pela retenção.",
                "login"
            ),
            List.of("ID", "Dia", "Login", "Palavra", "Acertou", "Registrada em"),
            (busca, pagina) -> {
                Page<br.com.digitado.domain.PalavraDoDiaTentativa> page = busca.isEmpty()
                    ? palavraDoDiaTentativaRepository.findAll(pagina)
                    : palavraDoDiaTentativaRepository.findByLoginContainingIgnoreCase(busca, pagina);
                Map<Long, String> textos = textosDasPalavras(page.getContent().stream().map(t -> t.getPalavraId()).toList());
                return page.map(t ->
                    linha(
                        t.getId(),
                        t.getData(),
                        t.getLogin(),
                        textos.getOrDefault(t.getPalavraId(), "#" + t.getPalavraId()),
                        t.getAcertou(),
                        t.getCriadoEm()
                    )
                );
            }
        );
        registrar(
            new TabelaInfoVM("partidas-individuais", "Partidas individuais", "Partidas encerradas do modo individual.", "login"),
            List.of("ID", "Login", "Início", "Fim", "Palavras", "Acertos", "Melhor sequência", "XP", "Encerramento"),
            (busca, pagina) ->
                (busca.isEmpty()
                        ? partidaIndividualRepository.findAll(pagina)
                        : partidaIndividualRepository.findByLoginContainingIgnoreCase(busca, pagina)).map(p ->
                        linha(
                            p.getId(),
                            p.getLogin(),
                            p.getDataInicio(),
                            p.getDataFim(),
                            p.getPalavras(),
                            p.getAcertos(),
                            p.getMelhorSequencia(),
                            p.getXpGanho(),
                            p.getEncerramento()
                        )
                    )
        );
    }

    /** As tabelas que a tela oferece. */
    @GetMapping("")
    public List<TabelaInfoVM> listar() {
        return tabelas.values().stream().map(Definicao::info).toList();
    }

    /** Uma página da tabela, dos registros mais novos para os mais antigos. */
    @GetMapping("/{nome}")
    public TabelaVM consultar(
        @PathVariable("nome") String nome,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size,
        @RequestParam(name = "busca", required = false) String busca
    ) {
        Definicao definicao = tabelas.get(nome);
        if (definicao == null) {
            throw new BadRequestAlertException("Tabela desconhecida: " + nome, "tabela", "tabeladesconhecida");
        }
        Pageable pagina = PageRequest.of(
            Math.max(0, page),
            Math.max(1, Math.min(TAMANHO_MAXIMO, size)),
            Sort.by(Sort.Direction.DESC, "id")
        );
        Page<List<Object>> resultado = definicao.consulta().apply(busca == null ? "" : busca.trim(), pagina);
        return new TabelaVM(
            nome,
            definicao.info().titulo(),
            definicao.colunas(),
            resultado.getContent(),
            resultado.getTotalElements(),
            resultado.getNumber(),
            resultado.getSize()
        );
    }

    private void registrar(TabelaInfoVM info, List<String> colunas, BiFunction<String, Pageable, Page<List<Object>>> consulta) {
        tabelas.put(info.nome(), new Definicao(info, colunas, consulta));
    }

    // Texto das palavras de uma página, numa consulta só (em vez de uma por linha)
    private Map<Long, String> textosDasPalavras(Collection<Long> ids) {
        Set<Long> unicos = new HashSet<>(ids);
        unicos.remove(null);
        if (unicos.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> textos = new HashMap<>();
        palavraRepository.findAllById(unicos).forEach(p -> textos.put(p.getId(), p.getTexto()));
        return textos;
    }

    // Uma linha da tabela; enums e datas viram texto, o resto vai como está
    private static List<Object> linha(Object... valores) {
        List<Object> linha = new ArrayList<>(valores.length);
        for (Object v : valores) {
            linha.add(v instanceof Enum<?> e ? e.name() : v instanceof java.time.temporal.Temporal t ? t.toString() : v);
        }
        return linha;
    }
}
