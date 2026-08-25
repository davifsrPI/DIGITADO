package br.com.digitado.service;

import br.com.digitado.domain.EstatisticaPartida;
import br.com.digitado.repository.EstatisticaPartidaRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda e devolve o consolidado da última partida de cada sala.
 *
 * O JogoSalaService mantém a partida só em memória: quando o professor fecha a
 * sala (ou o último participante desconecta) o estado é descartado, e antes
 * disto o desempenho da turma se perdia junto - reabrir a sala mostrava uma sala
 * zerada. Ao encerrar a partida o ranking e o relatório por palavra são
 * serializados aqui, e a tela "Ver estatísticas" do professor lê deste snapshot,
 * com a sala aberta ou fechada.
 */
@Service
public class EstatisticaPartidaService {

    private static final Logger LOG = LoggerFactory.getLogger(EstatisticaPartidaService.class);

    private final EstatisticaPartidaRepository estatisticaPartidaRepository;
    private final ObjectMapper objectMapper;

    public EstatisticaPartidaService(EstatisticaPartidaRepository estatisticaPartidaRepository, ObjectMapper objectMapper) {
        this.estatisticaPartidaRepository = estatisticaPartidaRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Uma posição do ranking final: quem jogou, quantos pontos fez e quantas
     * respostas suspeitas de burla acumulou. É o placar do fim da partida SEM o
     * status da rodada (que só faz sentido com o jogo em andamento).
     */
    public record RankingEntrada(String login, String nome, int pontos, int alertas) {}

    /**
     * O que a tela de estatísticas recebe: quando a partida encerrou, quantas
     * palavras teve, o ranking final e o relatório de cada palavra.
     */
    public record EstatisticasPartida(
        Instant dataEncerramento,
        int totalPalavras,
        List<RankingEntrada> ranking,
        List<JogoSalaService.RelatorioPalavra> relatorio
    ) {}

    // Formato gravado na coluna dados (JSON). ignoreUnknown: snapshot antigo com
    // campos que a versão atual já não conhece continua sendo lido, em vez de
    // derrubar a tela de estatísticas inteira.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DadosPartida(List<RankingEntrada> ranking, List<JogoSalaService.RelatorioPalavra> relatorio) {}

    /**
     * Grava (ou substitui) o snapshot da sala. Chamado pelo JogoSalaService no
     * encerramento da partida.
     *
     * Estatística é acessória: uma falha aqui não pode derrubar o encerramento
     * da partida, então o erro é apenas logado - os alunos continuam vendo o
     * placar final normalmente. REQUIRES_NEW pelo mesmo motivo: chamada de dentro
     * do PATCH que fecha a sala, um erro ao gravar marcaria aquela transação para
     * rollback e o professor não conseguiria fechar a sala.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvar(
        String codigoSala,
        int totalPalavras,
        List<RankingEntrada> ranking,
        List<JogoSalaService.RelatorioPalavra> relatorio
    ) {
        try {
            EstatisticaPartida registro = estatisticaPartidaRepository.findBySalaCodigo(codigoSala).orElseGet(EstatisticaPartida::new);
            registro.setSalaCodigo(codigoSala);
            registro.setDataEncerramento(Instant.now());
            registro.setTotalPalavras(totalPalavras);
            registro.setDados(objectMapper.writeValueAsString(new DadosPartida(ranking, relatorio)));
            estatisticaPartidaRepository.save(registro);
        } catch (Exception e) {
            LOG.error("Falha ao salvar as estatísticas da sala {}: {}", codigoSala, e.getMessage(), e);
        }
    }

    /**
     * Estatísticas da última partida da sala, ou vazio se ela nunca chegou ao
     * fim de uma partida (ou o snapshot ficou ilegível).
     */
    @Transactional(readOnly = true)
    public Optional<EstatisticasPartida> buscar(String codigoSala) {
        return estatisticaPartidaRepository
            .findBySalaCodigo(codigoSala)
            .map(registro -> {
                try {
                    DadosPartida dados = objectMapper.readValue(registro.getDados(), new TypeReference<DadosPartida>() {});
                    return new EstatisticasPartida(
                        registro.getDataEncerramento(),
                        registro.getTotalPalavras(),
                        dados.ranking() != null ? dados.ranking() : List.of(),
                        dados.relatorio() != null ? dados.relatorio() : List.of()
                    );
                } catch (Exception e) {
                    LOG.error("Snapshot de estatísticas ilegível na sala {}: {}", codigoSala, e.getMessage(), e);
                    return null;
                }
            });
    }

    // Quais das salas informadas já têm estatísticas gravadas - a lista "Minhas
    // Salas" usa para mostrar o botão só onde há o que ver
    @Transactional(readOnly = true)
    public Set<String> salasComEstatisticas(Collection<String> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(estatisticaPartidaRepository.findSalaCodigosIn(codigos));
    }
}
