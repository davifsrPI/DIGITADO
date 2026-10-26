import React from 'react';
import axios from 'axios';
import { act, fireEvent, render, screen, within } from '@testing-library/react';

import { LeituraRequisicoes, RequisicoesPorApi, URL_REQUISICOES, montarLinhas, ordenar } from './requisicoes-api';

jest.mock('axios');
const get = axios.get as jest.Mock;

const respostas = () => act(() => Promise.resolve());

const rota = (metodo: string, uri: string, requisicoes: number, mudancas = {}) => ({
  metodo,
  uri,
  requisicoes,
  tempoTotalMs: requisicoes * 20,
  maximoMs: 80,
  sucesso: requisicoes,
  erros4xx: 0,
  erros5xx: 0,
  porStatus: { '200': requisicoes },
  excecoes: {},
  ...mudancas,
});

const leitura = (mudancas: Partial<LeituraRequisicoes> = {}): LeituraRequisicoes => ({
  instante: 10_000,
  rotas: [
    rota('GET', '/api/salas/{codigo}', 100, {
      sucesso: 90,
      erros4xx: 8,
      erros5xx: 2,
      porStatus: { '200': 90, '404': 8, '500': 2 },
      excecoes: { IllegalStateException: 2 },
    }),
    rota('POST', '/api/account', 10),
  ],
  errosRecentes: [
    {
      instante: 9_000,
      metodo: 'GET',
      rota: '/api/salas/{codigo}',
      caminho: '/api/salas/ABC123',
      status: 500,
      duracaoMs: 312,
      excecao: 'java.lang.IllegalStateException',
      mensagem: 'falhou ao montar a sala',
      causaRaiz: 'java.lang.IllegalArgumentException: código inválido',
      pilha: ['» br.com.digitado.service.SalaService.montar(SalaService.java:42)', 'java.base/java.lang.Thread.run(Thread.java:1583)'],
    },
    {
      instante: 8_000,
      metodo: 'GET',
      rota: null,
      caminho: '/api/admin/desempenho',
      status: 403,
      duracaoMs: 3,
      excecao: null,
      mensagem: null,
      causaRaiz: null,
      pilha: [],
    },
  ],
  errosRegistrados: 57,
  ...mudancas,
});

describe('montarLinhas (taxa de cada API entre duas leituras)', () => {
  it('primeira leitura: totais sim, req/s ainda não', () => {
    const [sala] = montarLinhas(leitura());
    expect(sala.porSegundo).toBeNull();
    expect(sala.tempoMedioMs).toBe(20);
    expect(sala.percentualErro).toBe(10);
  });

  it('req/s sai da diferença; rota nova conta inteira; contador que voltou não vira negativo', () => {
    const antes = leitura();
    const depois = leitura({
      instante: 12_000,
      rotas: [rota('GET', '/api/salas/{codigo}', 110), rota('POST', '/api/account', 4), rota('GET', '/api/nova', 6)],
    });
    const linhas = montarLinhas(depois, antes);
    expect(linhas.find(l => l.uri === '/api/salas/{codigo}').porSegundo).toBe(5);
    expect(linhas.find(l => l.uri === '/api/nova').porSegundo).toBe(3);
    expect(linhas.find(l => l.uri === '/api/account').porSegundo).toBeNull();
  });

  it('ordena por qualquer coluna, nos dois sentidos', () => {
    const linhas = montarLinhas(leitura());
    expect(ordenar(linhas, 'requisicoes', true).map(l => l.metodo)).toEqual(['GET', 'POST']);
    expect(ordenar(linhas, 'requisicoes', false).map(l => l.metodo)).toEqual(['POST', 'GET']);
    expect(ordenar(linhas, 'api', false).map(l => l.uri)).toEqual(['/api/account', '/api/salas/{codigo}']);
  });
});

describe('RequisicoesPorApi', () => {
  afterEach(() => get.mockReset());

  it('mostra uma linha por API e abre as respostas por status e as exceções', async () => {
    get.mockResolvedValue({ data: leitura() });
    render(<RequisicoesPorApi intervalo={1000} />);
    await respostas();

    expect(get).toHaveBeenCalledWith(URL_REQUISICOES);
    const tabela = screen.getByRole('table');
    const linhas = within(tabela).getAllByRole('row');
    // cabeçalho + 2 APIs
    expect(linhas).toHaveLength(3);
    expect(linhas[1].textContent).toContain('/api/salas/{codigo}');
    expect(linhas[1].textContent).toContain('10%');

    fireEvent.click(within(linhas[1]).getByRole('button'));
    expect(screen.getByText('Respostas por status')).toBeTruthy();
    expect(screen.getByText(/IllegalStateException/, { selector: '.rq-chips li' })).toBeTruthy();
  });

  it('filtra as APIs pela rota e só as que têm erro', async () => {
    get.mockResolvedValue({ data: leitura() });
    render(<RequisicoesPorApi intervalo={1000} />);
    await respostas();

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'account' } });
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(2);
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('checkbox'));
    const linhas = within(screen.getByRole('table')).getAllByRole('row');
    expect(linhas).toHaveLength(2);
    expect(linhas[1].textContent).toContain('/api/salas/{codigo}');
  });

  it('lista os erros com exceção, causa raiz e pilha, e filtra por 4xx/5xx', async () => {
    get.mockResolvedValue({ data: leitura() });
    render(<RequisicoesPorApi intervalo={1000} />);
    await respostas();

    expect(screen.getByText(/57 desde que o servidor subiu/)).toBeTruthy();
    const erros = () => document.querySelectorAll('.rq-erro');
    expect(erros()).toHaveLength(2);
    expect(erros()[0].textContent).toContain('IllegalStateException: falhou ao montar a sala');
    expect(erros()[0].textContent).toContain('java.lang.IllegalArgumentException: código inválido');
    expect(erros()[0].querySelector('.rq-pilha').textContent).toContain('» br.com.digitado.service.SalaService.montar');
    // o 403 não tem exceção nem rota: o filtro de segurança barrou antes
    expect(erros()[1].textContent).toContain('sem exceção');
    expect(erros()[1].textContent).toContain('nenhuma (barrado antes de chegar a uma rota)');

    const filtro = within(screen.getByRole('group', { name: 'Filtrar erros por status' }));
    fireEvent.click(filtro.getByRole('button', { name: /^5xx/ }));
    expect(erros()).toHaveLength(1);
    fireEvent.click(filtro.getByRole('button', { name: /^4xx/ }));
    expect(erros()).toHaveLength(1);
    expect(erros()[0].textContent).toContain('403');
  });

  it('pausado lê uma vez e para; nunca mais rápido que 1 s', async () => {
    jest.useFakeTimers();
    get.mockResolvedValue({ data: leitura() });
    const { rerender } = render(<RequisicoesPorApi intervalo={0} />);
    await respostas();
    await act(() => {
      jest.advanceTimersByTime(5000);
      return Promise.resolve();
    });
    expect(get).toHaveBeenCalledTimes(1);

    rerender(<RequisicoesPorApi intervalo={500} />);
    await respostas();
    const depois = get.mock.calls.length;
    await act(() => {
      jest.advanceTimersByTime(600);
      return Promise.resolve();
    });
    expect(get.mock.calls.length).toBe(depois);
    await act(() => {
      jest.advanceTimersByTime(500);
      return Promise.resolve();
    });
    expect(get.mock.calls.length).toBe(depois + 1);
    jest.useRealTimers();
  });
});
