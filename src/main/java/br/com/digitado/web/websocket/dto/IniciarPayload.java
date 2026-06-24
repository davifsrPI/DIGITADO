package br.com.digitado.web.websocket.dto;

import java.util.List;

public record IniciarPayload(int tempoLimite, int qtdFacil, int qtdMedio, int qtdDificil, List<Long> palavrasExtrasIds) {}
