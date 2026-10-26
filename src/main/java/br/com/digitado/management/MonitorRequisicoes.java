package br.com.digitado.management;

import java.util.List;
import java.util.Map;

/**
 * Uma leitura de /management/requisicoes: as requisições HTTP agrupadas por API
 * (método + rota) e os últimos erros. Os contadores são acumulados desde que o
 * servidor subiu; quem mostra req/s é o painel, pela diferença entre duas leituras.
 *
 * @param instante quando a leitura foi feita (epoch ms), a base das taxas
 * @param rotas cada API, da mais chamada para a menos chamada
 * @param errosRecentes os últimos erros, do mais novo para o mais antigo
 * @param errosRegistrados quantos erros houve desde que o servidor subiu
 */
public record MonitorRequisicoes(long instante, List<Rota> rotas, List<ErroHttp> errosRecentes, long errosRegistrados) {
    /**
     * Uma API: método e rota como o Spring os conhece ("/api/salas/{codigo}"), não o
     * caminho com o id de cada chamada.
     *
     * @param maximoMs a requisição mais lenta dos últimos minutos (o Micrometer
     *        esquece o máximo depois de uma janela de tempo)
     * @param porStatus quantas respostas de cada status ("200", "404"...)
     * @param excecoes quantas vezes cada exceção apareceu (nome simples da classe)
     */
    public record Rota(
        String metodo,
        String uri,
        long requisicoes,
        double tempoTotalMs,
        double maximoMs,
        long sucesso,
        long erros4xx,
        long erros5xx,
        Map<String, Long> porStatus,
        Map<String, Long> excecoes
    ) {}

    /**
     * Um erro, com o detalhe que dá para mostrar: a rota (padrão) e o caminho de
     * verdade, o status, quanto demorou, a exceção, a causa raiz e o começo da
     * pilha. Sem query string, corpo nem cabeçalhos.
     *
     * @param rota o padrão da rota; nulo quando a requisição nem chegou a uma
     *        (401/403 do filtro de segurança, por exemplo)
     */
    public record ErroHttp(
        long instante,
        String metodo,
        String rota,
        String caminho,
        int status,
        Double duracaoMs,
        String excecao,
        String mensagem,
        String causaRaiz,
        List<String> pilha
    ) {}
}
