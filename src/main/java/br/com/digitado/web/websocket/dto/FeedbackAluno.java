package br.com.digitado.web.websocket.dto;

public record FeedbackAluno(boolean correta, int pontos, int ordem, String tipoErro, String textoCorreto) {}
