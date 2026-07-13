package br.com.digitado.web.websocket.dto;

import java.util.List;

// Configuração enviada pelo professor ao iniciar o jogo: tempo de cada rodada
// POR DIFICULDADE (fácil/médio/difícil), quantas palavras de cada dificuldade
// sortear, IDs de palavras extras adicionadas manualmente e, opcionalmente, os
// IDs das palavras JÁ SORTEADAS na tela de criação da sala (palavrasIds) — quando
// presentes, a primeira partida usa exatamente essas palavras em vez de sortear
public record IniciarPayload(
    int tempoFacil,
    int tempoMedio,
    int tempoDificil,
    int qtdFacil,
    int qtdMedio,
    int qtdDificil,
    List<Long> palavrasExtrasIds,
    List<Long> palavrasIds
) {}
