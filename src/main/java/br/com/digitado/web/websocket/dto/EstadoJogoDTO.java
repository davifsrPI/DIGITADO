package br.com.digitado.web.websocket.dto;

import java.util.List;

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
