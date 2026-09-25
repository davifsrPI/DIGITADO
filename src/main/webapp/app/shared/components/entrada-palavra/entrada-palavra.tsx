import React, { useCallback, useEffect, useLayoutEffect, useRef } from 'react';
import './entrada-palavra.scss';

// Tipos de tentativa de burla que a guarda detecta e bloqueia
export type TipoBurla = 'colagem' | 'arrasto' | 'correcao-automatica' | 'insercao-multipla';

// Só letras do português (com acentos) e hífen de palavras compostas passam
const CARACTERES_INVALIDOS = /[^a-záàâãéêíóôõúüç-]/g;
// Enquanto o acento está sendo composto (tecla morta ´ seguida da vogal), o campo
// precisa exibir o acento sozinho por um instante. Apagá-lo nessa janela cancela a
// composição do navegador e a vogal acentuada nunca chega, por isso as teclas
// mortas passam durante a composição e só são sanitizadas no fim dela
const CARACTERES_INVALIDOS_COMPONDO = /[^a-záàâãéêíóôõúüç´`^~¨-]/g;

/**
 * Diferença entre dois estados do campo, medida descontando o pedaço igual do começo
 * e do fim: quantos caracteres entraram e quantos saíram.
 *
 * É o que separa DIGITAR de SER CORRIGIDO. Uma tecla insere 1 e remove 0; o backspace
 * remove 1 e insere 0; uma tecla morta virando vogal acentuada troca 1 por 1. Já o
 * corretor reescreve um trecho inteiro de uma vez ("csaa" virando "casa" troca 2 por
 * 2) e a sugestão da barra do teclado insere a palavra toda.
 */
export function medirEdicao(antes: string, depois: string): { inseridos: number; removidos: number } {
  let inicio = 0;
  while (inicio < antes.length && inicio < depois.length && antes[inicio] === depois[inicio]) {
    inicio++;
  }
  let fim = 0;
  while (fim < antes.length - inicio && fim < depois.length - inicio && antes[antes.length - 1 - fim] === depois[depois.length - 1 - fim]) {
    fim++;
  }
  return { removidos: antes.length - inicio - fim, inseridos: depois.length - inicio - fim };
}

/**
 * A edição cabe em UMA tecla?
 *
 * compondo: durante a composição vale também a troca de 1 por 1, que é a tecla morta
 * do teclado físico (´ virando é) e o caractere que o teclado do celular reescreve
 * enquanto a palavra está sendo montada. Fora da composição a regra é mais dura: ou
 * entrou um caractere, ou saiu um.
 */
function edicaoDeUmaTecla(antes: string, depois: string, compondo: boolean): boolean {
  const { inseridos, removidos } = medirEdicao(antes, depois);
  if (inseridos === 0 && removidos === 0) return true;
  if (inseridos <= 1 && removidos === 0) return true;
  if (inseridos === 0 && removidos <= 1) return true;
  return compondo && inseridos === 1 && removidos === 1;
}

interface Props {
  id?: string;
  value: string;
  onChange: (valor: string) => void;
  // Notifica cada tentativa bloqueada de inserir texto sem digitar (colar, corretor, arrastar)
  onBurla?: (tipo: TipoBurla) => void;
  disabled?: boolean;
  maxLength?: number;
  placeholder?: string;
  // Classes visuais do input, cada tela mantém o próprio estilo de campo
  className?: string;
  ariaLabel?: string;
  inputRef?: React.MutableRefObject<HTMLInputElement | null>;
}

/**
 * Campo de resposta blindado contra o corretor ortográfico e a colagem, digitado no
 * teclado NORMAL do aparelho (o do sistema no celular, o físico no computador). Os
 * acentos saem do próprio teclado: segurando a letra no celular, com a tecla morta
 * (ABNT2: ´ + a = á) no computador.
 *
 * A blindagem tem duas camadas, porque uma só não basta:
 *
 * 1. Os atributos autocorrect/autocapitalize/autocomplete/spellcheck PEDEM ao teclado
 *    que não corrija. O Safari do iPhone respeita; o Gboard do Android ignora boa
 *    parte deles e continua oferecendo sugestões. Por isso eles são só a primeira camada.
 *
 * 2. A guarda de verdade é o que o campo ACEITA: cada evento pode mudar o texto no
 *    máximo o equivalente a uma tecla (ver edicaoDeUmaTecla). Tocar numa sugestão da
 *    barra, escrever deslizando o dedo, colar, arrastar texto ou deixar o corretor
 *    trocar a palavra na barra de espaço muda vários caracteres de uma vez - tudo
 *    isso é recusado e contado como tentativa de burla, doa a qual teclado for.
 */
export const EntradaPalavra: React.FC<Props> = ({
  id,
  value,
  onChange,
  onBurla,
  disabled,
  maxLength = 40,
  placeholder,
  className,
  ariaLabel,
  inputRef,
}) => {
  const innerRef = useRef<HTMLInputElement | null>(null);
  const onBurlaRef = useRef(onBurla);
  useEffect(() => {
    onBurlaRef.current = onBurla;
  }, [onBurla]);

  // Composição em andamento: tecla morta no teclado físico, palavra sendo montada no
  // teclado do celular (o Gboard compõe a palavra inteira enquanto o aluno digita)
  const compondoRef = useRef(false);

  // Guarda nativa: o SyntheticEvent do React não expõe o inputType de forma
  // confiável, então o listener de beforeinput é registrado direto no elemento
  useEffect(() => {
    const el = innerRef.current;
    if (!el) return undefined;
    const guarda = (ev: InputEvent) => {
      if (ev.inputType === 'insertReplacementText') {
        ev.preventDefault();
        onBurlaRef.current?.('correcao-automatica');
      } else if (ev.inputType === 'insertFromPaste') {
        ev.preventDefault();
        onBurlaRef.current?.('colagem');
      } else if (ev.inputType === 'insertFromDrop') {
        ev.preventDefault();
        onBurlaRef.current?.('arrasto');
      }
    };
    el.addEventListener('beforeinput', guarda);
    return () => el.removeEventListener('beforeinput', guarda);
  }, []);

  const setRefs = useCallback(
    (el: HTMLInputElement | null) => {
      innerRef.current = el;
      if (inputRef) inputRef.current = el;
    },
    [inputRef],
  );

  const sanitizar = useCallback((s: string) => s.toLowerCase().replace(CARACTERES_INVALIDOS, '').slice(0, maxLength), [maxLength]);

  /**
   * Onde deixar o cursor depois que o React reescrever o campo.
   *
   * Só é usado quando a limpeza muda o texto que o navegador acabou de montar (o aluno
   * digitou um número no meio da palavra, por exemplo): aí o React reescreve o input e
   * o cursor saltaria para o fim, fazendo o resto da digitação sair fora de ordem.
   * null = o valor no DOM já é o certo, e o cursor fica onde o navegador deixou.
   */
  const cursorDesejadoRef = useRef<number | null>(null);
  useLayoutEffect(() => {
    const el = innerRef.current;
    const pos = cursorDesejadoRef.current;
    if (el == null || pos == null) return;
    cursorDesejadoRef.current = null;
    const alvo = Math.max(0, Math.min(pos, el.value.length));
    try {
      el.setSelectionRange(alvo, alvo);
    } catch {
      // Campo de um tipo que não suporta seleção: sem reposicionar, nada quebra
    }
  }, [value]);

  // Desfaz no DOM uma edição recusada. Sem isto o texto recusado fica visível no campo:
  // o estado não mudou, então o React não tem o que re-renderizar no input controlado.
  const recusar = (el: HTMLInputElement, tipo: TipoBurla) => {
    const cursor = el.selectionStart;
    el.value = value;
    if (cursor != null) {
      try {
        el.setSelectionRange(Math.min(cursor, value.length), Math.min(cursor, value.length));
      } catch {
        // idem: reposicionar o cursor é conforto, não requisito
      }
    }
    onBurlaRef.current?.(tipo);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const el = e.target;
    const bruto = el.value;
    const compondo = compondoRef.current;
    // Com acento a meio caminho, as teclas mortas passam: limpá-las agora cancelaria a
    // composição do navegador e a vogal acentuada nunca chegaria
    const limpo = compondo ? bruto.toLowerCase().replace(CARACTERES_INVALIDOS_COMPONDO, '').slice(0, maxLength) : sanitizar(bruto);

    // Mudou mais que uma tecla: corretor, sugestão da barra, escrita deslizando ou
    // algum preenchimento automático que escapou da guarda de beforeinput
    if (!edicaoDeUmaTecla(value, limpo, compondo)) {
      recusar(el, 'insercao-multipla');
      return;
    }

    // Durante a composição o valor vai para o estado como veio do navegador: devolver
    // outro texto aqui faria o React reescrever o input no meio da composição, e o
    // navegador a cancelaria
    if (compondo) {
      onChange(limpo);
      return;
    }
    // A limpeza mexeu no texto (caractere não aceito): o React vai reescrever o input,
    // então o cursor precisa voltar para depois do que sobrou do que foi digitado
    if (limpo !== bruto) {
      const cursor = el.selectionStart ?? bruto.length;
      cursorDesejadoRef.current = Math.max(0, cursor - (bruto.length - limpo.length));
    }
    onChange(limpo);
  };

  const handleCompositionStart = () => {
    compondoRef.current = true;
  };

  // Fim da composição: o acento já virou vogal acentuada e a palavra do teclado do
  // celular já foi confirmada. Agora as teclas mortas que tenham sobrado saem.
  const handleCompositionEnd = (e: React.CompositionEvent<HTMLInputElement>) => {
    compondoRef.current = false;
    const el = e.currentTarget;
    const composto = sanitizar(el.value);
    // A confirmação não pode trazer texto novo: o corretor do Android troca a palavra
    // composta pela "correta" exatamente neste momento
    if (!edicaoDeUmaTecla(value, composto, true)) {
      // Volta ao que estava ANTES da confirmação, já sem uma tecla morta que tenha
      // ficado pendente (durante a composição ela é deixada passar de propósito)
      const anterior = sanitizar(value);
      el.value = anterior;
      onBurlaRef.current?.('insercao-multipla');
      onChange(anterior);
      return;
    }
    onChange(composto);
  };

  return (
    <div className="ep-wrap">
      <input
        id={id}
        ref={setRefs}
        type="text"
        className={className}
        value={value}
        onChange={handleChange}
        onCompositionStart={handleCompositionStart}
        onCompositionEnd={handleCompositionEnd}
        onPaste={e => {
          e.preventDefault();
          onBurlaRef.current?.('colagem');
        }}
        onDrop={e => {
          e.preventDefault();
          onBurlaRef.current?.('arrasto');
        }}
        placeholder={placeholder}
        // Teclado normal de texto do aparelho. Os atributos abaixo pedem para ele não
        // corrigir, não sugerir e não deixar a primeira letra maiúscula - quem garante
        // mesmo é a guarda de edição acima, mas pedir bem evita a barra de sugestões
        // aparecer e tentar o aluno
        inputMode="text"
        autoComplete="off"
        autoCorrect="off"
        autoCapitalize="none"
        spellCheck={false}
        data-gramm="false"
        data-enable-grammarly="false"
        maxLength={maxLength}
        disabled={disabled}
        aria-label={ariaLabel}
      />
    </div>
  );
};

export default EntradaPalavra;
