package br.com.digitado.web.websocket.dto;

// Entrada do placar de um participante: identifica o aluno, sua pontuação acumulada
// e o status na rodada atual (AGUARDANDO, ACERTOU ou ERROU)
public record PlacarEntry(String login, String nome, int pontos, String statusAtual) {}
