import * as fs from 'fs';
import * as path from 'path';

import { ORDEM_TIPOS_ERRO, SUBTIPOS_ERRO, SUBTIPOS_POR_TIPO, TIPOS_ERRO, TIPOS_ERRO_ANTIGOS, temSubtiposErro } from './tipos-erro';

// O painel do admin mostra o catálogo inteiro, com zero no que ainda não aconteceu:
// ele tem de bater com o que o servidor (ComparadorPalavras) consegue gravar
describe('catálogo de tipos e subtipos de erro', () => {
  it('cada subtipo pertence a um tipo só, e todo tipo com subtipo existe', () => {
    const listados = Object.values(SUBTIPOS_POR_TIPO).flat();
    expect([...listados].sort()).toEqual(Object.keys(SUBTIPOS_ERRO).sort());
    expect(new Set(listados).size).toBe(listados.length);
    Object.keys(SUBTIPOS_POR_TIPO).forEach(tipo => expect(TIPOS_ERRO[tipo]).toBeDefined());
    expect(TIPOS_ERRO_ANTIGOS.every(tipo => TIPOS_ERRO[tipo])).toBe(true);
  });

  it('só outra palavra, não respondeu e o antigo ficam sem subtipo', () => {
    expect(ORDEM_TIPOS_ERRO.filter(tipo => !temSubtiposErro(tipo))).toEqual(['OUTRA_PALAVRA', 'OUTRO', 'NAO_RESPONDEU']);
  });

  it('tem exatamente os tipos e subtipos que o servidor grava', () => {
    const java = fs.readFileSync(path.resolve(__dirname, '../../../../java/br/com/digitado/service/ComparadorPalavras.java'), 'utf8');
    const doServidor = new Set(Array.from(java.matchAll(/"([A-Z][A-Z_]+)"/g), m => m[1]));
    expect([...doServidor].sort()).toEqual([...ORDEM_TIPOS_ERRO, ...Object.keys(SUBTIPOS_ERRO)].sort());
  });
});
