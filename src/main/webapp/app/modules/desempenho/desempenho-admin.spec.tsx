import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import axios from 'axios';

import DesempenhoAdmin, { Trecho, errosSemDetalhe } from './desempenho-admin';

// Um painel com o essencial preenchido: os números gerais não importam aqui, só os
// erros destrinchados (subtipos, trechos trocados e o erro mais comum da palavra)
const painel = (mudancas = {}) => ({
  totalRespostas: 40,
  totalAcertos: 30,
  totalErros: 10,
  totalNaoRespondeu: 1,
  taxaGeral: 75,
  totalAlunos: 3,
  alunosBons: 2,
  alunosMedios: 1,
  alunosRuins: 0,
  desenvolvimento: {
    alunosAvaliados: 0,
    mediaTaxaInicial: 0,
    mediaTaxaAtual: 0,
    mediaEvolucao: 0,
    alunosMelhoraram: 0,
    alunosEstaveis: 0,
    alunosPioraram: 0,
    resumo: 'Ninguém com 10 respostas.',
  },
  relatorioMensal: {
    mesAtual: '2026-10',
    mesAnterior: '2026-09',
    respostasMesAtual: 40,
    respostasMesAnterior: 0,
    acertosMesAtual: 30,
    errosMesAtual: 10,
    acertosMesAnterior: 0,
    errosMesAnterior: 0,
    taxaMesAtual: 75,
    taxaMesAnterior: 0,
    variacaoTaxa: 0,
    variacaoRespostas: 40,
    maisAcertosQueErros: true,
    balancoDoMes: 'Mais acertos.',
    veredito: 'SEM_COMPARATIVO',
    resumo: 'Primeiro mês.',
  },
  evolucao: [],
  palavrasMaisAcertadas: [],
  palavrasMaisErradas: [{ palavraId: 7, texto: 'pássaro', dificuldade: 'MEDIO', total: 8, acertos: 2, erros: 6, taxa: 75 }],
  errosPorTipo: [
    { tipo: 'ERRO_FONETICO', total: 6, percentual: 60 },
    { tipo: 'ACENTUACAO', total: 3, percentual: 30 },
    { tipo: 'NAO_RESPONDEU', total: 1, percentual: 10 },
  ],
  subtiposErro: [
    { tipo: 'ERRO_FONETICO', subtipo: 'SOM_S_Z', total: 6, percentualDoTipo: 100 },
    { tipo: 'ACENTUACAO', subtipo: 'ACENTO_FALTANDO', total: 2, percentualDoTipo: 67 },
    { tipo: 'ACENTUACAO', subtipo: null, total: 1, percentualDoTipo: 33 },
    { tipo: 'NAO_RESPONDEU', subtipo: null, total: 1, percentualDoTipo: 100 },
  ],
  confusoes: [
    {
      tipo: 'ERRO_FONETICO',
      subtipo: 'SOM_S_Z',
      detalhe: 'ss→s',
      ocorrencias: 4,
      alunos: 3,
      palavras: 5,
      percentualDosErros: 40,
      exemplos: ['assado', 'missa', 'pássaro', 'passo'],
    },
  ],
  erroMaisComumPorPalavra: { 7: { tipo: 'ERRO_FONETICO', subtipo: 'SOM_S_Z', detalhe: 'ss→s', ocorrencias: 4, percentualDosErros: 67 } },
  filtro: null,
  ...mudancas,
});

const montar = async (dados = painel()) => {
  jest.spyOn(axios, 'get').mockResolvedValue({ data: dados });
  render(
    <MemoryRouter>
      <DesempenhoAdmin />
    </MemoryRouter>,
  );
  await act(() => Promise.resolve());
};

describe('Desempenho geral: erros destrinchados', () => {
  afterEach(() => jest.restoreAllMocks());

  it('abre cada tipo em todos os subtipos e avisa dos registros antigos', async () => {
    await montar();
    const listas = Array.from(document.querySelectorAll('.da-subtipos')).map(l => l.textContent);
    // os oito tipos com subtipo, do mais frequente para o menos; "não respondeu" não tem o que abrir
    expect(listas).toHaveLength(8);
    expect(listas[0].startsWith('Som de S/Z6 · 100%')).toBe(true);
    // o que ainda não aconteceu aparece com zero
    expect(listas[0]).toContain('CH ou X0 · 0%');
    // o erro gravado antes da classificação aparece como "sem detalhe", não some
    expect(listas[1]).toContain('Faltou o acento2 · 67%');
    expect(listas[1]).toContain('Sem detalhe (registro antigo)1 · 33%');
    expect(listas[1]).toContain('Acento trocado0 · 0%');
    expect(document.querySelectorAll('.da-subtipo--zero').length).toBeGreaterThan(30);
    expect(screen.getByText(/1 erro\(s\) foram gravados antes da classificação detalhada/)).toBeTruthy();
  });

  it('lista todos os tipos, com zero nos que não aconteceram e sem o "outro" antigo', async () => {
    await montar();
    const tipos = Array.from(document.querySelectorAll('.da-tipo-bloco')).map(b => b.getAttribute('data-tipo'));
    expect(tipos.slice(0, 3)).toEqual(['ERRO_FONETICO', 'ACENTUACAO', 'NAO_RESPONDEU']);
    expect(tipos).toHaveLength(10);
    expect(tipos).not.toContain('OUTRO');
    expect(document.querySelector('[data-tipo="LETRAS_INVERTIDAS"].da-tipo-bloco').textContent).toContain('0 · 0%');
  });

  it('o "outro" antigo aparece quando tem registro', async () => {
    await montar(painel({ errosPorTipo: [{ tipo: 'OUTRO', total: 2, percentual: 100 }], subtiposErro: [] }));
    expect(document.querySelector('.da-tipo-bloco').getAttribute('data-tipo')).toBe('OUTRO');
    const filtro = screen.getByRole('group', { name: 'Filtrar por tipo de erro' });
    expect(within(filtro).getByText(/Outro erro \(antigo\)/)).toBeTruthy();
  });

  it('sem nenhuma resposta, mostra mesmo assim todos os erros que vão ser contados', async () => {
    await montar(painel({ totalRespostas: 0, totalAcertos: 0, totalErros: 0, totalNaoRespondeu: 0, errosPorTipo: [], subtiposErro: [] }));
    expect(screen.getByText(/Ainda não há respostas de alunos registradas/)).toBeTruthy();
    expect(document.querySelectorAll('.da-tipo-bloco')).toHaveLength(10);
    // os 40 jeitos de errar, todos com zero
    expect(document.querySelectorAll('.da-subtipo')).toHaveLength(40);
    expect(document.querySelectorAll('.da-subtipo--zero')).toHaveLength(40);
    const filtro = screen.getByRole('group', { name: 'Filtrar por tipo de erro' });
    expect(within(filtro).getAllByRole('button')).toHaveLength(11);
  });

  it('filtrado por um tipo sem registro, mostra os jeitos dele com zero', async () => {
    const filtro = {
      tipo: 'LETRAS_INVERTIDAS',
      ocorrencias: 0,
      percentualDosErros: 0,
      percentualDasRespostas: 0,
      alunosAfetados: 0,
      palavrasAfetadas: 0,
      taxaMesAtual: 0,
      taxaMesAnterior: 0,
      veredito: 'SEM_DADOS',
      resumo: 'Nenhuma resposta registrada neste mês nem no anterior.',
      evolucao: [],
      palavras: [],
      subtipos: [],
      confusoes: [],
      erroMaisComumPorPalavra: {},
    };
    await montar(painel({ totalRespostas: 0, errosPorTipo: [], subtiposErro: [], filtro }));
    expect(screen.getByText('Como este erro acontece')).toBeTruthy();
    const lista = document.querySelector('.da-subtipos').textContent;
    expect(lista).toContain('Vogal e consoante0 · 0%');
    expect(lista).toContain('Duas consoantes0 · 0%');
    expect(lista).toContain('Duas vogais0 · 0%');
  });

  it('lista o que a turma mais troca, com onde aconteceu', async () => {
    await montar();
    const item = screen.getByText(/em assado, missa, pássaro, passo/).closest('button').textContent;
    expect(item).toContain('4 vezes');
    expect(item).toContain('3 alunos · 40% dos erros');
    // 5 palavras, 4 de exemplo
    expect(item).toContain('e mais 1');
    expect(item).toContain('ss→s');
  });

  it('mostra o erro mais comum de cada palavra do ranking', async () => {
    await montar();
    const linha = screen.getByText('Erro mais comum:').parentElement.textContent;
    expect(linha).toContain('Erro fonético');
    expect(linha).toContain('ss→s');
    expect(linha).toContain('4× · 67% dos erros');
  });

  it('chips de todos os tipos (os sem registro apagados); clicar num trecho filtra pelo tipo dele', async () => {
    await montar();
    const filtro = screen.getByRole('group', { name: 'Filtrar por tipo de erro' });
    expect(within(filtro).getByText(/Letras invertidas/).className).toContain('da-filtro-chip--zero');
    expect(within(filtro).getByText(/Erro fonético/).className).not.toContain('da-filtro-chip--zero');
    expect(within(filtro).queryByText(/Outro erro \(antigo\)/)).toBeNull();

    const get = axios.get as jest.Mock;
    get.mockClear();
    fireEvent.click(screen.getByText(/em assado, missa/).closest('button'));
    await act(() => Promise.resolve());
    expect(get).toHaveBeenCalledWith('/api/admin/desempenho', { params: { tipoErro: 'ERRO_FONETICO' } });
  });
});

describe('Trecho (certo→digitado)', () => {
  it('lado vazio vira traço, espaço vira ␣ e acento traz vários pares', () => {
    const { container } = render(<Trecho detalhe="a→" />);
    expect(container.textContent).toBe('a→—');
    const { container: espaco } = render(<Trecho detalhe="a-c→a c" />);
    expect(espaco.textContent).toBe('a-c→a␣c');
    const { container: pares } = render(<Trecho detalhe="a→á, é→e" />);
    expect(pares.querySelectorAll('.da-trecho-par')).toHaveLength(2);
  });

  it('errosSemDetalhe não conta "não respondeu" nem "outra palavra"', () => {
    expect(
      errosSemDetalhe([
        { tipo: 'ACENTUACAO', subtipo: null, total: 2, percentualDoTipo: 50 },
        { tipo: 'OUTRO', subtipo: null, total: 3, percentualDoTipo: 100 },
        { tipo: 'NAO_RESPONDEU', subtipo: null, total: 5, percentualDoTipo: 100 },
        { tipo: 'OUTRA_PALAVRA', subtipo: null, total: 7, percentualDoTipo: 100 },
        { tipo: 'ACENTUACAO', subtipo: 'ACENTO_FALTANDO', total: 2, percentualDoTipo: 50 },
      ]),
    ).toBe(5);
  });
});
