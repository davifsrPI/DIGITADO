package br.com.digitado.web.websocket.dto;

// Entrada do placar de um participante: identifica o aluno, sua pontuação acumulada,
// o status na rodada atual (AGUARDANDO, ACERTOU ou ERROU) e o total de respostas
// suspeitas de burla na partida (exibido apenas na tela do professor)
public record PlacarEntry(String login, String nome, int pontos, String statusAtual, int alertas) {}
