// Posição de cada linha do ranking, com EMPATE dividindo o lugar.
//
// Numerar pelo índice da lista dava posições erradas sempre que havia empate: dois
// jogadores com a mesma pontuação apareciam como 3º e 4º, e o seguinte, com menos
// pontos, como 5º - ninguém ficava em 4º. Aqui vale a regra de competição: quem empata
// divide a posição e o próximo pula os lugares ocupados (1º, 2º, 2º, 4º).
//
// A lista precisa chegar JÁ ORDENADA do maior para o menor (é como o servidor monta o
// placar); a função só numera.
export function posicoesRanking(pontuacoes: number[]): number[] {
  const posicoes: number[] = [];
  let posicaoAtual = 1;
  for (let i = 0; i < pontuacoes.length; i++) {
    // Mesma pontuação do anterior: repete a posição dele. Senão, a posição é o
    // tamanho da lista já percorrida + 1 (os empates anteriores ficam "gastos").
    if (i > 0 && pontuacoes[i] === pontuacoes[i - 1]) {
      posicoes.push(posicaoAtual);
    } else {
      posicaoAtual = i + 1;
      posicoes.push(posicaoAtual);
    }
  }
  return posicoes;
}
