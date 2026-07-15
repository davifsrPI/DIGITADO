package br.com.digitado.service;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.PalavraDoDiaTentativa;
import br.com.digitado.repository.PalavraDoDiaTentativaRepository;
import br.com.digitado.repository.PalavraRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica da Palavra do Dia — TODA a informação sensível fica aqui no backend:
 *
 * - A palavra é sorteada de forma determinística pelo dia (mesma palavra para todos
 *   durante o dia inteiro); o desafio é um ditado — o cliente recebe o texto apenas
 *   para a síntese de voz, sem exibi-lo.
 * - A validação da resposta acontece aqui, com a mesma regra das partidas.
 * - O controle de "uma chance" é do servidor: conta logada é validada contra o banco
 *   (constraint única por dia+login) e visitante anônimo recebe um cookie httpOnly.
 */
@Service
public class PalavraDoDiaService {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PalavraDoDiaService.class);

    // Fuso oficial do jogo — o "dia" vira à meia-noite de Brasília
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final PalavraRepository palavraRepository;
    private final PalavraDoDiaTentativaRepository tentativaRepository;
    private final PalavraEstatisticaService estatisticaService;
    private final XpService xpService;
    private final ConquistaEngineService conquistaEngine;

    public PalavraDoDiaService(
        PalavraRepository palavraRepository,
        PalavraDoDiaTentativaRepository tentativaRepository,
        PalavraEstatisticaService estatisticaService,
        XpService xpService,
        ConquistaEngineService conquistaEngine
    ) {
        this.palavraRepository = palavraRepository;
        this.tentativaRepository = tentativaRepository;
        this.estatisticaService = estatisticaService;
        this.xpService = xpService;
        this.conquistaEngine = conquistaEngine;
    }

    public LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    /**
     * Cache da palavra do dia: a resposta é a MESMA durante o dia inteiro, mas o
     * endpoint é público e chamado em toda visita à home — sem cache eram duas
     * consultas (COUNT + SELECT paginado) por visita. A chave é a própria data:
     * na virada do dia (fuso de Brasília) o cache expira sozinho; reiniciar o
     * servidor apenas o recomputa. Bônus de estabilidade: ativar/desativar
     * palavras no meio do dia não troca a palavra que todos já estão jogando.
     * volatile: escrito por uma requisição e lido pelas demais threads.
     */
    private volatile CachePalavraDoDia cache;

    private record CachePalavraDoDia(LocalDate dia, Optional<Palavra> palavra) {}

    // Sorteio determinístico: índice = dia-da-época % total de palavras ativas.
    // Ordenação estável por id garante a mesma palavra em todas as chamadas do dia.
    public Optional<Palavra> palavraDeHoje() {
        LocalDate hoje = hoje();
        CachePalavraDoDia atual = cache;
        if (atual != null && atual.dia().equals(hoje)) {
            return atual.palavra();
        }
        long total = palavraRepository.countByAtivaTrue();
        Optional<Palavra> palavra = Optional.empty();
        if (total > 0) {
            int indice = (int) (hoje.toEpochDay() % total);
            palavra = palavraRepository.findByAtivaTrue(PageRequest.of(indice, 1, Sort.by("id"))).stream().findFirst();
        }
        // Sem palavras ativas o Optional vazio também é cacheado — evita marretar o
        // banco em toda visita de um ambiente ainda sem acervo
        cache = new CachePalavraDoDia(hoje, palavra);
        return palavra;
    }

    // O usuário logado já usou a chance de hoje? (fonte da verdade: banco)
    public Optional<PalavraDoDiaTentativa> tentativaDoUsuario(String login) {
        return tentativaRepository.findByDataAndLogin(hoje(), login);
    }

    // Valida a resposta, grava a tentativa e atualiza as estatísticas — tudo no backend.
    // Mesma regra de comparação das partidas: ignora maiúsculas, acentos contam.
    @Transactional
    public boolean tentar(Palavra palavra, String login, String resposta) {
        boolean acertou = resposta != null && resposta.trim().toLowerCase().equals(palavra.getTexto().trim().toLowerCase());

        tentativaRepository.save(new PalavraDoDiaTentativa().data(hoje()).login(login).acertou(acertou).palavraId(palavra.getId()));
        estatisticaService.registrarTentativa(palavra.getId(), acertou);
        // Acerto de usuário logado vale XP no Ranking Mundial (anônimo não tem conta para creditar)
        if (acertou && login != null) {
            xpService.premiarAcertoPalavraDoDia(login);
            // O XP novo pode desbloquear conquistas de XP acumulado/posição no ranking.
            // Transação própria + try/catch: falha aqui não derruba a tentativa.
            try {
                conquistaEngine.verificarXpERanking(login);
            } catch (Exception e) {
                LOG.error("Falha ao verificar conquistas de XP de {}: {}", login, e.getMessage(), e);
            }
        }
        return acertou;
    }

    // Contadores agregados da palavra (para exibir "% de acerto" após a tentativa).
    // Leitura direta no banco, nas colunas total_tentativas/total_acertos da tabela palavra
    public long[] estatisticasDaPalavra(Long palavraId) {
        List<Object[]> linhas = palavraRepository.buscarEstatistica(palavraId);
        if (linhas.isEmpty()) {
            return new long[] { 0, 0 };
        }
        Object[] linha = linhas.get(0);
        return new long[] { ((Number) linha[0]).longValue(), ((Number) linha[1]).longValue() };
    }
}
