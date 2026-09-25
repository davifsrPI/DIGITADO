package br.com.digitado.web.websocket.dto;

/**
 * Aviso LEVE de que alguém respondeu a palavra da rodada.
 *
 * Substitui, durante a rodada, o envio do estado inteiro da sala a cada resposta.
 * Numa turma de 40 aquele estado tinha 5 KB e saía 40 vezes por resposta (uma para
 * cada aparelho): 1600 mensagens e cerca de 8 MB por palavra, e cada mensagem
 * redesenhava a tela de todo mundo. Este aviso tem algumas dezenas de bytes e vai
 * só para quem precisa dele durante a rodada - o professor, que acompanha quantos
 * já responderam.
 *
 * indiceAtual: a rodada a que a resposta pertence. Um aviso atrasado, de uma palavra
 * que já passou, é descartado pelo cliente em vez de sujar a contagem da rodada nova.
 *
 * totalRespostas/totalAcertos vêm prontos do servidor em vez de o cliente somar o que
 * recebeu: um aviso perdido no caminho não desencontra o painel do professor.
 */
public record RespostaRodada(int indiceAtual, String login, String statusAtual, int totalRespostas, int totalAcertos) {}
