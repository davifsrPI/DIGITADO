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

/**
 * O código dentro de um texto COLADO no campo.
 *
 * O professor costuma mandar o código numa mensagem ("Entrem na sala FB5P4D!"),
 * e o aluno cola a mensagem inteira. Limpar e cortar nos 6 primeiros caracteres
 * dava "ENTREM" - um código errado que ele nem tinha digitado. Aqui o código é
 * procurado como palavra de 6 letras/números; havendo mais de uma, vence a que tem
 * número (como os códigos do servidor costumam ter). Sem nenhuma, vale a limpeza
 * comum, como se tivesse sido digitado.
 */
export const extrairCodigoColado = (texto: string): string => {
  const candidatos = (texto.toUpperCase().match(/[A-Z0-9]+/g) ?? []).filter(p => p.length === TAMANHO_CODIGO_SALA);
  if (candidatos.length === 0) return limparCodigoSala(texto);
  return candidatos.find(p => /\d/.test(p)) ?? candidatos[candidatos.length - 1];
};
