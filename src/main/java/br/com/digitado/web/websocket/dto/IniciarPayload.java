package br.com.digitado.web.websocket.dto;

import java.util.List;

// Configuração enviada pelo professor ao iniciar o jogo: tempo de cada rodada,
// quantas palavras de cada dificuldade sortear e IDs de palavras extras adicionadas manualmente
public record IniciarPayload(int tempoLimite, int qtdFacil, int qtdMedio, int qtdDificil, List<Long> palavrasExtrasIds) {}
