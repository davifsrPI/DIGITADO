/**
 * Forma do código de acesso da sala, para as telas que o leem e o validam.
 *
 * Quem GERA o código é o servidor, e só ele: CodigoSalaService, exposto em
 * GET /api/salas/codigo/novo. Antes a tela de criação sorteava no navegador - o
 * cliente escolhia a chave primária da sala e tentava de novo a cada colisão.
 * Aqui ficam apenas as constantes de que o front precisa para desenhar as caixas
 * do código e limpar o que o aluno digita.
 */

/** Quantos caracteres o código tem - uma caixa por caractere na tela de entrada. */
export const TAMANHO_CODIGO_SALA = 6;

/**
 * Normaliza o que foi digitado ou colado no campo do código: maiúsculas, fora o
 * que não é letra nem número, e cortado no tamanho.
 *
 * O filtro é de propósito mais largo que o alfabeto do servidor (que não usa O,
 * I, 1 nem 0, os caracteres que o aluno confunde ao copiar do quadro): se ele
 * digitar um "O" no lugar do zero, a letra APARECE e a sala é que não vai ser
 * encontrada. Sumir com o caractere enquanto ele digita parece campo travado.
 */
export const limparCodigoSala = (valor: string): string =>
  valor
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, TAMANHO_CODIGO_SALA);
