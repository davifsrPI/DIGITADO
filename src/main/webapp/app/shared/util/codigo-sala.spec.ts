import { extrairCodigoColado, limparCodigoSala } from './codigo-sala';

describe('código da sala colado no campo', () => {
  it('o código sozinho entra como está', () => {
    expect(extrairCodigoColado('FB5P4D')).toBe('FB5P4D');
    expect(extrairCodigoColado('  fb5p4d\n')).toBe('FB5P4D');
  });

  // Antes: limpar e cortar nos 6 primeiros caracteres dava "ENTREM"
  it('acha o código no meio da mensagem do professor', () => {
    expect(extrairCodigoColado('Entrem na sala FB5P4D!')).toBe('FB5P4D');
    expect(extrairCodigoColado('Código da sala: 8H4XEZ (turma 5A)')).toBe('8H4XEZ');
  });

  it('entre duas palavras de 6 letras, vence a que tem número', () => {
    expect(extrairCodigoColado('Sala ESCOLA codigo KQ7MPA')).toBe('KQ7MPA');
  });

  it('sem nenhuma palavra de 6, limpa como se tivesse sido digitado', () => {
    expect(extrairCodigoColado('FB-5P-4D')).toBe('FB5P4D');
    expect(extrairCodigoColado('abc')).toBe('ABC');
  });

  it('a limpeza da digitação continua igual', () => {
    expect(limparCodigoSala('fb5 p4d-xyz')).toBe('FB5P4D');
  });
});
