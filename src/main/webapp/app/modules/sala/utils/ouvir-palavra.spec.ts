jest.mock('axios');
jest.mock('./falar-palavra', () => ({ falarPalavra: jest.fn() }));

// Um <audio> de mentira: guarda o src e responde ao play() como mandarmos
class AudioFalso {
  static instancias: AudioFalso[] = [];
  static recusar: string | null = null;
  src = '';
  currentTime = 0;
  preload = '';
  onended: (() => void) | null = null;
  onerror: (() => void) | null = null;
  tocou: string[] = [];
  constructor() {
    AudioFalso.instancias.push(this);
  }
  play() {
    this.tocou.push(this.src);
    if (AudioFalso.recusar) return Promise.reject(Object.assign(new Error('bloqueado'), { name: AudioFalso.recusar }));
    return Promise.resolve();
  }
  pause() {}
}

const esperar = () => new Promise(r => setTimeout(r, 0));

describe('ouvirPalavraDaRodada', () => {
  let modulo: typeof import('./ouvir-palavra');
  // Recarregado a cada teste junto com o módulo: o resetModules cria outro mock do axios
  let get: jest.Mock;

  beforeEach(() => {
    jest.resetModules();
    AudioFalso.instancias = [];
    AudioFalso.recusar = null;
    (global as any).Audio = AudioFalso;
    (URL as any).createObjectURL = jest.fn(() => `blob:${Math.random()}`);
    (URL as any).revokeObjectURL = jest.fn();
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    get = require('axios').get as jest.Mock;
    get.mockReset();
    get.mockResolvedValue({ data: new Blob(['wav']) });
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    modulo = require('./ouvir-palavra');
  });

  it('busca o som da sala quando o texto não veio e toca', async () => {
    modulo.ouvirPalavraDaRodada({ codigoSala: 'ABC234', chave: 7, pausaMs: 0 });
    await esperar();
    await esperar();
    expect(get).toHaveBeenCalledWith('/api/salas/ABC234/audio', { responseType: 'blob' });
    expect(AudioFalso.instancias).toHaveLength(1);
    expect(AudioFalso.instancias[0].tocou[0]).toMatch(/^blob:/);
  });

  // "Ouvir de novo" toca na hora, dentro do clique - é o que funciona no iPhone
  it('reouvir a mesma palavra não volta ao servidor e usa sempre o mesmo elemento', async () => {
    modulo.ouvirPalavraDaRodada({ codigoSala: 'ABC234', chave: 7, pausaMs: 0 });
    await esperar();
    await esperar();
    modulo.ouvirPalavraDaRodada({ codigoSala: 'ABC234', chave: 7, pausaMs: 0 });
    // tocou sem esperar nada: síncrono, dentro do gesto
    expect(AudioFalso.instancias[0].tocou).toHaveLength(2);
    expect(get).toHaveBeenCalledTimes(1);
    expect(AudioFalso.instancias).toHaveLength(1);
  });

  it('palavra nova busca de novo', async () => {
    modulo.ouvirPalavraDaRodada({ codigoSala: 'ABC234', chave: 7, pausaMs: 0 });
    await esperar();
    modulo.ouvirPalavraDaRodada({ codigoSala: 'ABC234', chave: 8, pausaMs: 0 });
    await esperar();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('serve ao modo individual pelo endereço próprio', async () => {
    modulo.ouvirPalavraDaRodada({ urlAudio: '/api/individual/audio', chave: 1, pausaMs: 0 });
    await esperar();
    expect(get).toHaveBeenCalledWith('/api/individual/audio', { responseType: 'blob' });
  });

  it('avisa a tela quando o navegador recusa tocar sem um toque', async () => {
    AudioFalso.recusar = 'NotAllowedError';
    const bloqueado = jest.fn();
    const fim = jest.fn();
    modulo.ouvirPalavraDaRodada({ codigoSala: 'ABC234', chave: 9, pausaMs: 0, onBloqueadoPeloNavegador: bloqueado, onEnd: fim });
    await esperar();
    await esperar();
    await esperar();
    expect(bloqueado).toHaveBeenCalled();
    expect(fim).toHaveBeenCalled();
  });

  it('com o texto no estado (servidor sem sintetizador), quem fala é o navegador', () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { falarPalavra } = require('./falar-palavra');
    modulo.ouvirPalavraDaRodada({ codigoSala: 'ABC234', texto: 'casa' });
    expect(falarPalavra).toHaveBeenCalledWith('casa', expect.anything());
    expect(get).not.toHaveBeenCalled();
  });
});
