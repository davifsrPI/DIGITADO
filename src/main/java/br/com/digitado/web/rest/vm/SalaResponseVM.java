package br.com.digitado.web.rest.vm;

import com.fasterxml.jackson.annotation.JsonRawValue;

// ViewModel retornado ao criar/listar salas — expõe apenas os campos públicos, sem dados do professor.
// descricao: String JSON crua ({"descricao": texto, "modo": "1v1"|"normal"}) emitida como objeto na resposta.
// jogadores: quantos estão conectados agora (usado na lista de duelos 1v1 públicos).
public record SalaResponseVM(
    String codigo,
    String nome,
    @JsonRawValue String descricao,
    Boolean ativo,
    String tipo,
    Boolean privada,
    int jogadores
) {}
