/**
 * Os tipos de erro que o servidor grava no histórico (ComparadorPalavras), com o que
 * cada um quer dizer e um exemplo.
 *
 * Os painéis mostravam só o rótulo ("Troca de letra", "Outro"), e quem lia não sabia
 * o que entrava em cada um - "Erro fonético" e "Outro", principalmente. Um lugar só
 * para o texto: o painel do admin, o "Meu Desempenho" e o modo individual contam a
 * mesma história.
 *
 * A ordem é a da classificação no servidor: acento, som, uma letra, o resto - e por
 * último a rodada sem resposta.
 */
export interface DescricaoTipoErro {
  rotulo: string;
  explicacao: string;
  exemplo: string;
  cor: string;
}

export const TIPOS_ERRO: Record<string, DescricaoTipoErro> = {
  ACENTUACAO: {
    rotulo: 'Acentuação',
    explicacao: 'Todas as letras certas; só o acento ficou errado ou faltou.',
    exemplo: '"cafe" no lugar de "café"',
    cor: '#f59e0b',
  },
  ERRO_FONETICO: {
    rotulo: 'Erro fonético',
    explicacao: 'Escreveu como se fala: o som está certo, a grafia não (ç/ss/s/z, ch/x, qu/c, lh/l...).',
    exemplo: '"xave" no lugar de "chave"',
    cor: '#a78bfa',
  },
  TROCA_LETRA: {
    rotulo: 'Troca de letra',
    explicacao: 'Uma letra trocada por outra; o resto da palavra está certo.',
    exemplo: '"menimo" no lugar de "menino"',
    cor: '#f472b6',
  },
  LETRA_FALTANDO: {
    rotulo: 'Letra faltando',
    explicacao: 'Esqueceu uma letra da palavra.',
    exemplo: '"cavlo" no lugar de "cavalo"',
    cor: '#38bdf8',
  },
  LETRA_EXTRA: {
    rotulo: 'Letra sobrando',
    explicacao: 'Uma letra a mais na palavra.',
    exemplo: '"carrro" no lugar de "carro"',
    cor: '#34d399',
  },
  OUTRO: {
    rotulo: 'Outro erro',
    explicacao: 'Mais de uma diferença ao mesmo tempo: a palavra saiu bem diferente da certa.',
    exemplo: '"caza" no lugar de "chácara"',
    cor: '#f87171',
  },
  NAO_RESPONDEU: {
    rotulo: 'Não respondeu',
    explicacao: 'O tempo da rodada acabou sem resposta. Conta como erro na taxa de acerto.',
    exemplo: 'campo em branco quando o relógio zerou',
    cor: '#94a3b8',
  },
};

export const ORDEM_TIPOS_ERRO = Object.keys(TIPOS_ERRO);

/** Rótulo de um tipo, tolerando um tipo que o servidor venha a gravar e a tela não conheça. */
export const rotuloTipoErro = (tipo: string | null | undefined): string => (tipo ? (TIPOS_ERRO[tipo]?.rotulo ?? tipo) : '');
