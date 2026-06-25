package br.com.digitado.web.websocket.dto;

// Representação mínima da palavra enviada no estado do jogo — apenas os campos necessários para o frontend
public record PalavraDTO(Long id, String texto, String dificuldade, String categoria) {}
