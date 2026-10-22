import { textoProgresso } from './conquistas';

describe('textoProgresso (porcentagem das conquistas)', () => {
  // Antes: 3 de 10 vitórias aparecia como "3%"
  it('mostra o progresso contra a meta', () => {
    expect(textoProgresso({ progresso: 3, meta: 10, percentual: 30 })).toBe('3 de 10 · 30%');
    expect(textoProgresso({ progresso: 0, meta: 5, percentual: 0 })).toBe('0 de 5 · 0%');
  });

  it('não passa da meta no texto', () => {
    expect(textoProgresso({ progresso: 12, meta: 10, percentual: 99 })).toBe('10 de 10 · 99%');
  });

  it('sem meta conhecida não inventa porcentagem', () => {
    expect(textoProgresso({ progresso: 3, meta: null, percentual: null })).toBeNull();
  });
});
