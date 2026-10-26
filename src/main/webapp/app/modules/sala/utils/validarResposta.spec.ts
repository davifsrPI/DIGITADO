import { MENSAGEM_ERRO, TipoErro, validarResposta } from './validarResposta';
import { TIPOS_ERRO } from 'app/shared/util/tipos-erro';

// O retorno imediato da tela tem de bater com o que o servidor grava
// (ComparadorPalavrasTest usa os mesmos pares): se os dois discordarem, o aluno lê
// "Uma letra trocada" e o histórico conta outra coisa.
describe('validarResposta (a mesma régua do servidor)', () => {
  const casos: [string, string, TipoErro][] = [
    ['cafe', 'café', 'ACENTUACAO'],
    ['caca', 'caça', 'ACENTUACAO'],
    ['guardachuva', 'guarda-chuva', 'ESPACO_HIFEN'],
    ['arco iris', 'arco-íris', 'ESPACO_HIFEN'],
    ['derepente', 'de repente', 'ESPACO_HIFEN'],
    ['xave', 'chave', 'ERRO_FONETICO'],
    ['cassa', 'caça', 'ERRO_FONETICO'],
    ['esseção', 'exceção', 'ERRO_FONETICO'],
    ['jente', 'gente', 'ERRO_FONETICO'],
    ['ora', 'hora', 'ERRO_FONETICO'],
    ['canpo', 'campo', 'ERRO_FONETICO'],
    ['homen', 'homem', 'ERRO_FONETICO'],
    ['brasiu', 'brasil', 'ERRO_FONETICO'],
    ['caro', 'carro', 'ERRO_FONETICO'],
    ['testo', 'texto', 'ERRO_FONETICO'],
    ['pdera', 'pedra', 'LETRAS_INVERTIDAS'],
    ['menimo', 'menino', 'TROCA_LETRA'],
    ['dola', 'bola', 'TROCA_LETRA'],
    ['cavlo', 'cavalo', 'LETRA_FALTANDO'],
    ['piza', 'pizza', 'LETRA_FALTANDO'],
    ['carrro', 'carro', 'LETRA_EXTRA'],
    ['casda', 'casa', 'LETRA_EXTRA'],
    ['trabisseiro', 'travesseiro', 'VARIOS_ERROS'],
    ['caza', 'chácara', 'OUTRA_PALAVRA'],
    ['', 'casa', 'NAO_RESPONDEU'],
  ];

  it.each(casos)('"%s" por "%s" = %s', (digitado, correto, tipo) => {
    const r = validarResposta(digitado, correto);
    expect(r.correta).toBe(false);
    expect(r.tipoErro).toBe(tipo);
  });

  it('acerto ignora maiúscula, espaço nas pontas e acento decomposto', () => {
    expect(validarResposta('  Café ', 'café').correta).toBe(true);
    expect(validarResposta('mão', 'mão').correta).toBe(true);
  });

  it('todo tipo tem mensagem para o aluno e texto para os painéis', () => {
    (Object.keys(MENSAGEM_ERRO) as TipoErro[]).forEach(tipo => {
      expect(MENSAGEM_ERRO[tipo]).toBeTruthy();
      expect(TIPOS_ERRO[tipo]).toBeDefined();
    });
  });

  it('os exemplos dos painéis caem no tipo que explicam', () => {
    Object.entries(TIPOS_ERRO)
      .filter(([tipo]) => !['OUTRO', 'NAO_RESPONDEU'].includes(tipo))
      .forEach(([tipo, { exemplo }]) => {
        const [, digitado, correto] = /^"(.+)" no lugar de "(.+)"$/.exec(exemplo);
        expect(validarResposta(digitado, correto).tipoErro).toBe(tipo);
      });
  });
});
