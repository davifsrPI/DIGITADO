import { GB, MB, leituraFalsa } from './leitura-falsa';
import {
  CAPACIDADE_HISTORICO,
  acrescentar,
  calcularAmostra,
  formatarBytes,
  formatarDuracao,
  formatarFracaoBytes,
  formatarMs,
  formatarPercentual,
  tetoDaEscala,
  tetoDeBytes,
} from './monitor-util';

describe('calcularAmostra (taxas a partir de duas leituras)', () => {
  it('primeira leitura: valores do instante, taxas ainda desconhecidas', () => {
    const a = calcularAmostra(leituraFalsa());
    expect(a.cpuMaquina).toBe(12.5);
    expect(a.threads).toBe(48);
    expect(a.memoriaEmUso).toBe(6 * GB);
    expect(a.gcPercentual).toBeNull();
    expect(a.requisicoesPorSegundo).toBeNull();
  });

  it('tempo em GC e requisições por segundo saem da diferença', () => {
    const antes = leituraFalsa();
    const depois = leituraFalsa({
      instante: antes.instante + 2000,
      coletorDeLixo: { ...antes.coletorDeLixo, coletas: 44, tempoMs: 900 },
      http: { requisicoes: 1010, tempoTotalMs: 20250, erros: 2 },
    });
    const a = calcularAmostra(depois, antes);
    // 100 ms de coleta em 2000 ms
    expect(a.gcPercentual).toBeCloseTo(5);
    expect(a.gcColetasPorSegundo).toBeCloseTo(2);
    expect(a.requisicoesPorSegundo).toBeCloseTo(5);
    expect(a.tempoMedioMs).toBeCloseTo(25);
  });

  it('intervalo sem requisição: taxa zero, tempo médio desconhecido', () => {
    const antes = leituraFalsa();
    const a = calcularAmostra(leituraFalsa({ instante: antes.instante + 1000 }), antes);
    expect(a.requisicoesPorSegundo).toBe(0);
    expect(a.tempoMedioMs).toBeNull();
    expect(a.gcPercentual).toBe(0);
  });

  // O servidor reiniciou entre as leituras: os contadores voltaram para perto de zero
  it('contador que andou para trás não vira taxa negativa', () => {
    const antes = leituraFalsa();
    const depois = leituraFalsa({
      instante: antes.instante + 1000,
      coletorDeLixo: { coletas: 1, tempoMs: 5, coletores: [] },
      http: { requisicoes: 3, tempoTotalMs: 40, erros: 0 },
    });
    const a = calcularAmostra(depois, antes);
    expect(a.gcPercentual).toBeNull();
    expect(a.requisicoesPorSegundo).toBeNull();
  });
});

describe('acrescentar (histórico de 60 pontos)', () => {
  it('guarda só os mais recentes', () => {
    let historico = [];
    for (let i = 0; i < CAPACIDADE_HISTORICO + 5; i++) {
      historico = acrescentar(historico, calcularAmostra(leituraFalsa({ instante: i })));
    }
    expect(historico).toHaveLength(CAPACIDADE_HISTORICO);
    expect(historico[0].instante).toBe(5);
    expect(historico[CAPACIDADE_HISTORICO - 1].instante).toBe(CAPACIDADE_HISTORICO + 4);
  });
});

describe('tetoDaEscala (gráficos que se ajustam sozinhos)', () => {
  it('arredonda para 1, 2, 2,5 ou 5 vezes uma potência de 10', () => {
    expect(tetoDaEscala([3, 7, 48], 10)).toBe(50);
    expect(tetoDaEscala([51], 10)).toBe(100);
    expect(tetoDaEscala([0.3, null], 1)).toBe(1);
    expect(tetoDaEscala([1.7], 1)).toBe(2);
    expect(tetoDaEscala([210], 10)).toBe(250);
  });

  it('sem valores, fica no mínimo', () => {
    expect(tetoDaEscala([], 10)).toBe(10);
    expect(tetoDaEscala([null, null], 1)).toBe(1);
  });
});

describe('tetoDeBytes (escala de memória)', () => {
  it('arredonda na unidade que o rótulo mostra, em base 1024', () => {
    expect(tetoDeBytes([75 * MB, 176 * MB], 1)).toBe(200 * MB);
    expect(tetoDeBytes([430 * MB], 1)).toBe(500 * MB);
    // acima de 500 MB passa a contar em GB
    expect(tetoDeBytes([510 * MB], 1)).toBe(512 * MB);
    expect(tetoDeBytes([600 * MB], 1)).toBe(GB);
    expect(tetoDeBytes([2.2 * GB], 1)).toBe(2.5 * GB);
  });

  it('sem valores, fica no mínimo', () => {
    expect(tetoDeBytes([null], 176 * MB)).toBe(200 * MB);
  });
});

describe('formatação (no jeito do Gerenciador de Tarefas, em pt-BR)', () => {
  it('bytes em base 1024', () => {
    expect(formatarBytes(312 * MB)).toBe('312 MB');
    expect(formatarBytes(5.2 * GB)).toBe('5,2 GB');
    expect(formatarBytes(0)).toBe('0 B');
    expect(formatarBytes(null)).toBe('—');
  });

  it('fração na unidade da maior parte', () => {
    expect(formatarFracaoBytes(5.2 * GB, 15.8 * GB)).toBe('5,2/15,8 GB');
    expect(formatarFracaoBytes(300 * MB, 2 * GB)).toBe('0,3/2,0 GB');
    expect(formatarFracaoBytes(300 * MB, 512 * MB)).toBe('300/512 MB');
    expect(formatarFracaoBytes(null, 2 * GB)).toBe('—');
  });

  it('percentual: inteiro acima de 10, uma casa abaixo', () => {
    expect(formatarPercentual(12.5)).toBe('13%');
    expect(formatarPercentual(0.42)).toBe('0,4%');
    expect(formatarPercentual(5)).toBe('5%');
    expect(formatarPercentual(null)).toBe('—');
  });

  it('tempo de atividade d:hh:mm:ss', () => {
    expect(formatarDuracao(3_723_000)).toBe('0:01:02:03');
    expect(formatarDuracao(2 * 86_400_000 + 5000)).toBe('2:00:00:05');
  });

  it('milissegundos', () => {
    expect(formatarMs(350)).toBe('350 ms');
    expect(formatarMs(1800)).toBe('1,8 s');
    expect(formatarMs(null)).toBe('—');
  });
});
