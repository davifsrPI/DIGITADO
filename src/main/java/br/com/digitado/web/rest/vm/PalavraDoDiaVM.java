package br.com.digitado.web.rest.vm;

import java.time.LocalDate;

// ViewModel público da Palavra do Dia — NUNCA contém o texto da palavra.
// O frontend recebe apenas o anagrama (letras embaralhadas), o tamanho e metadados
// inofensivos (incluindo a dica cadastrada no banco, feita para ser mostrada);
// o texto correto só aparece no resultado, DEPOIS da tentativa.
public record PalavraDoDiaVM(
    boolean disponivel,
    LocalDate data,
    int tamanho,
    String letrasEmbaralhadas,
    String dificuldade,
    String categoria,
    String dica,
    boolean jaTentou,
    ResultadoPalavraDoDiaVM resultado
) {}
