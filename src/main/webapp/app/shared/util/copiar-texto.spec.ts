import { copiarTexto } from './copiar-texto';

describe('copiarTexto', () => {
  const clipboardOriginal = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
  const seguroOriginal = Object.getOwnPropertyDescriptor(window, 'isSecureContext');

  afterEach(() => {
    if (clipboardOriginal) Object.defineProperty(navigator, 'clipboard', clipboardOriginal);
    else delete (navigator as any).clipboard;
    if (seguroOriginal) Object.defineProperty(window, 'isSecureContext', seguroOriginal);
    else delete (window as any).isSecureContext;
    delete (document as any).execCommand;
  });

  // O comando de cópia do navegador dispara o evento "copy": o texto copiado é o
  // que o evento entrega à área de transferência
  const comandoQueCopia = () => {
    const entregue: string[] = [];
    (document as any).execCommand = jest.fn((comando: string) => {
      const dados = new Map<string, string>();
      const evento = new Event('copy', { bubbles: true, cancelable: true }) as any;
      evento.clipboardData = { setData: (tipo: string, valor: string) => dados.set(tipo, valor) };
      document.dispatchEvent(evento);
      entregue.push(dados.get('text/plain') ?? '');
      return comando === 'copy';
    });
    return entregue;
  };

  // http://192.168.x.x da escola: sem navigator.clipboard, e tem de copiar mesmo assim
  it('copia o texto puro pelo comando do navegador, sem precisar de página segura', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    Object.defineProperty(window, 'isSecureContext', { value: false, configurable: true });
    const entregue = comandoQueCopia();

    expect(await copiarTexto('FB5P4D')).toBe(true);
    expect(entregue).toEqual(['FB5P4D']);
    // nada fica para trás na página nem a seleção da pessoa é trocada
    expect(document.body.textContent).not.toContain('FB5P4D');
    expect(window.getSelection()?.toString()).toBe('');
  });

  // O comando roda DENTRO do clique, antes de qualquer espera: a API assíncrona na
  // frente empurrava a cópia para depois do gesto, quando o navegador já recusa
  it('não espera a API assíncrona quando o comando já copiou', async () => {
    const writeText = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
    comandoQueCopia();

    expect(await copiarTexto('ABC234')).toBe(true);
    expect(writeText).not.toHaveBeenCalled();
  });

  it('usa a API do navegador quando o comando é recusado', async () => {
    const writeText = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
    (document as any).execCommand = jest.fn().mockReturnValue(false);

    expect(await copiarTexto('XYZ789')).toBe(true);
    expect(writeText).toHaveBeenCalledWith('XYZ789');
  });

  it('avisa quando nenhum dos dois caminhos funciona', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: jest.fn().mockRejectedValue(new Error('negado')) },
      configurable: true,
    });
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
    (document as any).execCommand = jest.fn().mockReturnValue(false);

    expect(await copiarTexto('ABC234')).toBe(false);
  });

  it('comando que estoura erro não derruba a tela', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    Object.defineProperty(window, 'isSecureContext', { value: false, configurable: true });
    (document as any).execCommand = jest.fn(() => {
      throw new Error('não suportado');
    });

    expect(await copiarTexto('ABC234')).toBe(false);
  });
});
