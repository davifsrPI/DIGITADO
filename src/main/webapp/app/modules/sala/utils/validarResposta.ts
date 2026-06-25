// Classificação dos tipos de erro que o aluno pode cometer ao digitar uma palavra
export type TipoErro = 'ACENTUACAO' | 'TROCA_LETRA' | 'LETRA_FALTANDO' | 'LETRA_EXTRA' | 'ERRO_FONETICO' | 'OUTRO';

// Resultado da validação local (feita no frontend antes de receber o feedback do servidor)
export interface ResultadoValidacao {
  correta: boolean;
  tipoErro?: TipoErro;
  similaridade: number;
}

// Remove acentos da string usando decomposição Unicode (NFD) — permite comparar sem diferenciar versões acentuadas
function removerAcentos(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '');
}

// Algoritmo de Levenshtein: calcula quantas edições (inserção, remoção, substituição) são necessárias
// para transformar a string "a" na string "b" — base para medir similaridade entre palavras
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

// Tabela de equivalências fonéticas do português: mapeia grafias diferentes que soam igual.
// Ex: "ç" e "ss" soam como "s"; "ch" e "x" soam igual em muitas regiões.
const FONETICO: [RegExp, string][] = [
  [/ç/g, 's'],
  [/ss/g, 's'],
  [/rr/g, 'r'],
  [/ph/g, 'f'],
  [/ch/g, 'x'],
  [/lh/g, 'l'],
  [/nh/g, 'n'],
  [/[qk]/g, 'c'],
  [/y/g, 'i'],
  [/w/g, 'v'],
  [/[sz]/g, 'z'],
];

// Aplica todas as substituições fonéticas da tabela acima — duas palavras foneticamente equivalentes
// terão o mesmo resultado, permitindo detectar erros de som vs. grafia
function fonetizar(s: string): string {
  let r = s;
  for (const [from, to] of FONETICO) r = r.replace(from, to);
  return r;
}

// Valida a resposta do aluno comparando com a palavra correta em várias camadas:
// 1. Exata → correta
// 2. Igual sem acentos → erro de acentuação
// 3. Igual foneticamente → erro fonético (ex: "chave" vs "xave")
// 4. Levenshtein = 1 → identifica letra trocada, extra ou faltando
// 5. Qualquer outro → erro genérico com percentual de similaridade
export function validarResposta(digitado: string, correto: string): ResultadoValidacao {
  const d = digitado.trim().toLowerCase();
  const c = correto.trim().toLowerCase();

  if (d === c) return { correta: true, similaridade: 1 };

  const dSem = removerAcentos(d);
  const cSem = removerAcentos(c);

  if (dSem === cSem) return { correta: false, tipoErro: 'ACENTUACAO', similaridade: 0.95 };

  if (fonetizar(dSem) === fonetizar(cSem)) return { correta: false, tipoErro: 'ERRO_FONETICO', similaridade: 0.85 };

  const dist = levenshtein(dSem, cSem);
  const maxLen = Math.max(d.length, c.length);
  const similaridade = parseFloat((1 - dist / maxLen).toFixed(2));

  if (dist === 1) {
    if (dSem.length < cSem.length) return { correta: false, tipoErro: 'LETRA_FALTANDO', similaridade };
    if (dSem.length > cSem.length) return { correta: false, tipoErro: 'LETRA_EXTRA', similaridade };
    return { correta: false, tipoErro: 'TROCA_LETRA', similaridade };
  }

  return { correta: false, tipoErro: 'OUTRO', similaridade };
}

// Mensagens amigáveis exibidas ao aluno para cada tipo de erro detectado
export const MENSAGEM_ERRO: Record<TipoErro, string> = {
  ACENTUACAO: 'Atenção com os acentos!',
  TROCA_LETRA: 'Uma letra trocada',
  LETRA_FALTANDO: 'Faltou uma letra',
  LETRA_EXTRA: 'Uma letra a mais',
  ERRO_FONETICO: 'Erro fonético (som certo, escrita diferente)',
  OUTRO: 'Resposta incorreta',
};
