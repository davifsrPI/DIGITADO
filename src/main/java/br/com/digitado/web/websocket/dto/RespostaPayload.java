package br.com.digitado.web.websocket.dto;

// Payload enviado pelo aluno (ou professor) ao submeter a resposta de uma palavra.
// tentativasBurla: inserções bloqueadas pelo cliente na rodada (colar, corretor) —
// meramente informativo, o servidor faz a própria checagem de plausibilidade
public record RespostaPayload(String respostaDigitada, Integer tentativasBurla) {}
