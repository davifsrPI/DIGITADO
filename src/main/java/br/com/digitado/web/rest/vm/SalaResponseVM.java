package br.com.digitado.web.rest.vm;

// ViewModel retornado ao criar uma sala — expõe apenas os campos públicos, sem dados do professor
public record SalaResponseVM(String codigo, String nome, String descricao, Boolean ativo) {}
