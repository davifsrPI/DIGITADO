import { posicoesRanking } from './posicoes-ranking';

describe('posicoesRanking', () => {
  it('numera na sequência quando não há empate', () => {
    expect(posicoesRanking([50, 40, 30, 20])).toEqual([1, 2, 3, 4]);
  });

  it('divide a posição no empate e pula os lugares ocupados', () => {
    // dois em 2º → o seguinte é 4º, não 3º
    expect(posicoesRanking([50, 40, 40, 30])).toEqual([1, 2, 2, 4]);
  });

  it('mantém a numeração correta com empate no fim da lista grande', () => {
    // 18 pontuações distintas e um empate triplo no fim: as posições 19, 19, 19
    const pontos = [...Array(18).keys()].map(i => 100 - i).concat([5, 5, 5]);
    const posicoes = posicoesRanking(pontos);
    expect(posicoes.slice(0, 18)).toEqual([...Array(18).keys()].map(i => i + 1));
    expect(posicoes.slice(18)).toEqual([19, 19, 19]);
  });

  it('coloca todos em 1º quando ninguém pontuou', () => {
    expect(posicoesRanking([0, 0, 0])).toEqual([1, 1, 1]);
  });

  it('devolve lista vazia para placar vazio', () => {
    expect(posicoesRanking([])).toEqual([]);
  });
});
