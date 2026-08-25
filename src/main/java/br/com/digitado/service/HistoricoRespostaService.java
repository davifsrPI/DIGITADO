package br.com.digitado.service;

import br.com.digitado.domain.HistoricoResposta;
import br.com.digitado.domain.Palavra;
import br.com.digitado.repository.HistoricoRespostaRepository;
import br.com.digitado.web.rest.vm.DesempenhoVM.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Histórico de respostas por jogador: a gravação de cada resposta e os dois
 * painéis que saem dela - o do titular ("Meu Desempenho") e o agregado do admin.
 *
 * GRAVAÇÃO: chamada dos dois pontos onde uma resposta é validada no servidor -
 * JogoSalaService.responder (partidas e duelos) e PalavraDoDiaService.tentar.
 * Só jogador logado gera linha; visitante anônimo não tem histórico.
 *
 * LEITURA: toda agregação acontece no banco (ver HistoricoRespostaRepository).
 * Aqui só se converte Object[] em VM e se aplicam as regras de classificação -
 * o frontend não calcula nada, apenas desenha.
 */
@Service
public class HistoricoRespostaService {

    private static final Logger LOG = LoggerFactory.getLogger(HistoricoRespostaService.class);

    /**
     * Faixas do "acelerador". Abaixo de MEDIO a pessoa erra mais do que acerta;
     * de BOM para cima acerta 3 em cada 4. São os mesmos limites do rótulo que o
     * admin vê na lista de jogadores, então as duas telas contam a mesma história.
     */
    private static final int LIMITE_BOM = 75;
    private static final int LIMITE_MEDIO = 50;

    /**
     * Quantas respostas o painel considera "recente" - é a janela que move o
     * ponteiro do acelerador. Pequena de propósito: o acelerador responde "como
     * estou HOJE", não "como fui a vida toda" (isso é a taxa geral).
     */
    private static final int JANELA_RECENTE = 30;

    /** Janela maior, usada só para medir sequências de acerto sem varrer tudo. */
    private static final int JANELA_SEQUENCIA = 300;

    /** Quantas respostas antes de o acelerador valer alguma coisa. */
    private static final int MINIMO_PARA_NIVEL = 10;

    /** Quantas palavras cada ranking mostra: 10 no painel pessoal, 5 no do admin. */
    private static final int TOP_PALAVRAS = 10;
    private static final int TOP_PALAVRAS_ADMIN = 5;

    private static final int MESES_EVOLUCAO_ADMIN = 12;

    /**
     * Quantos pontos percentuais uma variação precisa ter para contar como
     * melhora ou piora - abaixo disso é ruído, não tendência. Vale tanto para o
     * comparativo entre meses quanto para a evolução individual de cada aluno.
     */
    private static final int VARIACAO_RELEVANTE = 3;

    /**
     * Quantas respostas uma palavra precisa ter, somando toda a base, para entrar
     * no ranking global de mais erradas. Sem isso, uma palavra respondida uma
     * única vez (e errada) lideraria a lista com 100% de erro.
     */
    private static final int MIN_RESPOSTAS_PALAVRA_GLOBAL = 5;

    private final HistoricoRespostaRepository historicoRepository;

    public HistoricoRespostaService(HistoricoRespostaRepository historicoRepository) {
        this.historicoRepository = historicoRepository;
    }

    // ===== Gravação =====

    /**
     * Registra uma resposta no histórico do jogador.
     *
     * Nunca pode derrubar a partida nem a palavra do dia: login nulo (visitante)
     * é ignorado em silêncio e qualquer falha de banco é apenas logada - o jogo
     * segue normalmente, só aquele ponto do histórico se perde.
     */
    @Transactional
    public void registrar(
        String login,
        Palavra palavra,
        boolean correta,
        String tipoErro,
        Integer tempoMs,
        HistoricoResposta.Origem origem
    ) {
        if (login == null || login.isBlank() || palavra == null || palavra.getId() == null) {
            return;
        }
        try {
            HistoricoResposta registro = new HistoricoResposta();
            registro.setLogin(login);
            registro.setPalavraId(palavra.getId());
            registro.setCorreta(correta);
            // Dificuldade EFETIVA agora - ela é calculada e muda com o tempo
            registro.setDificuldade(palavra.getDificuldade() != null ? palavra.getDificuldade().name() : null);
            registro.setOrigem(origem);
            registro.setTipoErro(correta ? null : tipoErro);
            registro.setTempoMs(tempoMs);
            registro.setDataResposta(Instant.now());
            historicoRepository.save(registro);
        } catch (Exception e) {
            LOG.error("Falha ao registrar histórico de resposta de {}: {}", login, e.getMessage(), e);
        }
    }

    // ===== Painel do titular =====

    /**
     * Monta o painel de desempenho do jogador informado.
     *
     * O login vem SEMPRE do token no controller - este método não é exposto de
     * forma que alguém possa pedir o painel de outra pessoa.
     */
    @Transactional(readOnly = true)
    public MeuDesempenhoVM meuDesempenho(String login) {
        Object[] resumo = primeiraLinha(historicoRepository.resumoDoUsuario(login));
        long total = numero(resumo, 0);
        long acertos = numero(resumo, 1);
        Instant primeira = instante(resumo, 2);
        Instant ultima = instante(resumo, 3);

        // Sem histórico ainda: devolve o painel vazio em vez de 404 - a tela mostra
        // o convite para jogar, e não uma mensagem de erro
        if (total == 0) {
            return new MeuDesempenhoVM(
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                Nivel.MEDIO,
                "Você ainda não respondeu nenhuma palavra. Jogue uma partida ou a palavra do dia para começar a medir sua evolução.",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
            );
        }

        int taxaGeral = percentual(acertos, total);

        // Uma consulta só alimenta o ponteiro (as 30 mais novas) e as sequências
        List<Object> recentes = historicoRepository.ultimasRespostas(login, JANELA_SEQUENCIA);
        int taxaRecente = taxaDaJanela(recentes, JANELA_RECENTE, taxaGeral);
        int sequenciaAtual = sequenciaInicial(recentes);
        int melhorSequencia = maiorSequencia(recentes);

        Nivel nivel = classificar(taxaRecente);

        List<FaixaDificuldadeVM> porDificuldade = historicoRepository
            .porDificuldade(login)
            .stream()
            .map(l -> {
                long t = numero(l, 1);
                long a = numero(l, 2);
                return new FaixaDificuldadeVM(texto(l, 0), t, a, percentual(a, t));
            })
            .sorted(Comparator.comparingInt(f -> ordemDificuldade(f.dificuldade())))
            .toList();

        List<PontoEvolucaoVM> evolucao = historicoRepository
            .evolucaoMensal(login)
            .stream()
            .map(l -> {
                long t = numero(l, 1);
                long a = numero(l, 2);
                return new PontoEvolucaoVM(texto(l, 0), t, a, percentual(a, t), 0);
            })
            .toList();

        List<PalavraDesempenhoVM> maisErradas = historicoRepository
            .maisErradas(login, TOP_PALAVRAS)
            .stream()
            .map(l -> {
                long t = numero(l, 3);
                long erros = numero(l, 4);
                return new PalavraDesempenhoVM(id(l, 0), texto(l, 1), texto(l, 2), t, t - erros, erros, percentual(erros, t));
            })
            .toList();

        List<PalavraDesempenhoVM> maisAcertadas = historicoRepository
            .maisAcertadas(login, TOP_PALAVRAS)
            .stream()
            .map(l -> {
                long t = numero(l, 3);
                long a = numero(l, 4);
                return new PalavraDesempenhoVM(id(l, 0), texto(l, 1), texto(l, 2), t, a, t - a, percentual(a, t));
            })
            .toList();

        long totalErros = total - acertos;
        List<TipoErroVM> errosPorTipo = historicoRepository
            .errosPorTipo(login)
            .stream()
            .map(l -> new TipoErroVM(texto(l, 0), numero(l, 1), percentual(numero(l, 1), totalErros)))
            .toList();

        return new MeuDesempenhoVM(
            primeira,
            ultima,
            total,
            acertos,
            totalErros,
            taxaGeral,
            taxaRecente,
            taxaRecente - taxaGeral,
            nivel,
            mensagemDoNivel(nivel, taxaRecente, taxaRecente - taxaGeral, total),
            sequenciaAtual,
            melhorSequencia,
            porDificuldade,
            evolucao,
            maisErradas,
            maisAcertadas,
            errosPorTipo
        );
    }

    // ===== Painel do administrador =====

    /**
     * Retrato agregado da TURMA, com o comparativo do mês.
     *
     * Duas regras moldam este painel:
     * - as respostas de quem tem ROLE_ADMIN ficam fora de todos os números
     *   (o filtro está em HistoricoRespostaRepository.SO_ALUNOS): o admin joga
     *   para testar o sistema e isso não é desempenho de turma;
     * - nada é individual. Só médias, contagens e o ranking de palavras - o
     *   painel responde "a turma está evoluindo?", não "quem evoluiu quanto".
     */
    @Transactional(readOnly = true)
    public DesempenhoGeralVM desempenhoGeral() {
        Object[] resumo = primeiraLinha(historicoRepository.resumoGlobal());
        long total = numero(resumo, 0);
        long acertos = numero(resumo, 1);
        long alunos = numero(resumo, 2);
        long totalErros = total - acertos;

        Instant desde = YearMonth.now(ZoneId.systemDefault())
            .minusMonths(MESES_EVOLUCAO_ADMIN - 1L)
            .atDay(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant();

        List<PontoEvolucaoVM> evolucao = historicoRepository
            .evolucaoMensalGlobal(desde)
            .stream()
            .map(l -> {
                long t = numero(l, 1);
                long a = numero(l, 2);
                return new PontoEvolucaoVM(texto(l, 0), t, a, percentual(a, t), numero(l, 3));
            })
            .toList();

        List<PalavraDesempenhoVM> palavrasMaisErradas = historicoRepository
            .maisErradasGlobal(MIN_RESPOSTAS_PALAVRA_GLOBAL, TOP_PALAVRAS_ADMIN)
            .stream()
            .map(l -> {
                long t = numero(l, 3);
                long erros = numero(l, 4);
                return new PalavraDesempenhoVM(id(l, 0), texto(l, 1), texto(l, 2), t, t - erros, erros, percentual(erros, t));
            })
            .toList();

        List<PalavraDesempenhoVM> palavrasMaisAcertadas = historicoRepository
            .maisAcertadasGlobal(MIN_RESPOSTAS_PALAVRA_GLOBAL, TOP_PALAVRAS_ADMIN)
            .stream()
            .map(l -> {
                long t = numero(l, 3);
                long a = numero(l, 4);
                return new PalavraDesempenhoVM(id(l, 0), texto(l, 1), texto(l, 2), t, a, t - a, percentual(a, t));
            })
            .toList();

        List<TipoErroVM> errosPorTipo = historicoRepository
            .errosPorTipoGlobal()
            .stream()
            .map(l -> new TipoErroVM(texto(l, 0), numero(l, 1), percentual(numero(l, 1), totalErros)))
            .toList();

        // Uma consulta só alimenta a média de desenvolvimento E a distribuição
        // por faixa - as duas leem a taxa ATUAL de cada aluno (segunda metade do
        // histórico dele), então as duas seções do painel contam a mesma história
        List<Object[]> porAluno = historicoRepository.evolucaoPorAluno(MINIMO_PARA_NIVEL);
        DesenvolvimentoVM desenvolvimento = montarDesenvolvimento(porAluno);

        long bons = contarNaFaixa(porAluno, Nivel.BOM);
        long medios = contarNaFaixa(porAluno, Nivel.MEDIO);
        long ruins = contarNaFaixa(porAluno, Nivel.RUIM);

        return new DesempenhoGeralVM(
            total,
            acertos,
            totalErros,
            percentual(acertos, total),
            alunos,
            bons,
            medios,
            ruins,
            desenvolvimento,
            montarRelatorioMensal(evolucao),
            evolucao,
            palavrasMaisAcertadas,
            palavrasMaisErradas,
            errosPorTipo
        );
    }

    // Quantos alunos estão na faixa, pela taxa ATUAL deles (coluna 2 = segunda metade)
    private long contarNaFaixa(List<Object[]> porAluno, Nivel faixa) {
        return porAluno.stream().filter(l -> classificar((int) Math.round(decimal(l, 2))) == faixa).count();
    }

    /**
     * Quanto a turma se desenvolveu, em média.
     *
     * Para cada aluno com histórico suficiente o banco devolve a taxa da PRIMEIRA
     * metade e a da SEGUNDA metade das respostas dele; a evolução individual é a
     * diferença entre as duas, e o que sai daqui é a média dessas diferenças mais
     * a contagem de quem melhorou, piorou e ficou estável.
     *
     * Metades em vez de datas fixas de propósito: os alunos começam em momentos
     * diferentes, e comparar "março x agosto" puniria quem entrou em julho.
     */
    private DesenvolvimentoVM montarDesenvolvimento(List<Object[]> linhas) {
        if (linhas.isEmpty()) {
            return new DesenvolvimentoVM(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                String.format(
                    "Nenhum aluno chegou a %d respostas ainda - a média de desenvolvimento aparece quando houver histórico suficiente.",
                    MINIMO_PARA_NIVEL
                )
            );
        }

        double somaInicial = 0;
        double somaAtual = 0;
        long melhoraram = 0;
        long estaveis = 0;
        long pioraram = 0;
        for (Object[] linha : linhas) {
            double inicial = decimal(linha, 1);
            double atual = decimal(linha, 2);
            somaInicial += inicial;
            somaAtual += atual;
            double variacao = atual - inicial;
            if (variacao >= VARIACAO_RELEVANTE) {
                melhoraram++;
            } else if (variacao <= -VARIACAO_RELEVANTE) {
                pioraram++;
            } else {
                estaveis++;
            }
        }

        int mediaInicial = (int) Math.round(somaInicial / linhas.size());
        int mediaAtual = (int) Math.round(somaAtual / linhas.size());
        int mediaEvolucao = mediaAtual - mediaInicial;

        String resumo;
        if (mediaEvolucao >= VARIACAO_RELEVANTE) {
            resumo = String.format(
                "Os alunos evoluíram em média %+d pontos: começaram acertando %d%% e hoje acertam %d%%. %d melhoraram, %d pioraram e %d mantiveram o ritmo.",
                mediaEvolucao,
                mediaInicial,
                mediaAtual,
                melhoraram,
                pioraram,
                estaveis
            );
        } else if (mediaEvolucao <= -VARIACAO_RELEVANTE) {
            resumo = String.format(
                "Os alunos recuaram em média %d pontos: começaram acertando %d%% e hoje acertam %d%%. %d pioraram, %d melhoraram e %d mantiveram o ritmo.",
                mediaEvolucao,
                mediaInicial,
                mediaAtual,
                pioraram,
                melhoraram,
                estaveis
            );
        } else {
            resumo = String.format(
                "A turma está estável: a média saiu de %d%% para %d%% de acerto. %d melhoraram, %d pioraram e %d mantiveram o ritmo.",
                mediaInicial,
                mediaAtual,
                melhoraram,
                pioraram,
                estaveis
            );
        }

        return new DesenvolvimentoVM(linhas.size(), mediaInicial, mediaAtual, mediaEvolucao, melhoraram, estaveis, pioraram, resumo);
    }

    /**
     * O "relatório do mês", com as duas leituras que o painel precisa dar:
     *
     * 1) BALANÇO do mês corrente - houve mais acertos do que erros no total das
     *    palavras usadas?
     * 2) COMPARATIVO com o mês anterior - melhorou ou piorou?
     *
     * Mês sem nenhuma resposta não vem na evolução (o GROUP BY não inventa linha
     * vazia), então os dois meses são buscados pela chave 'YYYY-MM' e o que faltar
     * entra zerado - assim um mês parado aparece como queda de volume, e não some
     * do relatório.
     */
    private RelatorioMensalVM montarRelatorioMensal(List<PontoEvolucaoVM> evolucao) {
        Map<String, PontoEvolucaoVM> porMes = new LinkedHashMap<>();
        evolucao.forEach(p -> porMes.put(p.mes(), p));

        YearMonth atual = YearMonth.now(ZoneId.systemDefault());
        YearMonth anterior = atual.minusMonths(1);
        String chaveAtual = atual.toString();
        String chaveAnterior = anterior.toString();

        PontoEvolucaoVM mesAtual = porMes.getOrDefault(chaveAtual, new PontoEvolucaoVM(chaveAtual, 0, 0, 0, 0));
        PontoEvolucaoVM mesAnterior = porMes.getOrDefault(chaveAnterior, new PontoEvolucaoVM(chaveAnterior, 0, 0, 0, 0));

        long errosAtual = mesAtual.total() - mesAtual.acertos();
        long errosAnterior = mesAnterior.total() - mesAnterior.acertos();
        boolean maisAcertosQueErros = mesAtual.acertos() > errosAtual;

        String balanco;
        if (mesAtual.total() == 0) {
            balanco = "Nenhuma palavra foi respondida neste mês ainda.";
        } else if (maisAcertosQueErros) {
            balanco = String.format(
                "Mais ACERTOS do que erros: %d acertos contra %d erros nas %d palavras respondidas no mês.",
                mesAtual.acertos(),
                errosAtual,
                mesAtual.total()
            );
        } else if (mesAtual.acertos() == errosAtual) {
            balanco = String.format("Empate: %d acertos e %d erros nas palavras respondidas no mês.", mesAtual.acertos(), errosAtual);
        } else {
            balanco = String.format(
                "Mais ERROS do que acertos: %d erros contra %d acertos nas %d palavras respondidas no mês.",
                errosAtual,
                mesAtual.acertos(),
                mesAtual.total()
            );
        }

        int variacaoTaxa = mesAtual.taxa() - mesAnterior.taxa();
        long variacaoRespostas = mesAtual.total() - mesAnterior.total();

        String veredito;
        String resumo;
        if (mesAnterior.total() == 0 && mesAtual.total() == 0) {
            veredito = "SEM_DADOS";
            resumo = "Nenhuma resposta registrada neste mês nem no anterior.";
        } else if (mesAnterior.total() == 0) {
            veredito = "SEM_COMPARATIVO";
            resumo = String.format(
                "Primeiro mês com dados: %d respostas e %d%% de acerto. O comparativo com o mês anterior aparece no mês que vem.",
                mesAtual.total(),
                mesAtual.taxa()
            );
        } else if (variacaoTaxa >= VARIACAO_RELEVANTE) {
            veredito = "MELHOROU";
            resumo = String.format(
                "A turma acertou MAIS que no mês passado: %d%% contra %d%% (+%d pontos).",
                mesAtual.taxa(),
                mesAnterior.taxa(),
                variacaoTaxa
            );
        } else if (variacaoTaxa <= -VARIACAO_RELEVANTE) {
            veredito = "PIOROU";
            resumo = String.format(
                "A turma acertou MENOS que no mês passado: %d%% contra %d%% (%d pontos).",
                mesAtual.taxa(),
                mesAnterior.taxa(),
                variacaoTaxa
            );
        } else {
            veredito = "ESTAVEL";
            resumo = String.format(
                "Desempenho estável: %d%% de acerto neste mês contra %d%% no mês passado.",
                mesAtual.taxa(),
                mesAnterior.taxa()
            );
        }

        return new RelatorioMensalVM(
            chaveAtual,
            chaveAnterior,
            mesAtual.total(),
            mesAnterior.total(),
            mesAtual.acertos(),
            errosAtual,
            mesAnterior.acertos(),
            errosAnterior,
            mesAtual.taxa(),
            mesAnterior.taxa(),
            variacaoTaxa,
            variacaoRespostas,
            maisAcertosQueErros,
            balanco,
            veredito,
            resumo
        );
    }

    // ===== Regras e utilitários =====

    /** A faixa do acelerador para uma taxa de acerto. */
    public static Nivel classificar(int taxa) {
        if (taxa >= LIMITE_BOM) {
            return Nivel.BOM;
        }
        if (taxa >= LIMITE_MEDIO) {
            return Nivel.MEDIO;
        }
        return Nivel.RUIM;
    }

    // Texto que acompanha o ponteiro - fala da tendência, não só da faixa
    private String mensagemDoNivel(Nivel nivel, int taxaRecente, int tendencia, long total) {
        if (total < MINIMO_PARA_NIVEL) {
            return String.format("Você respondeu %d palavra(s) até agora - jogue mais um pouco para o acelerador ficar confiável.", total);
        }
        String base =
            switch (nivel) {
                case BOM -> String.format("Você está indo BEM: %d%% de acerto nas últimas respostas.", taxaRecente);
                case MEDIO -> String.format("Você está MEDIANO: %d%% de acerto nas últimas respostas.", taxaRecente);
                case RUIM -> String.format("Momento RUIM: %d%% de acerto nas últimas respostas.", taxaRecente);
            };
        if (tendencia >= 5) {
            return base + " E vem melhorando em relação à sua média de sempre.";
        }
        if (tendencia <= -5) {
            return base + " Está abaixo da sua média de sempre - vale revisar as palavras que mais erra.";
        }
        return base + " É praticamente a sua média de sempre.";
    }

    // Taxa de acerto das N respostas mais novas (a lista já vem da mais nova para a mais antiga)
    private int taxaDaJanela(List<Object> recentes, int janela, int padrao) {
        if (recentes.isEmpty()) {
            return padrao;
        }
        List<Object> recorte = recentes.subList(0, Math.min(janela, recentes.size()));
        long acertos = recorte.stream().filter(HistoricoRespostaService::acertou).count();
        return percentual(acertos, recorte.size());
    }

    // Quantos acertos seguidos a pessoa tem AGORA (a lista começa na resposta mais nova)
    private int sequenciaInicial(List<Object> recentes) {
        int seq = 0;
        for (Object valor : recentes) {
            if (!acertou(valor)) {
                break;
            }
            seq++;
        }
        return seq;
    }

    // Maior sequência de acertos dentro da janela consultada
    private int maiorSequencia(List<Object> recentes) {
        int melhor = 0;
        int atual = 0;
        for (Object valor : recentes) {
            atual = acertou(valor) ? atual + 1 : 0;
            melhor = Math.max(melhor, atual);
        }
        return melhor;
    }

    /**
     * O 1/0 que a consulta nativa projeta vira boolean aqui. Aceita Number e
     * Boolean: o tipo exato depende do driver e não vale amarrar a assinatura da
     * consulta a ele.
     */
    private static boolean acertou(Object valor) {
        if (valor instanceof Number numero) {
            return numero.intValue() == 1;
        }
        return Boolean.TRUE.equals(valor);
    }

    private static int percentual(long parte, long total) {
        return total <= 0 ? 0 : (int) Math.round((parte * 100.0) / total);
    }

    // Ordem de exibição das faixas (fácil -> difícil), qualquer outro valor no fim
    private static int ordemDificuldade(String dificuldade) {
        return switch (dificuldade == null ? "" : dificuldade) {
            case "FACIL" -> 0;
            case "MEDIO" -> 1;
            case "DIFICIL" -> 2;
            default -> 3;
        };
    }

    private static Object[] primeiraLinha(List<Object[]> linhas) {
        return linhas.isEmpty() ? new Object[0] : linhas.get(0);
    }

    /**
     * Coluna numérica com casas decimais (as médias de taxa vem como BigDecimal
     * do MySQL). numero() trunca com longValue e zeraria "66.7%" para 66 antes do
     * arredondamento - por isso as taxas passam por aqui.
     */
    private static double decimal(Object[] linha, int indice) {
        if (linha == null || indice >= linha.length || linha[indice] == null) {
            return 0;
        }
        return ((Number) linha[indice]).doubleValue();
    }

    private static long numero(Object[] linha, int indice) {
        if (linha == null || indice >= linha.length || linha[indice] == null) {
            return 0;
        }
        return ((Number) linha[indice]).longValue();
    }

    private static Long id(Object[] linha, int indice) {
        long valor = numero(linha, indice);
        return valor == 0 ? null : valor;
    }

    private static String texto(Object[] linha, int indice) {
        if (linha == null || indice >= linha.length || linha[indice] == null) {
            return null;
        }
        return String.valueOf(linha[indice]);
    }

    /**
     * Converte a coluna de data que o driver devolve. MySQL entrega
     * java.sql.Timestamp nas consultas nativas, mas a mesma coluna pode chegar
     * como Instant dependendo do dialeto - por isso os dois casos.
     */
    private static Instant instante(Object[] linha, int indice) {
        if (linha == null || indice >= linha.length || linha[indice] == null) {
            return null;
        }
        Object valor = linha[indice];
        if (valor instanceof Instant instant) {
            return instant;
        }
        if (valor instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (valor instanceof java.util.Date data) {
            return data.toInstant();
        }
        if (valor instanceof LocalDate localDate) {
            return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }
        return null;
    }
}
