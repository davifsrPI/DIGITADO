// Leitura plausível de GET /management/desempenho para os testes do painel;
// cada teste muda só o que importa para ele.
import { LeituraDesempenho } from './monitor-util';

export const GB = 1024 ** 3;
export const MB = 1024 ** 2;

export const leituraFalsa = (mudancas: Partial<LeituraDesempenho> = {}): LeituraDesempenho => ({
  instante: 1_000_000,
  cpu: { maquina: 12.5, processo: 3.2, processadores: 8, cargaMedia: null },
  memoria: { total: 16 * GB, disponivel: 10 * GB, emUso: 6 * GB, confirmada: 9 * GB, limiteConfirmada: 24 * GB },
  memoriaJvm: {
    heapUsada: 300 * MB,
    heapConfirmada: 512 * MB,
    heapMaxima: 2 * GB,
    naoHeapUsada: 150 * MB,
    naoHeapConfirmada: 170 * MB,
    buffersDiretos: 10 * MB,
    classesCarregadas: 21000,
    areas: [
      { nome: 'G1 Eden Space', heap: true, usada: 100 * MB, confirmada: 200 * MB, maxima: null },
      { nome: 'G1 Old Gen', heap: true, usada: 190 * MB, confirmada: 300 * MB, maxima: 2 * GB },
      { nome: 'G1 Survivor Space', heap: true, usada: 10 * MB, confirmada: 12 * MB, maxima: null },
      { nome: 'Metaspace', heap: false, usada: 120 * MB, confirmada: 125 * MB, maxima: null },
    ],
  },
  coletorDeLixo: {
    coletas: 40,
    tempoMs: 800,
    coletores: [
      { nome: 'G1 Young Generation', coletas: 38, tempoMs: 700 },
      { nome: 'G1 Concurrent GC', coletas: 2, tempoMs: 100 },
    ],
  },
  threads: { ativas: 48, daemon: 30, pico: 55, iniciadas: 300, porEstado: { RUNNABLE: 12, WAITING: 20, TIMED_WAITING: 16, BLOCKED: 0 } },
  http: { requisicoes: 1000, tempoTotalMs: 20000, erros: 2 },
  sistema: {
    nomeSistema: 'Windows 11',
    versaoSistema: '10.0',
    arquitetura: 'amd64',
    jvm: 'OpenJDK 64-Bit Server VM · Eclipse Adoptium',
    versaoJava: '21.0.5+11-LTS',
    pid: 4321,
    inicio: 0,
    tempoAtividadeMs: 3_723_000,
  },
  ...mudancas,
});
