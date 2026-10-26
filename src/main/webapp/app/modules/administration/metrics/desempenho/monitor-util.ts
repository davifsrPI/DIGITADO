// Leitura de GET /management/desempenho (MonitorDesempenho.java) e as contas que
// a tela faz em cima dela. Tudo aqui é função pura: a parte que dá para testar
// sem navegador nem servidor.

export interface AreaMemoria {
  nome: string;
  heap: boolean;
  usada: number;
  confirmada: number;
  maxima: number | null;
}

export interface LeituraDesempenho {
  instante: number;
  cpu: { maquina: number | null; processo: number | null; processadores: number; cargaMedia: number | null };
  memoria: {
    total: number | null;
    disponivel: number | null;
    emUso: number | null;
    confirmada: number | null;
    limiteConfirmada: number | null;
  };
  memoriaJvm: {
    heapUsada: number;
    heapConfirmada: number;
    heapMaxima: number | null;
    naoHeapUsada: number;
    naoHeapConfirmada: number;
    buffersDiretos: number;
    classesCarregadas: number;
    areas: AreaMemoria[];
  };
  coletorDeLixo: { coletas: number; tempoMs: number; coletores: { nome: string; coletas: number; tempoMs: number }[] };
  threads: { ativas: number; daemon: number; pico: number; iniciadas: number; porEstado: Record<string, number> };
  http: { requisicoes: number; tempoTotalMs: number; erros: number };
  sistema: {
    nomeSistema: string;
    versaoSistema: string;
    arquitetura: string;
    jvm: string;
    versaoJava: string;
    pid: number | null;
    inicio: number;
    tempoAtividadeMs: number;
  };
}

/**
 * Um ponto do histórico dos gráficos. CPU, memória e threads são do instante; o
 * resto é TAXA, tirada da diferença para a leitura anterior - nulo quando não há
 * anterior (primeira leitura) ou quando o contador andou para trás (o servidor
 * reiniciou entre uma leitura e outra).
 */
export interface Amostra {
  instante: number;
  cpuMaquina: number | null;
  cpuProcesso: number | null;
  memoriaEmUso: number | null;
  heapUsada: number;
  heapConfirmada: number;
  gcPercentual: number | null;
  gcColetasPorSegundo: number | null;
  threads: number;
  requisicoesPorSegundo: number | null;
  tempoMedioMs: number | null;
}

/** Quantos pontos cada gráfico guarda: 60, como o Gerenciador de Tarefas (60 s na velocidade normal). */
export const CAPACIDADE_HISTORICO = 60;

export const calcularAmostra = (atual: LeituraDesempenho, anterior?: LeituraDesempenho): Amostra => {
  const base: Amostra = {
    instante: atual.instante,
    cpuMaquina: atual.cpu.maquina,
    cpuProcesso: atual.cpu.processo,
    memoriaEmUso: atual.memoria.emUso,
    heapUsada: atual.memoriaJvm.heapUsada,
    heapConfirmada: atual.memoriaJvm.heapConfirmada,
    gcPercentual: null,
    gcColetasPorSegundo: null,
    threads: atual.threads.ativas,
    requisicoesPorSegundo: null,
    tempoMedioMs: null,
  };
  const intervaloMs = anterior ? atual.instante - anterior.instante : 0;
  if (!anterior || intervaloMs <= 0) {
    return base;
  }
  const segundos = intervaloMs / 1000;

  const tempoGc = atual.coletorDeLixo.tempoMs - anterior.coletorDeLixo.tempoMs;
  const coletas = atual.coletorDeLixo.coletas - anterior.coletorDeLixo.coletas;
  if (tempoGc >= 0 && coletas >= 0) {
    base.gcPercentual = Math.min(100, (tempoGc / intervaloMs) * 100);
    base.gcColetasPorSegundo = coletas / segundos;
  }

  const requisicoes = atual.http.requisicoes - anterior.http.requisicoes;
  const tempoHttp = atual.http.tempoTotalMs - anterior.http.tempoTotalMs;
  if (requisicoes >= 0 && tempoHttp >= 0) {
    base.requisicoesPorSegundo = requisicoes / segundos;
    base.tempoMedioMs = requisicoes > 0 ? tempoHttp / requisicoes : null;
  }
  return base;
};

/** Põe a amostra no fim e descarta as mais antigas além da capacidade. */
export const acrescentar = (historico: Amostra[], amostra: Amostra, capacidade = CAPACIDADE_HISTORICO): Amostra[] => {
  const novo = [...historico, amostra];
  return novo.length > capacidade ? novo.slice(novo.length - capacidade) : novo;
};

/**
 * Teto "redondo" para a escala de um gráfico que se ajusta sozinho (threads,
 * requisições, GC): 1, 2, 2,5 ou 5 vezes uma potência de 10, nunca abaixo do mínimo.
 */
export const tetoDaEscala = (valores: (number | null)[], minimo: number): number => {
  const maior = Math.max(minimo, ...valores.filter((v): v is number => v !== null && Number.isFinite(v)));
  const potencia = Math.pow(10, Math.floor(Math.log10(maior)));
  const passo = [1, 2, 2.5, 5, 10].find(m => m * potencia >= maior) ?? 10;
  return passo * potencia;
};

/**
 * O mesmo teto redondo, mas contado na unidade que o rótulo vai mostrar (base 1024):
 * "200 MB", "512 MB", "2,5 GB" - e não os "191 MB" de 2 * 10^8 bytes.
 */
export const tetoDeBytes = (valores: (number | null)[], minimo: number): number => {
  const maior = Math.max(minimo, ...valores.filter((v): v is number => v !== null && Number.isFinite(v)));
  let unidade = 1;
  // acima de 500 sobe de unidade: 600 MB vira 1,0 GB em vez de "1.000 MB"
  while (maior / unidade > 500) {
    unidade *= 1024;
  }
  return tetoDaEscala([], maior / unidade) * unidade;
};

const numero = (casas: number) => new Intl.NumberFormat('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });

export const formatarNumero = (valor: number, casas = 0): string => numero(casas).format(valor);

/** "12%"; abaixo de 10%, com uma casa ("0,4%") - o tempo em GC costuma ser pequeno. Inteiro fica inteiro. */
export const formatarPercentual = (valor: number | null | undefined): string => {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) {
    return '—';
  }
  return `${formatarNumero(valor, valor < 10 && !Number.isInteger(valor) ? 1 : 0)}%`;
};

const UNIDADES = ['B', 'KB', 'MB', 'GB', 'TB'];

/** Base 1024, como o Gerenciador de Tarefas: "312 MB", "5,2 GB". */
export const formatarBytes = (bytes: number | null | undefined): string => {
  if (bytes === null || bytes === undefined || !Number.isFinite(bytes) || bytes < 0) {
    return '—';
  }
  let valor = bytes;
  let unidade = 0;
  while (valor >= 1024 && unidade < UNIDADES.length - 1) {
    valor /= 1024;
    unidade++;
  }
  return `${formatarNumero(valor, unidade >= 3 ? 1 : 0)} ${UNIDADES[unidade]}`;
};

/** "5,2/15,8 GB": as duas partes na unidade da maior, como o Gerenciador de Tarefas. */
export const formatarFracaoBytes = (parte: number | null, todo: number | null): string => {
  if (parte === null || todo === null || todo <= 0) {
    return '—';
  }
  const [, unidadeTexto] = formatarBytes(todo).split(' ');
  const unidade = UNIDADES.indexOf(unidadeTexto);
  const divisor = Math.pow(1024, unidade);
  const casas = unidade >= 3 ? 1 : 0;
  return `${formatarNumero(parte / divisor, casas)}/${formatarNumero(todo / divisor, casas)} ${unidadeTexto}`;
};

/** Tempo de atividade no formato do Gerenciador de Tarefas: d:hh:mm:ss. */
export const formatarDuracao = (ms: number): string => {
  const total = Math.max(0, Math.floor(ms / 1000));
  const dias = Math.floor(total / 86400);
  const doisDigitos = (n: number) => String(n).padStart(2, '0');
  return `${dias}:${doisDigitos(Math.floor((total % 86400) / 3600))}:${doisDigitos(Math.floor((total % 3600) / 60))}:${doisDigitos(total % 60)}`;
};

/** "1,8 s" a partir de 1 s; "350 ms" abaixo disso. */
export const formatarMs = (ms: number | null | undefined): string => {
  if (ms === null || ms === undefined || !Number.isFinite(ms)) {
    return '—';
  }
  return ms >= 1000 ? `${formatarNumero(ms / 1000, 1)} s` : `${formatarNumero(ms, 0)} ms`;
};
