import React, { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { EntradaPalavra, TipoBurla, medirEdicao } from './entrada-palavra';

// Wrapper controlado: reproduz como as telas de jogo usam o componente
const Harness: React.FC<{ onBurla?: (tipo: TipoBurla) => void; maxLength?: number; className?: string; placeholder?: string }> = ({
  onBurla,
  maxLength,
  className,
  placeholder,
}) => {
  const [valor, setValor] = useState('');
  return (
    <EntradaPalavra
      ariaLabel="resposta"
      value={valor}
      onChange={setValor}
      onBurla={onBurla}
      maxLength={maxLength}
      className={className}
      placeholder={placeholder}
    />
  );
};

const getInput = () => screen.getByLabelText<HTMLInputElement>('resposta');

// Digita letra a letra, como o teclado físico entrega ao campo
const digitar = (input: HTMLInputElement, palavra: string) => {
  let atual = input.value;
  for (const letra of palavra) {
    atual += letra;
    fireEvent.change(input, { target: { value: atual } });
  }
};

// Digitação com tecla morta do teclado físico (ABNT2): o navegador põe o acento no
// campo, avisa que está compondo e só depois entrega a vogal acentuada
const digitarComAcento = (input: HTMLInputElement, base: string, acento: string, composto: string) => {
  fireEvent.compositionStart(input);
  fireEvent.change(input, { target: { value: base + acento } });
  fireEvent.change(input, { target: { value: base + composto } });
  fireEvent.compositionEnd(input, { target: { value: base + composto } });
};

// beforeinput nativo: é por ele que o navegador anuncia DE ONDE o texto está vindo
const beforeInput = (input: HTMLInputElement, inputType: string) => {
  const evento = new InputEvent('beforeinput', { inputType, cancelable: true, bubbles: true });
  input.dispatchEvent(evento);
  return evento.defaultPrevented;
};

describe('EntradaPalavra', () => {
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

  it('apaga letra a letra com o backspace', () => {
    render(<Harness />);
    const input = getInput();
    digitar(input, 'casa');
    fireEvent.change(input, { target: { value: 'cas' } });
    fireEvent.change(input, { target: { value: 'ca' } });
    expect(input.value).toBe('ca');
  });

  // Edição no meio da palavra: o teclado do aparelho é quem move o cursor, o campo só
  // precisa aceitar a mudança sem reescrever nada
  it('aceita apagar e inserir no meio da palavra', () => {
    render(<Harness />);
    const input = getInput();
    digitar(input, 'casa');
    // backspace com o cursor entre "ca" e "sa"
    fireEvent.change(input, { target: { value: 'csa' } });
    expect(input.value).toBe('csa');
    // volta a letra no mesmo lugar
    fireEvent.change(input, { target: { value: 'casa' } });
    expect(input.value).toBe('casa');
  });

  // Apagar não traz texto de fora, então limpar o campo de uma vez é edição normal -
  // não pode ser confundido com burla nem travar o campo
  it('aceita limpar o campo de uma vez (selecionar tudo e apagar)', () => {
    const onBurla = jest.fn();
    render(<Harness onBurla={onBurla} />);
    const input = getInput();
    digitar(input, 'cachorro');
    fireEvent.change(input, { target: { value: '' } });
    expect(input.value).toBe('');
    expect(onBurla).not.toHaveBeenCalled();
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

  // O teclado do sistema PODE abrir: é nele que o aluno digita, com os acentos que
  // aparecem ao segurar a letra. Quem barra o corretor é a guarda de edição.
  it('usa o teclado normal do aparelho', () => {
    render(<Harness />);
    expect(getInput().getAttribute('inputmode')).toBe('text');
  });

  it('pede ao teclado que não corrija nem sugira', () => {
    render(<Harness />);
    const input = getInput();
    expect(input.getAttribute('autocorrect')).toBe('off');
    expect(input.getAttribute('autocapitalize')).toBe('none');
    expect(input.getAttribute('autocomplete')).toBe('off');
    expect(input.getAttribute('spellcheck')).toBe('false');
  });

  describe('bloqueio de texto que não foi digitado', () => {
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

    it('bloqueia copiar e recortar', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      expect(fireEvent.copy(input)).toBe(false);
      expect(fireEvent.cut(input)).toBe(false);
      expect(onBurla).toHaveBeenCalledWith('copia');
    });

    it('bloqueia os atalhos de copiar, recortar e colar do teclado físico', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      expect(fireEvent.keyDown(input, { key: 'v', ctrlKey: true })).toBe(false);
      expect(onBurla).toHaveBeenLastCalledWith('colagem');
      expect(fireEvent.keyDown(input, { key: 'c', ctrlKey: true })).toBe(false);
      expect(onBurla).toHaveBeenLastCalledWith('copia');
      expect(fireEvent.keyDown(input, { key: 'x', metaKey: true })).toBe(false);
      expect(onBurla).toHaveBeenLastCalledWith('copia');
    });

    it('bloqueia o menu de toque longo, de onde saem "Colar" e "Substituir..."', () => {
      render(<Harness />);
      expect(fireEvent.contextMenu(getInput())).toBe(false);
    });

    it('bloqueia arrastar texto para dentro do campo', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      expect(fireEvent.drop(getInput())).toBe(false);
      expect(onBurla).toHaveBeenCalledWith('arrasto');
    });

    it('bloqueia a troca da palavra inteira sem mudar o tamanho', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      digitar(input, 'csaa');
      // corretor arrumando "csaa" para "casa": mesmo tamanho, duas letras trocadas
      fireEvent.change(input, { target: { value: 'casa' } });
      expect(input.value).toBe('csaa');
      expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
    });
  });

  /*
   * beforeinput diz de onde vem o texto. Só entra o que uma tecla produz: colar,
   * arrastar, autocompletar, desfazer e a substituição do corretor são recusados
   * antes mesmo de chegarem ao campo.
   */
  describe('origem do texto (beforeinput)', () => {
    it('deixa passar o que a tecla digita e o que o backspace apaga', () => {
      render(<Harness />);
      const input = getInput();
      expect(beforeInput(input, 'insertText')).toBe(false);
      expect(beforeInput(input, 'insertCompositionText')).toBe(false);
      expect(beforeInput(input, 'deleteContentBackward')).toBe(false);
      expect(beforeInput(input, 'deleteWordBackward')).toBe(false);
    });

    it('recusa colagem, arrasto, correção automática, recorte e desfazer', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      expect(beforeInput(input, 'insertFromPaste')).toBe(true);
      expect(onBurla).toHaveBeenLastCalledWith('colagem');
      expect(beforeInput(input, 'insertFromDrop')).toBe(true);
      expect(onBurla).toHaveBeenLastCalledWith('arrasto');
      expect(beforeInput(input, 'insertReplacementText')).toBe(true);
      expect(onBurla).toHaveBeenLastCalledWith('correcao-automatica');
      expect(beforeInput(input, 'deleteByCut')).toBe(true);
      expect(onBurla).toHaveBeenLastCalledWith('copia');
      expect(beforeInput(input, 'historyUndo')).toBe(true);
      expect(onBurla).toHaveBeenLastCalledWith('insercao-multipla');
    });
  });

  describe('acentuação com tecla morta (teclado físico ABNT2)', () => {
    it('compõe ´ + a = á no meio da palavra', () => {
      render(<Harness />);
      const input = getInput();
      digitar(input, 'caf');
      digitarComAcento(input, 'caf', '´', 'é');
      expect(input.value).toBe('café');
    });

    it('compõe ~ + a = ã', () => {
      render(<Harness />);
      const input = getInput();
      digitar(input, 'avi');
      digitarComAcento(input, 'avi', '~', 'ã');
      fireEvent.change(input, { target: { value: 'avião' } });
      expect(input.value).toBe('avião');
    });

    // Navegador que não chega a exibir o acento sozinho: a troca da letra pela letra
    // acentuada só é aceita porque a tecla morta acabou de ser pressionada
    it('aceita a troca da letra pela acentuada logo depois da tecla morta', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      digitar(input, 'a');
      fireEvent.keyDown(input, { key: 'Dead' });
      fireEvent.compositionStart(input);
      fireEvent.change(input, { target: { value: 'á' } });
      fireEvent.compositionEnd(input, { target: { value: 'á' } });
      expect(input.value).toBe('á');
      expect(onBurla).not.toHaveBeenCalled();
    });

    it('não deixa a tecla morta sozinha no campo quando a composição é cancelada', () => {
      render(<Harness />);
      const input = getInput();
      fireEvent.compositionStart(input);
      fireEvent.change(input, { target: { value: '´' } });
      fireEvent.compositionEnd(input, { target: { value: '´' } });
      expect(input.value).toBe('');
    });
  });

  /*
   * Teclado do celular (Gboard e afins): a palavra inteira é uma composição só, que
   * cresce uma letra por vez enquanto o aluno digita e é confirmada no fim. Medir o
   * crescimento pela composição inteira faria a digitação normal ser recusada.
   */
  describe('teclado do celular (composição da palavra inteira)', () => {
    // Digita dentro de uma composição, uma letra por evento, e confirma no fim
    const digitarCompondo = (input: HTMLInputElement, palavra: string) => {
      fireEvent.compositionStart(input);
      let atual = '';
      for (const letra of palavra) {
        atual += letra;
        fireEvent.change(input, { target: { value: atual } });
      }
      fireEvent.compositionEnd(input, { target: { value: atual } });
    };

    it('aceita a palavra digitada letra a letra dentro de uma composição', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      digitarCompondo(input, 'cachorro');
      expect(input.value).toBe('cachorro');
      expect(onBurla).not.toHaveBeenCalled();
    });

    it('aceita apagar durante a composição', () => {
      render(<Harness />);
      const input = getInput();
      fireEvent.compositionStart(input);
      for (const parcial of ['c', 'ca', 'cas']) {
        fireEvent.change(input, { target: { value: parcial } });
      }
      // backspace: a composição encolhe uma letra
      fireEvent.change(input, { target: { value: 'ca' } });
      fireEvent.compositionEnd(input, { target: { value: 'ca' } });
      expect(input.value).toBe('ca');
    });

    it('recusa a sugestão tocada na barra do teclado', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      fireEvent.compositionStart(input);
      fireEvent.change(input, { target: { value: 'c' } });
      fireEvent.change(input, { target: { value: 'ca' } });
      // aluno toca em "cachorro" na barra de sugestões
      fireEvent.change(input, { target: { value: 'cachorro' } });
      expect(input.value).toBe('ca');
      expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
    });

    // O corretor do celular troca UMA letra da palavra enquanto ela é composta - é
    // assim que "voce" vira "você" e "caza" vira "casa" sem o aluno pedir. Só a tecla
    // morta do teclado físico tem licença para trocar uma letra por outra.
    it('recusa a letra trocada pelo corretor no meio da composição', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      fireEvent.compositionStart(input);
      for (const parcial of ['v', 'vo', 'voc', 'voce']) {
        fireEvent.change(input, { target: { value: parcial } });
      }
      // corretor acentuando sozinho o que o aluno escreveu
      fireEvent.change(input, { target: { value: 'você' } });
      expect(input.value).toBe('voce');
      expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
    });

    it('recusa a palavra trocada pelo corretor na confirmação', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      fireEvent.compositionStart(input);
      for (const parcial of ['c', 'cs', 'csa', 'csaa']) {
        fireEvent.change(input, { target: { value: parcial } });
      }
      // barra de espaço: o corretor confirma "casa" no lugar do que foi digitado
      fireEvent.compositionEnd(input, { target: { value: 'casa' } });
      expect(input.value).toBe('csaa');
      expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
    });

    it('recusa a palavra escrita deslizando o dedo', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      fireEvent.compositionStart(input);
      fireEvent.change(input, { target: { value: 'paralelepipedo' } });
      expect(input.value).toBe('');
      expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
    });
  });

  /*
   * Aparelho de tela sensível ao toque: o campo vira de senha, que é o único tipo em
   * que nenhum teclado de vidro sugere, corrige ou aprende - e a palavra passa a ser
   * mostrada pelo espelho desenhado pelo componente.
   */
  describe('celular e tablet (campo de senha com espelho)', () => {
    let matchMediaOriginal: typeof window.matchMedia;

    beforeEach(() => {
      matchMediaOriginal = window.matchMedia;
      window.matchMedia = ((consulta: string) => ({ matches: consulta.includes('coarse'), media: consulta })) as typeof window.matchMedia;
    });

    afterEach(() => {
      window.matchMedia = matchMediaOriginal;
    });

    it('usa campo de senha, onde o teclado não oferece correção', () => {
      render(<Harness />);
      const input = getInput();
      expect(input.getAttribute('type')).toBe('password');
      // inputmode tem precedência sobre o tipo do campo no Android: pedir "texto
      // comum" devolveria as sugestões que o campo de senha cala
      expect(input.getAttribute('inputmode')).toBeNull();
    });

    it('mostra no espelho a palavra que o aluno digita', () => {
      render(<Harness className="campo-da-tela" placeholder="escreva aqui..." />);
      const input = getInput();
      const espelho = document.querySelector('.ep-espelho');
      expect(espelho).not.toBeNull();
      // O espelho leva a classe visual da tela para parecer o campo de sempre
      expect(espelho?.classList.contains('campo-da-tela')).toBe(true);
      expect(espelho?.textContent).toBe('escreva aqui...');
      digitar(input, 'casa');
      expect(espelho?.textContent).toBe('casa');
    });

    it('continua recusando o que não foi digitado', () => {
      const onBurla = jest.fn();
      render(<Harness onBurla={onBurla} />);
      const input = getInput();
      digitar(input, 'ca');
      fireEvent.change(input, { target: { value: 'cachorro' } });
      expect(input.value).toBe('ca');
      expect(onBurla).toHaveBeenCalledWith('insercao-multipla');
    });
  });

  describe('medirEdicao', () => {
    it('conta uma letra inserida no fim', () => {
      expect(medirEdicao('cas', 'casa')).toEqual({ inseridos: 1, removidos: 0 });
    });

    it('conta uma letra inserida no meio', () => {
      expect(medirEdicao('caa', 'casa')).toEqual({ inseridos: 1, removidos: 0 });
    });

    it('conta uma letra apagada', () => {
      expect(medirEdicao('casa', 'cas')).toEqual({ inseridos: 0, removidos: 1 });
    });

    it('conta a troca de um caractere (tecla morta virando vogal acentuada)', () => {
      expect(medirEdicao('caf´', 'café')).toEqual({ inseridos: 1, removidos: 1 });
    });

    it('conta a palavra inteira reescrita pelo corretor', () => {
      expect(medirEdicao('csaa', 'casa')).toEqual({ inseridos: 2, removidos: 2 });
    });

    it('não conta nada quando o texto não mudou', () => {
      expect(medirEdicao('casa', 'casa')).toEqual({ inseridos: 0, removidos: 0 });
    });
  });
});
