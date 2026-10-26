import { GB, MB, leituraFalsa } from './leitura-falsa';
import { Amostra, acrescentar, calcularAmostra } from './monitor-util';
import { CORES, montarPaineis, nomeDaArea } from './paineis';

const antes = leituraFalsa();
const depois = leituraFalsa({
  instante: antes.instante + 2000,
  coletorDeLixo: { ...antes.coletorDeLixo, coletas: 44, tempoMs: 900 },
  http: { requisicoes: 1010, tempoTotalMs: 20250, erros: 2 },
});
const historico: Amostra[] = acrescentar([calcularAmostra(antes)], calcularAmostra(depois, antes));

const painel = (id: string, leitura = depois, h = historico) => montarPaineis(leitura, h).find(p => p.id === id);
const destaque = (id: string, rotulo: string, leitura = depois) => painel(id, leitura).destaques.find(d => d.rotulo === rotulo)?.valor;

describe('montarPaineis (os recursos do painel Desempenho)', () => {
  it('seis recursos, na ordem da lista, cada um com sua cor', () => {
    const paineis = montarPaineis(depois, historico);
    expect(paineis.map(p => p.id)).toEqual(['cpu', 'memoria', 'jvm', 'gc', 'threads', 'http']);
    expect(new Set(paineis.map(p => p.cor)).size).toBe(6);
    expect(paineis[0].cor).toBe(CORES.cpu);
  });

  it('CPU: máquina e JVM, escala fixa de 100%', () => {
    const cpu = painel('cpu');
    expect(cpu.resumo).toBe('13% · JVM 3,2%');
    expect(cpu.maximo).toBe(100);
    expect(cpu.series.map(s => s.nome)).toEqual(['Máquina inteira', 'DIGITADO (JVM)']);
    expect(destaque('cpu', 'Tempo de atividade')).toBe('0:01:02:03');
    // carga média não existe no Windows: o campo some em vez de mostrar zero
    expect(destaque('cpu', 'Carga média (1 min)')).toBeUndefined();
    expect(destaque('cpu', 'Carga média (1 min)', leituraFalsa({ cpu: { ...antes.cpu, cargaMedia: 1.5 } }))).toBe('1,50');
  });

  it('Memória: uso da máquina e quanto disso é do DIGITADO', () => {
    const memoria = painel('memoria');
    expect(memoria.resumo).toBe('6,0/16,0 GB (38%)');
    expect(memoria.maximo).toBe(16 * GB);
    expect(destaque('memoria', 'Confirmada')).toBe('9,0/24,0 GB');
    // heap + não-heap confirmados + buffers diretos
    expect(destaque('memoria', 'DIGITADO (JVM)')).toBe('692 MB');

    const segmentos = memoria.composicao.segmentos;
    expect(segmentos.map(s => s.rotulo)).toEqual(['DIGITADO (JVM)', 'Outros programas', 'Disponível']);
    expect(segmentos.reduce((soma, s) => soma + s.bytes, 0)).toBe(16 * GB);
  });

  it('Memória fora do Windows: a área de troca é swap', () => {
    const linux = leituraFalsa({ sistema: { ...antes.sistema, nomeSistema: 'Linux' } });
    expect(destaque('memoria', 'Swap em uso', linux)).toBe('9,0/24,0 GB');
    expect(destaque('memoria', 'Confirmada', linux)).toBeUndefined();
  });

  it('Memória da máquina desconhecida: nada de zero inventado', () => {
    const semDados = leituraFalsa({ memoria: { total: null, disponivel: null, emUso: null, confirmada: null, limiteConfirmada: null } });
    const memoria = painel('memoria', semDados);
    expect(memoria.resumo).toBe('—');
    expect(memoria.composicao).toBeUndefined();
    expect(destaque('memoria', 'Em uso', semDados)).toBe('—');
  });

  it('Memória da JVM: resumo contra o -Xmx, escala na heap confirmada e heap por área', () => {
    const jvm = painel('jvm');
    expect(jvm.resumo).toBe('300 MB de 2,0 GB');
    expect(jvm.maximo).toBe(512 * MB);
    expect(jvm.rotuloMaximo).toBe('512 MB');
    // do que vive mais ao que acabou de nascer
    expect(jvm.composicao.segmentos.map(s => s.rotulo)).toEqual(['Old Gen', 'Survivor Space', 'Eden Space', 'Livre']);
    expect(jvm.composicao.total).toBe(512 * MB);
    expect(jvm.composicao.segmentos.find(s => s.rotulo === 'Livre').bytes).toBe(212 * MB);
    expect(jvm.especificacoes).toEqual([{ rotulo: 'Metaspace', valor: '120 MB de 125 MB' }]);
    expect(destaque('jvm', 'Heap máxima')).toBe('2,0 GB');
  });

  it('Memória da JVM com o -Xmx padrão (1/4 da RAM): o gráfico não vira uma linha no chão', () => {
    // o que o app de desenvolvimento mostrou de verdade: 75 MB em uso, 176 MB confirmados, 7,9 GB de máximo
    const real = leituraFalsa({ memoriaJvm: { ...antes.memoriaJvm, heapUsada: 75 * MB, heapConfirmada: 176 * MB, heapMaxima: 8120 * MB } });
    const jvm = painel('jvm', real, [calcularAmostra(real)]);
    expect(jvm.maximo).toBe(200 * MB);
    expect(jvm.rotuloMaximo).toBe('200 MB');
    expect(jvm.resumo).toBe('75 MB de 7,9 GB');
  });

  it('Memória da JVM perto do -Xmx: a escala para no máximo', () => {
    const cheia = leituraFalsa({
      memoriaJvm: { ...antes.memoriaJvm, heapUsada: 650 * MB, heapConfirmada: 700 * MB, heapMaxima: 768 * MB },
    });
    const jvm = painel('jvm', cheia, [calcularAmostra(cheia)]);
    // o teto redondo seria 1,0 GB; o -Xmx de 768 MB vem antes
    expect(jvm.maximo).toBe(768 * MB);
    expect(jvm.rotuloMaximo).toBe('768 MB (máximo)');
  });

  it('Memória da JVM sem -Xmx: resumo contra a heap confirmada', () => {
    const semTeto = leituraFalsa({ memoriaJvm: { ...antes.memoriaJvm, heapMaxima: null } });
    const jvm = painel('jvm', semTeto, [calcularAmostra(semTeto)]);
    expect(jvm.maximo).toBe(512 * MB);
    expect(jvm.resumo).toBe('300 MB de 512 MB');
    expect(destaque('jvm', 'Heap máxima', semTeto)).toBe('sem limite');
  });

  it('Coletor de lixo e HTTP: taxas do último intervalo', () => {
    expect(painel('gc').resumo).toBe('5% do tempo');
    expect(destaque('gc', 'Coletas por segundo')).toBe('2,0');
    expect(painel('http').resumo).toBe('5,0 req/s');
    expect(destaque('http', 'Tempo médio de resposta')).toBe('25 ms');
    expect(destaque('http', 'Erros 5xx desde o início')).toBe('2');
  });

  it('primeira leitura: taxas aparecem como "—", não como zero', () => {
    const primeira = [calcularAmostra(antes)];
    expect(painel('gc', antes, primeira).resumo).toBe('—');
    expect(painel('http', antes, primeira).resumo).toBe('—');
  });

  it('Threads: estados em português', () => {
    const threads = painel('threads');
    expect(threads.resumo).toBe('48 ativas');
    expect(threads.especificacoes.map(e => e.rotulo)).toEqual(['Executando', 'Aguardando', 'Aguardando (com prazo)', 'Bloqueadas']);
  });

  it('nome da área sem o prefixo do coletor', () => {
    expect(nomeDaArea('G1 Eden Space')).toBe('Eden Space');
    expect(nomeDaArea('PS Old Gen')).toBe('Old Gen');
    expect(nomeDaArea('Metaspace')).toBe('Metaspace');
  });
});
