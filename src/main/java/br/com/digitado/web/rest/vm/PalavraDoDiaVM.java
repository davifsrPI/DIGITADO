package br.com.digitado.web.rest.vm;

import java.time.LocalDate;

// ViewModel público da Palavra do Dia. O desafio é um DITADO: o jogador ouve a
// palavra (sintetizada no navegador a partir de textoAudio) e escreve a grafia.
// Por isso o texto viaja até o cliente - a validação e o controle de chance única
// continuam 100% no servidor. Os demais campos são metadados inofensivos
// (incluindo a dica cadastrada no banco, feita para ser mostrada).
public record PalavraDoDiaVM(
    boolean disponivel,
    LocalDate data,
    int tamanho,
    String textoAudio,
    String dificuldade,
    String categoria,
    String dica,
    boolean jaTentou,
    ResultadoPalavraDoDiaVM resultado
) {}
