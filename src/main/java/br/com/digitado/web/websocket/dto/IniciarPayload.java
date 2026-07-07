package br.com.digitado.web.websocket.dto;

import java.util.List;

// Configuração enviada pelo professor ao iniciar o jogo: tempo de cada rodada
// POR DIFICULDADE (fácil/médio/difícil), quantas palavras de cada dificuldade
// sortear e IDs de palavras extras adicionadas manualmente
public record IniciarPayload(
    int tempoFacil,
    int tempoMedio,
    int tempoDificil,
    int qtdFacil,
    int qtdMedio,
    int qtdDificil,
    List<Long> palavrasExtrasIds
) {}
