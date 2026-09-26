package br.com.digitado.service;

import br.com.digitado.domain.Palavra;
import br.com.digitado.domain.Sala;
import br.com.digitado.repository.PalavraRepository;
import br.com.digitado.repository.SalaRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda na sala a configuração da partida: tempo e quantidade por dificuldade,
 * as palavras extras escolhidas a mão e as palavras já sorteadas na criação.
 *
 * Isso vivia só no cliente - viajava no estado de navegação do React Router, com
 * uma cópia no sessionStorage para os duelos. Recarregar a tela de espera
 * descartava em silêncio a lista de palavras que o professor havia conferido uma
 * por uma, e a partida começava com a configuração PADRÃO, sorteando palavras
 * novas. No duelo, abrir o link em outro aparelho dava no mesmo.
 *
 * Os números que chegam são SEMPRE limitados aqui (ver normalizar): o payload vem
 * do navegador, e um tempo de rodada negativo ou 5.000 palavras não podem virar
 * uma partida.
 */
@Service
public class ConfiguracaoPartidaService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracaoPartidaService.class);

    // Mesmos limites dos controles da tela: sliders de 10 a 60 segundos e
    // steppers de 0 a 30 palavras por dificuldade
    private static final int TEMPO_MIN = 10;
    private static final int TEMPO_MAX = 60;
    private static final int QTD_MAX = 30;

    // Teto das listas de ids. Uma partida não tem centenas de palavras escolhidas
    // a dedo, e sem teto o payload viraria um jeito de encher a coluna
    private static final int MAX_IDS = 120;

    private final SalaRepository salaRepository;
    private final PalavraRepository palavraRepository;
    private final ObjectMapper objectMapper;

    public ConfiguracaoPartidaService(SalaRepository salaRepository, PalavraRepository palavraRepository, ObjectMapper objectMapper) {
        this.salaRepository = salaRepository;
        this.palavraRepository = palavraRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * A configuração como ela é gravada e devolvida.
     *
     * palavrasIds são as palavras que a partida vai usar - ou seja, as RESPOSTAS.
     * Por isso a configuração nunca entra na listagem pública de salas e só é
     * devolvida ao dono (ver SalaResource.getConfiguracao).
     *
     * ignoreUnknown: configuração gravada por uma versão anterior, com campos que
     * esta já não conhece, continua sendo lida em vez de derrubar a tela.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfiguracaoPartida(
        int tempoFacil,
        int tempoMedio,
        int tempoDificil,
        int qtdFacil,
        int qtdMedio,
        int qtdDificil,
        List<Long> palavrasExtrasIds,
        List<Long> palavrasIds
    ) {}

    /** Configuração padrão - a mesma que a tela do professor mostra ao abrir. */
    public static ConfiguracaoPartida padrao() {
        return new ConfiguracaoPartida(20, 30, 45, 5, 5, 5, List.of(), List.of());
    }

    /**
     * Traz os valores para dentro dos limites aceitáveis. O payload vem do
     * navegador: tempo fora da faixa dos sliders, quantidade negativa ou lista
     * gigante de ids são recortados aqui, antes de virarem uma partida.
     */
    public ConfiguracaoPartida normalizar(ConfiguracaoPartida cfg) {
        if (cfg == null) {
            return padrao();
        }
        return new ConfiguracaoPartida(
            entre(cfg.tempoFacil(), TEMPO_MIN, TEMPO_MAX),
            entre(cfg.tempoMedio(), TEMPO_MIN, TEMPO_MAX),
            entre(cfg.tempoDificil(), TEMPO_MIN, TEMPO_MAX),
            entre(cfg.qtdFacil(), 0, QTD_MAX),
            entre(cfg.qtdMedio(), 0, QTD_MAX),
            entre(cfg.qtdDificil(), 0, QTD_MAX),
            ids(cfg.palavrasExtrasIds()),
            ids(cfg.palavrasIds())
        );
    }

    /** Serializa para a coluna json da sala, já normalizada. */
    public String serializar(ConfiguracaoPartida cfg) {
        try {
            return objectMapper.writeValueAsString(normalizar(cfg));
        } catch (Exception e) {
            LOG.error("Falha ao serializar a configuração da partida: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Lê a coluna json; vazio quando não há nada gravado ou o conteúdo é ilegível. */
    public Optional<ConfiguracaoPartida> ler(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(normalizar(objectMapper.readValue(json, ConfiguracaoPartida.class)));
        } catch (Exception e) {
            LOG.error("Configuração de partida ilegível: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** Configuração gravada nesta sala. */
    @Transactional(readOnly = true)
    public Optional<ConfiguracaoPartida> buscar(String codigoSala) {
        return salaRepository.findById(codigoSala).map(Sala::getConfiguracao).flatMap(this::ler);
    }

    /**
     * Grava a configuração na sala.
     *
     * Chamado na criação e de novo ao INICIAR a partida: os ajustes que o
     * professor faz na tela de espera passam a ser o que a sala tem gravado, então
     * recarregar a página devolve o que ele escolheu, não o padrão.
     *
     * Falha aqui é registrada e engolida: perder a configuração gravada atrasa o
     * professor, mas não pode impedir a partida de começar.
     */
    @Transactional
    public void salvar(String codigoSala, ConfiguracaoPartida cfg) {
        try {
            String json = serializar(cfg);
            if (json == null) {
                return;
            }
            salaRepository
                .findById(codigoSala)
                .ifPresent(sala -> {
                    sala.setConfiguracao(json);
                    salaRepository.save(sala);
                });
        } catch (Exception e) {
            LOG.error("Falha ao gravar a configuração da sala {}: {}", codigoSala, e.getMessage(), e);
        }
    }

    /** Uma palavra da atividade, como a tela do professor a mostra. */
    public record PalavraDaSala(Long id, String texto, String dificuldade) {}

    /**
     * As palavras que esta sala tem guardadas: as SORTEADAS na criação e as que o
     * professor escolheu a mão no acervo.
     *
     * A configuração guarda só os ids. Quem preparou a sala dias antes não tem como
     * lembrar quais palavras caíram no sorteio, e sem esta lista a única forma de
     * conferir era começar a partida. Restrita ao dono da sala pelo mesmo motivo da
     * configuração: são as respostas do ditado.
     */
    public record PalavrasDaSala(List<PalavraDaSala> sorteadas, List<PalavraDaSala> extras) {}

    @Transactional(readOnly = true)
    public PalavrasDaSala palavras(String codigoSala) {
        ConfiguracaoPartida cfg = buscar(codigoSala).orElseGet(ConfiguracaoPartidaService::padrao);
        return new PalavrasDaSala(resolver(cfg.palavrasIds()), resolver(cfg.palavrasExtrasIds()));
    }

    /**
     * Ids para palavras, na ORDEM em que foram gravados - é a ordem que o professor
     * viu na tela de criação. Id que não existe mais no acervo simplesmente sai da
     * lista, como já acontece quando a partida começa.
     */
    private List<PalavraDaSala> resolver(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Palavra> porId = palavraRepository
            .findAllById(ids)
            .stream()
            .collect(Collectors.toMap(Palavra::getId, Function.identity(), (a, b) -> a));
        return ids
            .stream()
            .map(porId::get)
            .filter(Objects::nonNull)
            .map(p -> new PalavraDaSala(p.getId(), p.getTexto(), p.getDificuldade() == null ? null : p.getDificuldade().name()))
            .toList();
    }

    private static int entre(int valor, int min, int max) {
        return Math.max(min, Math.min(max, valor));
    }

    // Lista de ids limpa: sem nulos, sem repetidos e com teto de tamanho
    private static List<Long> ids(List<Long> valores) {
        if (valores == null || valores.isEmpty()) {
            return List.of();
        }
        return valores.stream().filter(java.util.Objects::nonNull).distinct().limit(MAX_IDS).toList();
    }
}
