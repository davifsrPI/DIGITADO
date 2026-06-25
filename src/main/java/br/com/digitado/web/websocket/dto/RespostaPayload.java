package br.com.digitado.web.websocket.dto;

// Payload enviado pelo aluno (ou professor) ao submeter a resposta de uma palavra
public record RespostaPayload(String respostaDigitada) {}
