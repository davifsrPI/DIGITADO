package br.com.digitado.web.websocket.dto;

import java.util.List;

// DTO enviado via WebSocket para todos os participantes da sala a cada evento do jogo.
// Contém o estado completo: tipo do evento, palavra atual, placar, alunos conectados e progresso.
public record EstadoJogoDTO(
    String tipo,
    PalavraDTO palavraAtual,
    int indiceAtual,
    int totalPalavras,
    int tempoLimite,
    long timestampInicio,
    List<PlacarEntry> placar,
    String nomeSala,
    String codigoSala,
    List<EntradaAluno> alunosConectados
) {}
