export type TipoErro = 'ACENTUACAO' | 'TROCA_LETRA' | 'LETRA_FALTANDO' | 'LETRA_EXTRA' | 'ERRO_FONETICO' | 'OUTRO';

export interface ResultadoValidacao {
  correta: boolean;
  tipoErro?: TipoErro;
  similaridade: number;
}

function removerAcentos(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '');
}

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

function fonetizar(s: string): string {
  let r = s;
  for (const [from, to] of FONETICO) r = r.replace(from, to);
  return r;
}

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

export const MENSAGEM_ERRO: Record<TipoErro, string> = {
  ACENTUACAO: 'Atenção com os acentos!',
  TROCA_LETRA: 'Uma letra trocada',
  LETRA_FALTANDO: 'Faltou uma letra',
  LETRA_EXTRA: 'Uma letra a mais',
  ERRO_FONETICO: 'Erro fonético (som certo, escrita diferente)',
  OUTRO: 'Resposta incorreta',
};
