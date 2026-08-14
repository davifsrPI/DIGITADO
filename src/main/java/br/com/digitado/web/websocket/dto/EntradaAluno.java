package br.com.digitado.web.websocket.dto;

// Payload enviado pelo cliente ao entrar na sala - identifica o participante pelo login e nome de exibição
public record EntradaAluno(String login, String nome) {}
