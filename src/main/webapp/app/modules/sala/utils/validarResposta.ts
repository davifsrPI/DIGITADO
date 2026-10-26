// Classificação dos tipos de erro que o aluno pode cometer ao digitar uma palavra.
// Os mesmos tipos do servidor (ComparadorPalavras), na ordem em que são testados.
export type TipoErro =
  | 'ACENTUACAO'
  | 'ESPACO_HIFEN'
  | 'ERRO_FONETICO'
  | 'LETRAS_INVERTIDAS'
  | 'TROCA_LETRA'
  | 'LETRA_FALTANDO'
  | 'LETRA_EXTRA'
  | 'VARIOS_ERROS'
  | 'OUTRA_PALAVRA'
  | 'NAO_RESPONDEU';

// Resultado da validação local (feita no frontend antes de receber o feedback do servidor)
export interface ResultadoValidacao {
  correta: boolean;
  tipoErro?: TipoErro;
  similaridade: number;
}

// Remove acentos da string usando decomposição Unicode (NFD), permite comparar sem diferenciar versões acentuadas
function removerAcentos(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '');
}

// Algoritmo de Levenshtein: calcula quantas edições (inserção, remoção, substituição) são necessárias
// para transformar a string "a" na string "b", base para medir similaridade entre palavras
function levenshtein(a: string, b: string): number {
  const m = a.length,
    n = b.length;
  const dp: number[][] = Array.from({ length: m + 1 }, (_, i) => [i, ...Array(n).fill(0)]);
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] : 1 + Math.min(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1]);
    }
  }
  return dp[m][n];
}

// Levenshtein que conta a inversão de duas letras vizinhas como UMA edição (Damerau
// restrita): "pdera" por "pedra" é um deslize só. Só a classificação usa; a
// similaridade continua com o Levenshtein comum, como no servidor.
function distanciaComInversao(a: string, b: string): number {
  const m = a.length,
    n = b.length;
  const dp: number[][] = Array.from({ length: m + 1 }, (_, i) => [i, ...Array(n).fill(0)]);
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      const custo = a[i - 1] === b[j - 1] ? 0 : 1;
      dp[i][j] = Math.min(dp[i - 1][j - 1] + custo, dp[i - 1][j] + 1, dp[i][j - 1] + 1);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
        dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
      }
    }
  }
  return dp[m][n];
}

// Tabela de equivalências fonéticas do português: mapeia grafias diferentes que soam igual.
// Ex: "ç" e "ss" soam como "s"; "ch" e "x" soam igual em muitas regiões. A MESMA tabela
// do servidor (ComparadorPalavras.REGRAS_FONETICAS), na mesma ordem: "sc" e "xc" viram
// "s" antes do "c" solto, e "ch"/"lh"/"nh" saem antes do "h" mudo do começo.
const FONETICO: [RegExp, string][] = [
  [/sc(?=[eiéêí])/g, 's'], // nascer / naser
  [/sç/g, 's'], // desça / dessa
  [/xc(?=[eiéêí])/g, 's'], // exceção / esseção
  [/x(?=[ptc])/g, 's'], // texto / testo
  [/ç/g, 's'],
  [/ss/g, 's'],
  [/rr/g, 'r'],
  [/ph/g, 'f'],
  [/ch/g, 'x'],
  [/lh/g, 'l'],
  [/nh/g, 'n'],
  [/(^|\s)h/g, '$1'], // hora / ora
  [/g(?=[eiéêí])/g, 'j'], // gente / jente
  [/n(?=[pb])/g, 'm'], // campo / canpo
  [/m(?=[^pbaeiouáéíóúâêôãõàü]|$)/g, 'n'], // homem / homen
  [/l(?=[^aeiouáéíóúâêôãõàü]|$)/g, 'u'], // Brasil / Brasiu
  [/[qk]/g, 'c'],
  [/y/g, 'i'],
  [/w/g, 'v'],
  [/[sz]/g, 'z'],
];

const semSeparador = (s: string): string => s.replace(/[\s-]/g, '');

// Abaixo disso a resposta é outra palavra, e não a certa com vários erros (mesmo limite do servidor)
const SIMILARIDADE_MINIMA_VARIOS_ERROS = 50;

// Exatamente uma letra fora do lugar (texto sem acento): faltou, sobrou, inverteu ou trocou
function tipoDeUmaLetra(dSem: string, cSem: string): TipoErro {
  if (dSem.length < cSem.length) return 'LETRA_FALTANDO';
  if (dSem.length > cSem.length) return 'LETRA_EXTRA';
  let i = 0;
  while (i < cSem.length && dSem[i] === cSem[i]) i++;
  const invertidas = i + 1 < cSem.length && dSem[i] === cSem[i + 1] && dSem[i + 1] === cSem[i] && dSem.slice(i + 2) === cSem.slice(i + 2);
  return invertidas ? 'LETRAS_INVERTIDAS' : 'TROCA_LETRA';
}

// Reduz a palavra ao "som" dela: duas grafias que soam igual caem no mesmo texto
// ("caça" e "cassa" viram "caza"; "chave" e "xave" viram "xave").
//
// A ORDEM importa: as regras rodam ANTES de tirar os acentos, porque o NFD decompõe
// "ç" em "c" + cedilha e a cedilha é um diacrítico como qualquer outro - removida
// primeiro, "caça" virava "caca" e a regra ç → s nunca chegava a valer. Os acentos de
// vogal saem depois; a diferença só de acento já foi tratada como ACENTUACAO acima.
// Mesma regra do servidor (JogoSalaService.som), que é quem grava no histórico.
function som(s: string): string {
  let r = s;
  for (const [from, to] of FONETICO) r = r.replace(from, to);
  return removerAcentos(r);
}

// Valida a resposta do aluno comparando com a palavra correta em várias camadas:
// 1. Exata → correta
// 2. Igual sem acentos → erro de acentuação
// 3. Igual sem espaços e hífens → espaço ou hífen
// 4. Igual foneticamente → erro fonético (ex: "chave" vs "xave")
// 5. Uma edição (contando inversão de vizinhas) → letra invertida, trocada, extra ou faltando
// 6. Mais que isso → vários erros (metade ou mais certa) ou outra palavra
// Forma canônica da comparação, a MESMA regra do servidor (JogoSalaService.canonico):
// NFC junta "a + til" no mesmo "ã" que o teclado produz, e o espaço fixo (NBSP) de uma
// palavra colada de documento vira espaço normal antes de aparar as pontas. Sem isso a
// conferência local e a do servidor podiam discordar da mesma resposta.
function canonico(s: string): string {
  return s
    .normalize('NFC')
    .replace(/\u00a0/g, ' ')
    .trim()
    .toLowerCase();
}

export function validarResposta(digitado: string, correto: string): ResultadoValidacao {
  const d = canonico(digitado);
  const c = canonico(correto);

  if (d === c) return { correta: true, similaridade: 1 };
  if (d === '') return { correta: false, tipoErro: 'NAO_RESPONDEU', similaridade: 0 };

  const dSem = removerAcentos(d);
  const cSem = removerAcentos(c);

  if (dSem === cSem) return { correta: false, tipoErro: 'ACENTUACAO', similaridade: 0.95 };

  const dist = levenshtein(dSem, cSem);
  const maxLen = Math.max(d.length, c.length);
  const similaridade = parseFloat((1 - dist / maxLen).toFixed(2));

  if (semSeparador(dSem) === semSeparador(cSem)) return { correta: false, tipoErro: 'ESPACO_HIFEN', similaridade };

  // Som certo com grafia errada: comparado sobre o texto AINDA acentuado, por causa
  // da cedilha (ver som)
  if (som(d) === som(c)) return { correta: false, tipoErro: 'ERRO_FONETICO', similaridade: 0.85 };

  if (distanciaComInversao(dSem, cSem) === 1) return { correta: false, tipoErro: tipoDeUmaLetra(dSem, cSem), similaridade };

  const tipoErro = Math.round((1 - dist / maxLen) * 100) >= SIMILARIDADE_MINIMA_VARIOS_ERROS ? 'VARIOS_ERROS' : 'OUTRA_PALAVRA';
  return { correta: false, tipoErro, similaridade };
}

// Uma letra na comparação visual: o caractere e se ele bate com a palavra correta
export interface LetraDiff {
  ch: string;
  ok: boolean;
}

// Compara o que o aluno digitou com a palavra correta e devolve, letra a letra,
// o que bateu e o que não bateu, em CADA lado. Usa o mesmo alinhamento do
// Levenshtein (backtrace) para casar letras mesmo quando falta ou sobra uma:
//  - digitado: cada letra marcada ok=false é uma letra trocada ou a mais
//  - correto:  cada letra marcada ok=false é uma letra que o aluno errou/esqueceu
// A comparação ignora maiúsculas/minúsculas, mas devolve os caracteres originais
// (acentos aparecem como diferença, que é o certo para um ditado).
export function compararLetras(digitado: string, correto: string): { digitado: LetraDiff[]; correto: LetraDiff[] } {
  const a = digitado.trim();
  const b = correto.trim();
  const al = a.toLowerCase();
  const bl = b.toLowerCase();
  const m = a.length;
  const n = b.length;

  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 0; i <= m; i++) dp[i][0] = i;
  for (let j = 0; j <= n; j++) dp[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      const custo = al[i - 1] === bl[j - 1] ? 0 : 1;
      dp[i][j] = Math.min(dp[i - 1][j - 1] + custo, dp[i - 1][j] + 1, dp[i][j - 1] + 1);
    }
  }

  const dig: LetraDiff[] = [];
  const cor: LetraDiff[] = [];
  let i = m;
  let j = n;
  while (i > 0 || j > 0) {
    const custo = i > 0 && j > 0 ? (al[i - 1] === bl[j - 1] ? 0 : 1) : Infinity;
    if (i > 0 && j > 0 && dp[i][j] === dp[i - 1][j - 1] + custo) {
      const ok = custo === 0;
      dig.unshift({ ch: a[i - 1], ok });
      cor.unshift({ ch: b[j - 1], ok });
      i--;
      j--;
    } else if (i > 0 && dp[i][j] === dp[i - 1][j] + 1) {
      // letra a mais no que o aluno digitou
      dig.unshift({ ch: a[i - 1], ok: false });
      i--;
    } else {
      // letra que faltou na resposta do aluno
      cor.unshift({ ch: b[j - 1], ok: false });
      j--;
    }
  }
  return { digitado: dig, correto: cor };
}

// Mensagens amigáveis exibidas ao aluno para cada tipo de erro detectado
export const MENSAGEM_ERRO: Record<TipoErro, string> = {
  ACENTUACAO: 'Atenção com os acentos!',
  ESPACO_HIFEN: 'Confira o espaço ou o hífen',
  ERRO_FONETICO: 'Erro fonético (som certo, escrita diferente)',
  LETRAS_INVERTIDAS: 'Duas letras trocadas de lugar',
  TROCA_LETRA: 'Uma letra trocada',
  LETRA_FALTANDO: 'Faltou uma letra',
  LETRA_EXTRA: 'Uma letra a mais',
  VARIOS_ERROS: 'Quase! Mais de uma letra errada',
  OUTRA_PALAVRA: 'Resposta incorreta',
  NAO_RESPONDEU: 'Sem resposta',
};
