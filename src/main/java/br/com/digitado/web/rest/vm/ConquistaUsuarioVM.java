package br.com.digitado.web.rest.vm;

import java.time.Instant;

// ViewModel de uma conquista vista pelo usuário logado (estilo Steam):
// junta os dados do catálogo (nome, descrição, XP) com o estado do usuário
// (desbloqueada ou não, progresso e data em que foi desbloqueada)
public record ConquistaUsuarioVM(
    Long id,
    String nome,
    String descricao,
    Integer xpRecompensa,
    boolean desbloqueada,
    Integer progresso,
    Instant dataConquista
) {}
