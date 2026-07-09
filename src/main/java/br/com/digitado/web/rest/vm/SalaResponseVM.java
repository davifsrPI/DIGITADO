package br.com.digitado.web.rest.vm;

// ViewModel retornado ao criar/listar salas — expõe apenas os campos públicos, sem dados do professor.
// jogadores: quantos estão conectados agora (usado na lista de duelos 1v1 públicos).
public record SalaResponseVM(String codigo, String nome, String descricao, Boolean ativo, String tipo, Boolean privada, int jogadores) {}
