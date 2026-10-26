import React from 'react';
import axios from 'axios';
import { act, fireEvent, render, screen } from '@testing-library/react';

import { leituraFalsa } from './leitura-falsa';
import { PainelDesempenho, URL_DESEMPENHO } from './painel-desempenho';

jest.mock('axios');
const get = axios.get as jest.Mock;

// Deixa a resposta do axios (uma promessa) chegar ao componente dentro do act
const respostas = () => act(() => Promise.resolve());
const avancar = (ms: number) =>
  act(() => {
    jest.advanceTimersByTime(ms);
    return Promise.resolve();
  });

const titulo = () => screen.getByRole('heading', { level: 3 }).textContent;
const itemDaLista = (nome: string) => screen.getByText(nome, { selector: '.pd-item-titulo' }).closest('button');

describe('PainelDesempenho (aba Desempenho de Métricas)', () => {
  afterEach(() => {
    get.mockReset();
    jest.useRealTimers();
  });

  it('mostra a lista de recursos e o escolhido em tamanho grande', async () => {
    get.mockResolvedValue({ data: leituraFalsa() });
    render(<PainelDesempenho />);

    expect(await screen.findByRole('heading', { level: 3 })).toBeTruthy();
    expect(get).toHaveBeenCalledWith(URL_DESEMPENHO);
    expect(titulo()).toBe('CPU');
    expect(itemDaLista('CPU').getAttribute('aria-pressed')).toBe('true');
    expect(screen.getAllByText('13% · JVM 3,2%').length).toBeGreaterThan(0);
    expect(screen.getByRole('status').textContent).toContain('Ao vivo');

    fireEvent.click(itemDaLista('Memória'));
    expect(titulo()).toBe('Memória');
    expect(itemDaLista('Memória').getAttribute('aria-pressed')).toBe('true');
    expect(itemDaLista('CPU').getAttribute('aria-pressed')).toBe('false');
    expect(screen.getByText('Composição da memória')).toBeTruthy();
  });

  it('lê de novo no intervalo escolhido, sem sobrepor leituras', async () => {
    jest.useFakeTimers();
    get.mockResolvedValue({ data: leituraFalsa() });
    render(<PainelDesempenho />);
    await respostas();
    expect(get).toHaveBeenCalledTimes(1);

    await avancar(1000);
    expect(get).toHaveBeenCalledTimes(2);

    // Alta: meio segundo
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '500' } });
    await respostas();
    const depoisDaTroca = get.mock.calls.length;
    await avancar(500);
    expect(get.mock.calls.length).toBe(depoisDaTroca + 1);
  });

  it('pausada não pergunta mais nada ao servidor', async () => {
    jest.useFakeTimers();
    get.mockResolvedValue({ data: leituraFalsa() });
    render(<PainelDesempenho />);
    await respostas();

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '0' } });
    const chamadas = get.mock.calls.length;
    await avancar(10_000);
    expect(get.mock.calls.length).toBe(chamadas);
    expect(screen.getByRole('status').textContent).toContain('Pausado');
  });

  it('servidor fora do ar: avisa e continua tentando', async () => {
    get.mockRejectedValue(new Error('Network Error'));
    render(<PainelDesempenho />);
    expect(await screen.findByText('Não foi possível ler o desempenho do servidor.')).toBeTruthy();
    expect(screen.getByRole('status').textContent).toContain('Sem resposta do servidor');
  });
});
