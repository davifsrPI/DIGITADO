package br.com.digitado.web.rest.vm;

import java.time.Instant;

// ViewModel de uma conquista vista pelo usuário logado (estilo Steam):
// junta os dados do catálogo (nome, descrição, XP) com o estado do usuário
// (desbloqueada ou não, progresso e data em que foi desbloqueada).
// meta: quanto o progresso precisa chegar (10 palavras, 5 vitórias...) - é com ela
// que a tela calcula a porcentagem; nula quando a conquista não tem regra no motor.
// percentual: progresso / meta, de 0 a 100, já calculado aqui para as duas telas
// (e os testes) contarem a mesma coisa.
public record ConquistaUsuarioVM(
    Long id,
    String nome,
    String descricao,
    Integer xpRecompensa,
    boolean desbloqueada,
    Integer progresso,
    Integer meta,
    Integer percentual,
    Instant dataConquista
) {
    /** progresso / meta em %, travado em 100; 100 quando desbloqueada, nulo sem meta conhecida. */
    public static Integer percentual(boolean desbloqueada, Integer progresso, Integer meta) {
        if (desbloqueada) {
            return 100;
        }
        if (meta == null || meta <= 0) {
            return null;
        }
        int atual = progresso != null ? Math.max(0, progresso) : 0;
        // floor, não round: 99,6% arredondado viraria "100%" de uma conquista ainda bloqueada
        return (int) Math.min(99, Math.floor((atual * 100.0) / meta));
    }
}
