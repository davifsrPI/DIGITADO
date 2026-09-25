package br.com.digitado.web.websocket.dto;

/**
 * Feedback privado enviado ao aluno depois que ele responde: se acertou, quantos
 * pontos ganhou, em que posição entre os ACERTOS chegou e qual o texto correto da
 * palavra (para mostrar no caso de erro).
 *
 * registrada: a resposta foi realmente contabilizada. Quando chega fora do tempo da
 * rodada (ou com a rodada já pausada/encerrada) o servidor a recusa, e é preciso
 * dizer isso ao aluno - antes a mensagem simplesmente não vinha, a tela ficava com o
 * resultado da conferência local e o aluno via "você acertou" sem ter ganho ponto
 * nenhum. Numa resposta recusada, pontos e ordem vêm zerados.
 */
public record FeedbackAluno(boolean correta, int pontos, int ordem, String tipoErro, String textoCorreto, boolean registrada) {}
