import type { SerieGrafico } from './grafico-desempenho';
import {
  Amostra,
  LeituraDesempenho,
  formatarBytes,
  formatarDuracao,
  formatarFracaoBytes,
  formatarMs,
  formatarNumero,
  formatarPercentual,
  tetoDaEscala,
  tetoDeBytes,
} from './monitor-util';

// Os seis recursos do painel "Desempenho" - o que a lista lateral resume e o que o
// quadro da direita mostra de cada um. Montado a cada leitura a partir da última
// leitura do servidor e do histórico de amostras.

export type IdPainel = 'cpu' | 'memoria' | 'jvm' | 'gc' | 'threads' | 'http';

export interface Dado {
  rotulo: string;
  valor: string;
}

/** Um pedaço da barra "Composição": opacidade 0 = parte livre (só o contorno). */
export interface SegmentoComposicao {
  rotulo: string;
  bytes: number;
  opacidade: number;
}

export interface Painel {
  id: IdPainel;
  titulo: string;
  cor: string;
  /** A linha de baixo do item da lista lateral ("12% · JVM 3%"). */
  resumo: string;
  /** O texto no canto direito do título, como o modelo do processador no Gerenciador de Tarefas. */
  subtitulo: string;
  series: SerieGrafico[];
  maximo: number;
  rotuloEixo: string;
  rotuloMaximo: string;
  formatar: (valor: number) => string;
  destaques: Dado[];
  especificacoes: Dado[];
  composicao?: { titulo: string; total: number; segmentos: SegmentoComposicao[] };
  nota?: string;
}

// Uma cor por recurso, na ordem da lista: azul da CPU e roxo da memória como no
// Gerenciador de Tarefas. Conferida no validador de paleta (separação para
// daltonismo e contraste com o fundo claro).
export const CORES: Record<IdPainel, string> = {
  cpu: '#117DBB',
  memoria: '#8B12AE',
  jvm: '#4DA60C',
  gc: '#00A0B4',
  threads: '#D6336C',
  http: '#A74F01',
};

const ROTULOS_ESTADO: Record<string, string> = {
  RUNNABLE: 'Executando',
  WAITING: 'Aguardando',
  TIMED_WAITING: 'Aguardando (com prazo)',
  BLOCKED: 'Bloqueadas',
  NEW: 'Novas',
  TERMINATED: 'Encerradas',
};

/** "G1 Eden Space" -> "Eden Space": o prefixo do coletor só repete o que o painel do GC já diz. */
export const nomeDaArea = (nome: string): string => nome.replace(/^(G1|PS|Par|Shenandoah|ZGC)\s+/i, '');

// Ordem da barra do heap: o que vive muito primeiro, o que acabou de nascer por último
const ordemDaArea = (nome: string): number => [/old|tenured/i, /survivor/i, /eden/i, /./].findIndex(padrao => padrao.test(nome));

const OPACIDADES_HEAP = [0.55, 0.4, 0.28, 0.2];

const coluna = (historico: Amostra[], campo: keyof Omit<Amostra, 'instante'>): (number | null)[] => historico.map(a => a[campo]);

const ultimo = (valores: (number | null)[]): number | null => (valores.length ? valores[valores.length - 1] : null);

const pct = (parte: number | null, todo: number | null): number | null =>
  parte === null || todo === null || todo <= 0 ? null : (parte / todo) * 100;

/** O "(33%)" do resumo: inteiro, como no Gerenciador de Tarefas. */
const pctInteiro = (parte: number | null, todo: number | null): number | null => {
  const valor = pct(parte, todo);
  return valor === null ? null : Math.round(valor);
};

const painelCpu = (l: LeituraDesempenho, h: Amostra[]): Painel => ({
  id: 'cpu',
  titulo: 'CPU',
  cor: CORES.cpu,
  resumo: `${formatarPercentual(l.cpu.maquina)}${l.cpu.processo === null ? '' : ` · JVM ${formatarPercentual(l.cpu.processo)}`}`,
  subtitulo: `${l.sistema.nomeSistema} · ${l.cpu.processadores} processadores lógicos`,
  series: [
    { nome: 'Máquina inteira', valores: coluna(h, 'cpuMaquina'), estilo: 'area' },
    { nome: 'DIGITADO (JVM)', valores: coluna(h, 'cpuProcesso'), estilo: 'area-forte' },
  ],
  maximo: 100,
  rotuloEixo: '% de utilização',
  rotuloMaximo: '100%',
  formatar: formatarPercentual,
  destaques: [
    { rotulo: 'Utilização', valor: formatarPercentual(l.cpu.maquina) },
    { rotulo: 'DIGITADO (JVM)', valor: formatarPercentual(l.cpu.processo) },
    { rotulo: 'Threads', valor: formatarNumero(l.threads.ativas) },
    { rotulo: 'Tempo de atividade', valor: formatarDuracao(l.sistema.tempoAtividadeMs) },
    ...(l.cpu.cargaMedia === null ? [] : [{ rotulo: 'Carga média (1 min)', valor: formatarNumero(l.cpu.cargaMedia, 2) }]),
  ],
  especificacoes: [
    { rotulo: 'Processadores lógicos', valor: String(l.cpu.processadores) },
    { rotulo: 'Sistema', valor: `${l.sistema.nomeSistema} ${l.sistema.versaoSistema}` },
    { rotulo: 'Arquitetura', valor: l.sistema.arquitetura },
    { rotulo: 'JVM', valor: l.sistema.jvm },
    { rotulo: 'Java', valor: l.sistema.versaoJava },
    { rotulo: 'PID', valor: l.sistema.pid === null ? '—' : String(l.sistema.pid) },
    { rotulo: 'Iniciado em', valor: new Date(l.sistema.inicio).toLocaleString('pt-BR') },
  ],
});

const painelMemoria = (l: LeituraDesempenho, h: Amostra[]): Painel => {
  const { total, emUso, disponivel, confirmada, limiteConfirmada } = l.memoria;
  const jvm = l.memoriaJvm;
  // O quanto a JVM tem reservado do sistema: aproximação, mas é o número que
  // responde "o DIGITADO é que está comendo a memória?"
  const reservadaJvm = jvm.heapConfirmada + jvm.naoHeapConfirmada + jvm.buffersDiretos;
  const doDigitado = emUso === null ? reservadaJvm : Math.min(reservadaJvm, emUso);
  const windows = /^windows/i.test(l.sistema.nomeSistema);
  const rotuloConfirmada = windows ? 'Confirmada' : 'Swap em uso';
  return {
    id: 'memoria',
    titulo: 'Memória',
    cor: CORES.memoria,
    resumo: total === null ? '—' : `${formatarFracaoBytes(emUso, total)} (${formatarPercentual(pctInteiro(emUso, total))})`,
    subtitulo: total === null ? 'memória da máquina indisponível' : `${formatarBytes(total)} de memória física`,
    series: [{ nome: 'Em uso', valores: coluna(h, 'memoriaEmUso'), estilo: 'area' }],
    maximo: total ?? 1,
    rotuloEixo: 'Uso de memória da máquina',
    rotuloMaximo: formatarBytes(total),
    formatar: formatarBytes,
    destaques: [
      { rotulo: 'Em uso', valor: formatarBytes(emUso) },
      { rotulo: 'Disponível', valor: formatarBytes(disponivel) },
      { rotulo: rotuloConfirmada, valor: formatarFracaoBytes(confirmada, limiteConfirmada) },
      { rotulo: 'DIGITADO (JVM)', valor: formatarBytes(reservadaJvm) },
    ],
    especificacoes: [
      { rotulo: 'Total', valor: formatarBytes(total) },
      { rotulo: 'Uso', valor: formatarPercentual(pct(emUso, total)) },
      { rotulo: 'Outros programas', valor: emUso === null ? '—' : formatarBytes(Math.max(0, emUso - doDigitado)) },
    ],
    composicao:
      total === null || emUso === null
        ? undefined
        : {
            titulo: 'Composição da memória',
            total,
            segmentos: [
              { rotulo: 'DIGITADO (JVM)', bytes: doDigitado, opacidade: 0.55 },
              { rotulo: 'Outros programas', bytes: Math.max(0, emUso - doDigitado), opacidade: 0.22 },
              { rotulo: 'Disponível', bytes: disponivel ?? 0, opacidade: 0 },
            ],
          },
    nota: `"DIGITADO (JVM)" soma heap, não-heap e buffers diretos que a JVM reservou: uma aproximação.${
      windows ? ' "Confirmada" é a carga de confirmação do Windows (RAM + arquivo de paginação).' : ''
    }`,
  };
};

const painelJvm = (l: LeituraDesempenho, h: Amostra[]): Painel => {
  const jvm = l.memoriaJvm;
  const usadas = coluna(h, 'heapUsada');
  const confirmadas = coluna(h, 'heapConfirmada');
  // A escala acompanha a heap confirmada (o que a JVM já pegou do sistema), não o
  // -Xmx: o padrão é 1/4 da RAM, e 80 MB em uso de 8 GB seria uma linha no chão
  const teto = Math.min(tetoDeBytes([...usadas, ...confirmadas], jvm.heapConfirmada || 1), jvm.heapMaxima ?? Infinity);
  const limite = jvm.heapMaxima ?? jvm.heapConfirmada;
  const areasHeap = jvm.areas.filter(a => a.heap).sort((a, b) => ordemDaArea(a.nome) - ordemDaArea(b.nome));
  const naoHeap = jvm.areas.filter(a => !a.heap);
  return {
    id: 'jvm',
    titulo: 'Memória da JVM',
    cor: CORES.jvm,
    // "146 MB de 7,9 GB": em "0,1/7,9 GB" o uso, que é o que interessa, some no arredondamento
    resumo: `${formatarBytes(jvm.heapUsada)} de ${formatarBytes(limite)}`,
    subtitulo: `Java ${l.sistema.versaoJava}`,
    series: [
      { nome: 'Heap em uso', valores: usadas, estilo: 'area' },
      { nome: 'Heap confirmada', valores: confirmadas, estilo: 'tracejada' },
    ],
    maximo: teto,
    rotuloEixo: 'Heap',
    rotuloMaximo: `${formatarBytes(teto)}${teto === jvm.heapMaxima ? ' (máximo)' : ''}`,
    formatar: formatarBytes,
    destaques: [
      { rotulo: 'Heap em uso', valor: formatarBytes(jvm.heapUsada) },
      { rotulo: 'Heap confirmada', valor: formatarBytes(jvm.heapConfirmada) },
      { rotulo: 'Heap máxima', valor: jvm.heapMaxima === null ? 'sem limite' : formatarBytes(jvm.heapMaxima) },
      { rotulo: 'Não-heap em uso', valor: formatarBytes(jvm.naoHeapUsada) },
      { rotulo: 'Buffers diretos', valor: formatarBytes(jvm.buffersDiretos) },
      { rotulo: 'Classes carregadas', valor: formatarNumero(jvm.classesCarregadas) },
    ],
    especificacoes: naoHeap.map(a => ({
      rotulo: nomeDaArea(a.nome),
      valor: `${formatarBytes(a.usada)} de ${formatarBytes(a.confirmada)}`,
    })),
    composicao: {
      titulo: 'Composição da heap confirmada',
      total: Math.max(jvm.heapConfirmada, jvm.heapUsada),
      segmentos: [
        ...areasHeap.map((a, i) => ({
          rotulo: nomeDaArea(a.nome),
          bytes: a.usada,
          opacidade: OPACIDADES_HEAP[Math.min(i, OPACIDADES_HEAP.length - 1)],
        })),
        { rotulo: 'Livre', bytes: Math.max(0, jvm.heapConfirmada - jvm.heapUsada), opacidade: 0.06 },
      ],
    },
    nota:
      'O gráfico vai até a heap confirmada, que a JVM aumenta conforme precisa, até a heap máxima (-Xmx). ' +
      'Metaspace, CodeHeap e Compressed Class Space são a memória fora do heap (classes carregadas e código compilado): em uso de confirmada.',
  };
};

const painelGc = (l: LeituraDesempenho, h: Amostra[]): Painel => {
  const valores = coluna(h, 'gcPercentual');
  const maximo = tetoDaEscala(valores, 1);
  const agora = ultimo(valores);
  return {
    id: 'gc',
    titulo: 'Coletor de lixo',
    cor: CORES.gc,
    resumo: agora === null ? '—' : `${formatarPercentual(agora)} do tempo`,
    subtitulo: l.coletorDeLixo.coletores.map(c => c.nome).join(' · '),
    series: [{ nome: 'Tempo em coleta', valores, estilo: 'area' }],
    maximo,
    rotuloEixo: '% do tempo em coleta de lixo',
    rotuloMaximo: `${formatarNumero(maximo, maximo < 10 ? 1 : 0)}%`,
    formatar: formatarPercentual,
    destaques: [
      { rotulo: 'Tempo em coleta', valor: formatarPercentual(agora) },
      { rotulo: 'Coletas por segundo', valor: formatarNumero(ultimo(coluna(h, 'gcColetasPorSegundo')) ?? 0, 1) },
      { rotulo: 'Coletas desde o início', valor: formatarNumero(l.coletorDeLixo.coletas) },
      { rotulo: 'Tempo total em coleta', valor: formatarMs(l.coletorDeLixo.tempoMs) },
    ],
    especificacoes: l.coletorDeLixo.coletores.map(c => ({
      rotulo: c.nome,
      valor: `${formatarNumero(c.coletas)} coletas · ${formatarMs(c.tempoMs)}`,
    })),
    nota: 'Quanto do intervalo a aplicação passou coletando lixo. Picos frequentes acima de 10% pedem mais heap (-Xmx).',
  };
};

const painelThreads = (l: LeituraDesempenho, h: Amostra[]): Painel => {
  const valores = coluna(h, 'threads');
  const maximo = tetoDaEscala(valores, 10);
  return {
    id: 'threads',
    titulo: 'Threads',
    cor: CORES.threads,
    resumo: `${formatarNumero(l.threads.ativas)} ativas`,
    subtitulo: `pico de ${formatarNumero(l.threads.pico)}`,
    series: [{ nome: 'Threads ativas', valores, estilo: 'area' }],
    maximo,
    rotuloEixo: 'Threads ativas',
    rotuloMaximo: formatarNumero(maximo),
    formatar: v => formatarNumero(v),
    destaques: [
      { rotulo: 'Ativas', valor: formatarNumero(l.threads.ativas) },
      { rotulo: 'Daemon', valor: formatarNumero(l.threads.daemon) },
      { rotulo: 'Pico', valor: formatarNumero(l.threads.pico) },
      { rotulo: 'Iniciadas desde o início', valor: formatarNumero(l.threads.iniciadas) },
    ],
    especificacoes: Object.entries(l.threads.porEstado).map(([estado, quantas]) => ({
      rotulo: ROTULOS_ESTADO[estado] ?? estado,
      valor: formatarNumero(quantas),
    })),
    nota: 'A lista de threads com a pilha de cada uma está na aba Detalhes.',
  };
};

const painelHttp = (l: LeituraDesempenho, h: Amostra[]): Painel => {
  const valores = coluna(h, 'requisicoesPorSegundo');
  const maximo = tetoDaEscala(valores, 1);
  const agora = ultimo(valores);
  return {
    id: 'http',
    titulo: 'Requisições HTTP',
    cor: CORES.http,
    resumo: agora === null ? '—' : `${formatarNumero(agora, 1)} req/s`,
    subtitulo: `${formatarNumero(l.http.requisicoes)} desde o início`,
    series: [{ nome: 'Requisições por segundo', valores, estilo: 'area' }],
    maximo,
    rotuloEixo: 'Requisições por segundo',
    rotuloMaximo: formatarNumero(maximo, maximo < 10 ? 1 : 0),
    formatar: v => `${formatarNumero(v, 1)} req/s`,
    destaques: [
      { rotulo: 'Requisições por segundo', valor: agora === null ? '—' : formatarNumero(agora, 1) },
      { rotulo: 'Tempo médio de resposta', valor: formatarMs(ultimo(coluna(h, 'tempoMedioMs'))) },
      { rotulo: 'Total desde o início', valor: formatarNumero(l.http.requisicoes) },
      { rotulo: 'Erros 5xx desde o início', valor: formatarNumero(l.http.erros) },
    ],
    especificacoes: [],
    nota: 'Não conta as consultas deste painel. Tempo médio das requisições que chegaram no último intervalo.',
  };
};

export const montarPaineis = (leitura: LeituraDesempenho, historico: Amostra[]): Painel[] => [
  painelCpu(leitura, historico),
  painelMemoria(leitura, historico),
  painelJvm(leitura, historico),
  painelGc(leitura, historico),
  painelThreads(leitura, historico),
  painelHttp(leitura, historico),
];
