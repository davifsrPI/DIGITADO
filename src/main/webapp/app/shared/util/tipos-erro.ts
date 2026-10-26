/**
 * Os tipos de erro que o servidor grava no histórico (ComparadorPalavras), com o que
 * cada um quer dizer e um exemplo.
 *
 * Os painéis mostravam só o rótulo ("Troca de letra", "Outro"), e quem lia não sabia
 * o que entrava em cada um - "Erro fonético" e "Outro", principalmente. Um lugar só
 * para o texto: o painel do admin, o "Meu Desempenho" e o modo individual contam a
 * mesma história.
 *
 * A ordem é a da classificação no servidor: acento, espaço, som, uma letra, mais de
 * uma letra - e por último o "Outro erro" dos registros antigos e a rodada sem resposta.
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
    explicacao: 'Todas as letras certas; só o acento (ou a cedilha) ficou errado, faltou ou sobrou.',
    exemplo: '"cafe" no lugar de "café"',
    cor: '#f59e0b',
  },
  ESPACO_HIFEN: {
    rotulo: 'Espaço ou hífen',
    explicacao: 'As letras estão certas; faltou ou sobrou um espaço ou um hífen.',
    exemplo: '"guardachuva" no lugar de "guarda-chuva"',
    cor: '#facc15',
  },
  ERRO_FONETICO: {
    rotulo: 'Erro fonético',
    explicacao: 'Escreveu como se fala: o som está certo, a grafia não (ç/ss/s/z, ch/x, g/j, h mudo, l/u, m/n...).',
    exemplo: '"xave" no lugar de "chave"',
    cor: '#a78bfa',
  },
  LETRAS_INVERTIDAS: {
    rotulo: 'Letras invertidas',
    explicacao: 'Duas letras vizinhas trocadas de lugar; todas as letras da palavra estão lá.',
    exemplo: '"pdera" no lugar de "pedra"',
    cor: '#2dd4bf',
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
  VARIOS_ERROS: {
    rotulo: 'Vários erros',
    explicacao: 'Mais de uma letra errada, mas ainda dá para reconhecer a palavra (metade ou mais certa).',
    exemplo: '"trabisseiro" no lugar de "travesseiro"',
    cor: '#fb7185',
  },
  OUTRA_PALAVRA: {
    rotulo: 'Outra palavra',
    explicacao: 'A resposta saiu bem diferente: menos da metade da palavra certa.',
    exemplo: '"caza" no lugar de "chácara"',
    cor: '#ef4444',
  },
  OUTRO: {
    rotulo: 'Outro erro (antigo)',
    explicacao:
      'Erro registrado antes da classificação detalhada, quando "vários erros", "outra palavra" e "letras invertidas" ' +
      'caíam todos aqui. Erros novos não entram mais neste tipo.',
    exemplo: '"caza" no lugar de "chácara"',
    cor: '#a8a29e',
  },
  NAO_RESPONDEU: {
    rotulo: 'Não respondeu',
    explicacao: 'O tempo da rodada acabou sem resposta. Conta como erro na taxa de acerto.',
    exemplo: 'campo em branco quando o relógio zerou',
    cor: '#94a3b8',
  },
};

export const ORDEM_TIPOS_ERRO = Object.keys(TIPOS_ERRO);

/** Tipos que só existem em registros antigos: o servidor não grava mais erro novo neles. */
export const TIPOS_ERRO_ANTIGOS = ['OUTRO'];

/** Rótulo de um tipo, tolerando um tipo que o servidor venha a gravar e a tela não conheça. */
export const rotuloTipoErro = (tipo: string | null | undefined): string => (tipo ? (TIPOS_ERRO[tipo]?.rotulo ?? tipo) : '');

/**
 * O jeito específico do erro dentro do tipo (ComparadorPalavras.analisarErro). Os
 * códigos são únicos entre todos os tipos, por isso um mapa só.
 */
export const SUBTIPOS_ERRO: Record<string, { rotulo: string; explicacao: string }> = {
  // Acentuação
  ACENTO_FALTANDO: { rotulo: 'Faltou o acento', explicacao: 'Escreveu a letra sem o acento que ela tem ("cafe").' },
  ACENTO_SOBRANDO: { rotulo: 'Acento onde não tem', explicacao: 'Pôs acento numa letra que não leva ("fáca").' },
  ACENTO_TROCADO: { rotulo: 'Acento trocado', explicacao: 'Agudo no lugar de circunflexo, til, crase... ("cafê").' },
  CEDILHA_FALTANDO: { rotulo: 'Faltou a cedilha', explicacao: 'Escreveu "c" onde é "ç" ("caca" por "caça").' },
  CEDILHA_SOBRANDO: { rotulo: 'Cedilha onde não tem', explicacao: 'Escreveu "ç" onde é "c".' },
  ACENTOS_VARIOS: { rotulo: 'Vários acentos errados', explicacao: 'Mais de um problema de acento na mesma palavra.' },
  // Espaço e hífen
  ESPACO_FALTANDO: { rotulo: 'Palavras juntas', explicacao: 'Faltou o espaço entre as palavras ("derepente").' },
  ESPACO_SOBRANDO: { rotulo: 'Palavra separada', explicacao: 'Separou com espaço o que é junto ("a gente" por "agente").' },
  HIFEN_FALTANDO: { rotulo: 'Faltou o hífen', explicacao: 'Juntou as partes sem o hífen ("guardachuva").' },
  HIFEN_SOBRANDO: { rotulo: 'Hífen onde não tem', explicacao: 'Pôs hífen onde a palavra é junta ou separada.' },
  HIFEN_POR_ESPACO: { rotulo: 'Espaço no lugar do hífen', explicacao: 'Separou com espaço o que é com hífen ("guarda chuva").' },
  ESPACO_POR_HIFEN: { rotulo: 'Hífen no lugar do espaço', explicacao: 'Ligou com hífen o que é separado.' },
  SEPARADORES_VARIOS: { rotulo: 'Espaços e hífens', explicacao: 'Mais de um espaço ou hífen fora do lugar.' },
  // Som
  SOM_S_Z: { rotulo: 'Som de S/Z', explicacao: 'Confundiu as grafias do som de S e Z: s, ss, ç, c, sc, xc, x, z.' },
  SOM_CH_X: { rotulo: 'CH ou X', explicacao: 'Trocou "ch" por "x" ou o contrário ("xave").' },
  SOM_G_J: { rotulo: 'G ou J', explicacao: 'Trocou "g" e "j" antes de E e I ("jente").' },
  H_MUDO: { rotulo: 'H mudo', explicacao: 'Esqueceu (ou pôs) o "h" do começo, que não tem som ("ora" por "hora").' },
  SOM_R_RR: { rotulo: 'R ou RR', explicacao: 'Um "r" no lugar de "rr" ou o contrário ("caro" por "carro").' },
  SOM_L_U: { rotulo: 'L ou U', explicacao: 'Trocou "l" e "u" no fim da sílaba, que soam igual ("Brasiu").' },
  SOM_M_N: { rotulo: 'M ou N', explicacao: 'Trocou "m" e "n" antes de P/B ou no fim da palavra ("canpo", "homen").' },
  SOM_LH_NH: { rotulo: 'LH ou NH', explicacao: 'Confundiu "lh"/"nh" com "l"/"n"/"li"/"ni".' },
  SOM_C_QU: { rotulo: 'C, QU ou K', explicacao: 'Trocou "c", "qu" e "k", que têm o mesmo som.' },
  SOM_OUTRO: { rotulo: 'Outros sons', explicacao: 'ph/f, y/i, w/v: grafias estrangeiras com o mesmo som.' },
  SOM_VARIOS: { rotulo: 'Mais de um som', explicacao: 'Mais de uma troca de som na mesma palavra.' },
  // Letras invertidas
  INVERTEU_VOGAL_CONSOANTE: { rotulo: 'Vogal e consoante', explicacao: 'Inverteu uma vogal e uma consoante vizinhas ("pdera").' },
  INVERTEU_CONSOANTES: { rotulo: 'Duas consoantes', explicacao: 'Inverteu duas consoantes vizinhas.' },
  INVERTEU_VOGAIS: { rotulo: 'Duas vogais', explicacao: 'Inverteu duas vogais vizinhas ("feil" por "fiel").' },
  // Troca de letra
  LETRA_ESPELHADA: {
    rotulo: 'Letra espelhada',
    explicacao: 'b/d, p/q, n/u: a mesma forma virada. Comum em quem está aprendendo a escrever.',
  },
  TECLA_VIZINHA: { rotulo: 'Tecla vizinha', explicacao: 'Apertou a tecla do lado no teclado: deslize de digitação, não de ortografia.' },
  TROCOU_VOGAL: { rotulo: 'Vogal trocada', explicacao: 'Trocou uma vogal por outra letra ("masa" por "mesa").' },
  TROCOU_CONSOANTE: { rotulo: 'Consoante trocada', explicacao: 'Trocou uma consoante por outra letra ("pato" por "gato").' },
  // Letra faltando
  FALTOU_LETRA_DOBRADA: { rotulo: 'Letra dobrada', explicacao: 'Escreveu uma letra só onde são duas ("piza" por "pizza").' },
  FALTOU_VOGAL: { rotulo: 'Faltou vogal', explicacao: 'Esqueceu uma vogal ("cavlo").' },
  FALTOU_CONSOANTE: { rotulo: 'Faltou consoante', explicacao: 'Esqueceu uma consoante ("caeta" por "caneta").' },
  // Letra sobrando
  LETRA_REPETIDA: { rotulo: 'Letra repetida', explicacao: 'Repetiu sem querer a letra do lado ("caasa").' },
  TECLA_VIZINHA_JUNTO: { rotulo: 'Tecla vizinha junto', explicacao: 'Apertou junto a tecla do lado ("casda").' },
  SOBROU_VOGAL: { rotulo: 'Vogal a mais', explicacao: 'Uma vogal que a palavra não tem.' },
  SOBROU_CONSOANTE: { rotulo: 'Consoante a mais', explicacao: 'Uma consoante que a palavra não tem.' },
  // Vários erros
  DUAS_DIFERENCAS: { rotulo: 'Duas diferenças', explicacao: 'Duas letras fora do lugar.' },
  TRES_OU_MAIS: { rotulo: 'Três ou mais', explicacao: 'Três ou mais letras fora do lugar.' },
};

/**
 * Os subtipos que o servidor pode gravar em cada tipo, na ordem de SUBTIPOS_ERRO. É o
 * catálogo que o painel mostra inteiro, com zero no que ainda não aconteceu. Tipo que
 * não está aqui (outra palavra, não respondeu, o "outro" antigo) não tem subtipo.
 */
export const SUBTIPOS_POR_TIPO: Record<string, string[]> = {
  ACENTUACAO: ['ACENTO_FALTANDO', 'ACENTO_SOBRANDO', 'ACENTO_TROCADO', 'CEDILHA_FALTANDO', 'CEDILHA_SOBRANDO', 'ACENTOS_VARIOS'],
  ESPACO_HIFEN: [
    'ESPACO_FALTANDO',
    'ESPACO_SOBRANDO',
    'HIFEN_FALTANDO',
    'HIFEN_SOBRANDO',
    'HIFEN_POR_ESPACO',
    'ESPACO_POR_HIFEN',
    'SEPARADORES_VARIOS',
  ],
  ERRO_FONETICO: [
    'SOM_S_Z',
    'SOM_CH_X',
    'SOM_G_J',
    'H_MUDO',
    'SOM_R_RR',
    'SOM_L_U',
    'SOM_M_N',
    'SOM_LH_NH',
    'SOM_C_QU',
    'SOM_OUTRO',
    'SOM_VARIOS',
  ],
  LETRAS_INVERTIDAS: ['INVERTEU_VOGAL_CONSOANTE', 'INVERTEU_CONSOANTES', 'INVERTEU_VOGAIS'],
  TROCA_LETRA: ['LETRA_ESPELHADA', 'TECLA_VIZINHA', 'TROCOU_VOGAL', 'TROCOU_CONSOANTE'],
  LETRA_FALTANDO: ['FALTOU_LETRA_DOBRADA', 'FALTOU_VOGAL', 'FALTOU_CONSOANTE'],
  LETRA_EXTRA: ['LETRA_REPETIDA', 'TECLA_VIZINHA_JUNTO', 'SOBROU_VOGAL', 'SOBROU_CONSOANTE'],
  VARIOS_ERROS: ['DUAS_DIFERENCAS', 'TRES_OU_MAIS'],
};

export const temSubtiposErro = (tipo: string): boolean => (SUBTIPOS_POR_TIPO[tipo]?.length ?? 0) > 0;

/**
 * Rótulo de um subtipo. Nulo é a resposta gravada antes da classificação detalhada,
 * que só guardou o tipo - e não "sem erro".
 */
export const rotuloSubtipoErro = (subtipo: string | null | undefined): string =>
  subtipo ? (SUBTIPOS_ERRO[subtipo]?.rotulo ?? subtipo) : 'Sem detalhe (registro antigo)';

/** Partes do detalhe "certo→digitado"; lado vazio é a letra que faltou ou sobrou. */
export const partesDoDetalhe = (detalhe: string): { certo: string; digitado: string } => {
  const [certo = '', digitado = ''] = detalhe.split('→');
  return { certo, digitado };
};

/** O detalhe para ler: espaço vira "␣", e o lado vazio vira "—" ("a → —" = faltou o a). */
export const textoDoTrecho = (trecho: string): string => (trecho === '' ? '—' : trecho.replace(/ /g, '␣'));
