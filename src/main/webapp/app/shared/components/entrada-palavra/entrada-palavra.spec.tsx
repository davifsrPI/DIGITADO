import React, { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { EntradaPalavra, TipoBurla } from './entrada-palavra';

// Wrapper controlado: reproduz como as telas de jogo usam o componente
const Harness: React.FC<{ onBurla?: (tipo: TipoBurla) => void; maxLength?: number }> = ({ onBurla, maxLength }) => {
  const [valor, setValor] = useState('');
  return <EntradaPalavra ariaLabel="resposta" value={valor} onChange={setValor} onBurla={onBurla} maxLength={maxLength} />;
};

const getInput = () => screen.getByLabelText<HTMLInputElement>('resposta');

// Simula o tipo de dispositivo: (pointer: coarse) = celular/tablet, senão desktop
const simularDispositivo = (toque: boolean) => {
  window.matchMedia = jest.fn().mockImplementation((query: string) => ({
    matches: toque && query === '(pointer: coarse)',
    media: query,
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
  })) as unknown as typeof window.matchMedia;
  Object.defineProperty(navigator, 'maxTouchPoints', { value: toque ? 5 : 0, configurable: true });
};

// Digitação com tecla morta do teclado físico (ABNT2): o navegador põe o acento no
// campo, avisa que está compondo e só depois entrega a vogal acentuada
const digitarComAcento = (input: HTMLInputElement, base: string, acento: string, composto: string) => {
  fireEvent.compositionStart(input);
  fireEvent.change(input, { target: { value: base + acento } });
  fireEvent.change(input, { target: { value: base + composto } });
  fireEvent.compositionEnd(input, { target: { value: base + composto } });
};

describe('EntradaPalavra', () => {
  beforeEach(() => simularDispositivo(false));

  it('aceita digitação letra a letra, normalizando para minúsculas', () => {
    render(<Harness />);
    const input = getInput();
    fireEvent.change(input, { target: { value: 'C' } });
    fireEvent.change(input, { target: { value: 'ca' } });
    fireEvent.change(input, { target: { value: 'caç' } });
    fireEvent.change(input, { target: { value: 'caçá' } });
    expect(input.value).toBe('caçá');
  });

  it('remove caracteres fora do alfabeto português (números, símbolos)', () => {
    render(<Harness />);
    const input = getInput();
    fireEvent.change(input, { target: { value: 'a' } });
    fireEvent.change(input, { target: { value: 'a1' } });
    fireEvent.change(input, { target: { value: 'a!' } });
    expect(input.value).toBe('a');
  });

  it('descarta inserção de vários caracteres de uma vez (corretor/autofill) e notifica burla', () => {
    const onBurla = jest.fn();
    render(<Harness onBurla={onBurla} />);
    const input = getInput();
    fireEvent.change(input, { target: { value: 'c' } });
    // Corretor injetando a palavra inteira num único evento
    fireEvent.change(input, { target: { value: 'cachorro' } });
    expect(input.value).toBe('c');
    expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
  });

  it('bloqueia colar e notifica burla', () => {
    const onBurla = jest.fn();
    render(<Harness onBurla={onBurla} />);
    const input = getInput();
    const evento = fireEvent.paste(input, { clipboardData: { getData: () => 'paralelepipedo' } });
    // preventDefault chamado → fireEvent retorna false
    expect(evento).toBe(false);
    expect(onBurla).toHaveBeenCalledWith('colagem');
    expect(input.value).toBe('');
  });

  it('respeita o maxLength', () => {
    render(<Harness maxLength={3} />);
    const input = getInput();
    fireEvent.change(input, { target: { value: 'a' } });
    fireEvent.change(input, { target: { value: 'ab' } });
    fireEvent.change(input, { target: { value: 'abc' } });
    fireEvent.change(input, { target: { value: 'abcd' } });
    expect(input.value).toBe('abc');
  });

  it('suprime o teclado nativo do celular via inputMode="none"', () => {
    simularDispositivo(true);
    render(<Harness />);
    expect(getInput().getAttribute('inputmode')).toBe('none');
  });

  // Sem teclado virtual na tela, suprimir o teclado do sistema deixaria o aluno
  // sem nenhuma forma de digitar - era o que travava o campo na sala
  it('não suprime o teclado do sistema em dispositivo sem tela de toque', () => {
    simularDispositivo(false);
    render(<Harness />);
    expect(getInput().getAttribute('inputmode')).toBeNull();
  });

  describe('acentuação com tecla morta (teclado físico ABNT2)', () => {
    it('compõe ´ + a = á no meio da palavra', () => {
      render(<Harness />);
      const input = getInput();
      fireEvent.change(input, { target: { value: 'c' } });
      fireEvent.change(input, { target: { value: 'ca' } });
      fireEvent.change(input, { target: { value: 'caf' } });
      digitarComAcento(input, 'caf', '´', 'é');
      expect(input.value).toBe('café');
    });

    it('compõe ~ + a = ã', () => {
      render(<Harness />);
      const input = getInput();
      fireEvent.change(input, { target: { value: 'a' } });
      fireEvent.change(input, { target: { value: 'av' } });
      fireEvent.change(input, { target: { value: 'avi' } });
      digitarComAcento(input, 'avi', '~', 'ã');
      fireEvent.change(input, { target: { value: 'avião' } });
      expect(input.value).toBe('avião');
    });

    it('não deixa a tecla morta sozinha no campo quando a composição é cancelada', () => {
      render(<Harness />);
      const input = getInput();
      fireEvent.compositionStart(input);
      fireEvent.change(input, { target: { value: '´' } });
      fireEvent.compositionEnd(input, { target: { value: '´' } });
      expect(input.value).toBe('');
    });

    it('descarta a palavra inteira sugerida pelo corretor durante a composição', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      fireEvent.change(input, { target: { value: 'c' } });
      fireEvent.compositionStart(input);
      fireEvent.change(input, { target: { value: 'cachorro' } });
      fireEvent.compositionEnd(input, { target: { value: 'cachorro' } });
      expect(input.value).toBe('c');
      expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
    });
  });

  describe('teclado virtual', () => {
    it('insere letras tocando nas teclas', () => {
      render(<Harness />);
      fireEvent.pointerDown(screen.getByRole('button', { name: 'c' }));
      fireEvent.pointerDown(screen.getByRole('button', { name: 'a' }));
      expect(getInput().value).toBe('ca');
    });

    it('compõe acento com tecla morta: ´ + a = á', () => {
      render(<Harness />);
      fireEvent.pointerDown(screen.getByRole('button', { name: '´' }));
      fireEvent.pointerDown(screen.getByRole('button', { name: 'a' }));
      expect(getInput().value).toBe('á');
    });

    it('descarta o acento pendente quando a letra não compõe: ~ + t = t', () => {
      render(<Harness />);
      fireEvent.pointerDown(screen.getByRole('button', { name: '~' }));
      fireEvent.pointerDown(screen.getByRole('button', { name: 't' }));
      expect(getInput().value).toBe('t');
    });

    it('apaga a última letra com o backspace', () => {
      render(<Harness />);
      fireEvent.pointerDown(screen.getByRole('button', { name: 'o' }));
      fireEvent.pointerDown(screen.getByRole('button', { name: 'i' }));
      fireEvent.pointerDown(screen.getByRole('button', { name: 'Apagar' }));
      expect(getInput().value).toBe('o');
    });
  });
});
