package br.com.digitado.web.websocket.dto;

// Feedback privado enviado ao aluno após ele responder: se acertou, quantos pontos ganhou,
// em qual posição chegou e qual o texto correto da palavra (para mostrar no caso de erro)
public record FeedbackAluno(boolean correta, int pontos, int ordem, String tipoErro, String textoCorreto) {}
