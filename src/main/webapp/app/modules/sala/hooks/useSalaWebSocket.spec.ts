import { EstadoJogo, corrigirRelogio } from './useSalaWebSocket';

const estado = (timestampInicio: number, timestampServidor: number): EstadoJogo => ({
  tipo: 'NOVA_PALAVRA',
  indiceAtual: 0,
  totalPalavras: 10,
  tempoLimite: 30,
  timestampInicio,
  timestampServidor,
  placar: [],
  nomeSala: 'Turma 5A',
  codigoSala: 'ABC123',
  alunosConectados: [],
});

// Quanto tempo de rodada já correu, do jeito que as telas calculam
const decorrido = (e: EstadoJogo) => Date.now() - e.timestampInicio;

describe('corrigirRelogio', () => {
  const AGORA = 1_700_000_000_000;

  beforeEach(() => jest.spyOn(Date, 'now').mockReturnValue(AGORA));
  afterEach(() => jest.restoreAllMocks());

  it('zera o tempo decorrido no início da rodada mesmo com o relógio do aparelho adiantado', () => {
    // Celular 5 minutos à frente do servidor: sem correção a rodada nasceria vencida
    // e o aluno caía direto no ranking, como se não tivesse respondido a tempo
    const noServidor = AGORA - 5 * 60 * 1000;
    expect(decorrido(estado(noServidor, noServidor))).toBeGreaterThan(30 * 1000);
    expect(decorrido(corrigirRelogio(estado(noServidor, noServidor)))).toBe(0);
  });

  it('zera o tempo decorrido no início da rodada com o relógio do aparelho atrasado', () => {
    const noServidor = AGORA + 5 * 60 * 1000;
    expect(decorrido(corrigirRelogio(estado(noServidor, noServidor)))).toBe(0);
  });

  it('preserva o tempo que já correu para quem entra no meio da rodada', () => {
    // Servidor: rodada começou há 12s. O aluno que chega agora pega os 18s restantes
    const servidorAgora = AGORA - 5 * 60 * 1000;
    const corrigido = corrigirRelogio(estado(servidorAgora - 12_000, servidorAgora));
    expect(decorrido(corrigido)).toBe(12_000);
  });

  it('mantém o estado intacto quando o servidor não mandou a hora', () => {
    const original = estado(AGORA - 3_000, 0);
    expect(corrigirRelogio(original)).toBe(original);
  });
});
