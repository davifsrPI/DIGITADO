package br.com.digitado.web.rest.vm;

// ViewModel retornado ao criar uma sala — expõe apenas os campos públicos, sem dados do professor
public record SalaResponseVM(Long id, String nome, String codigo, String descricao, Boolean ativo) {}
