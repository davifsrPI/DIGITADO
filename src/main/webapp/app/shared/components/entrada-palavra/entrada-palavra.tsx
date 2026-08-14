import React, { useCallback, useEffect, useRef, useState } from 'react';
import { TecladoVirtual } from './teclado-virtual';
import './entrada-palavra.scss';

// Tipos de tentativa de burla que a guarda detecta e bloqueia
export type TipoBurla = 'colagem' | 'arrasto' | 'correcao-automatica' | 'insercao-multipla';

// Só letras do português (com acentos) e hífen de palavras compostas passam
const CARACTERES_INVALIDOS = /[^a-záàâãéêíóôõúüç-]/g;
// Enquanto o acento está sendo composto (tecla morta ´ seguida da vogal), o campo
// precisa exibir o acento sozinho por um instante. Apagá-lo nessa janela cancela a
// composição do navegador e a vogal acentuada nunca chega - por isso as teclas
// mortas passam durante a composição e só são sanitizadas no fim dela
const CARACTERES_INVALIDOS_COMPONDO = /[^a-záàâãéêíóôõúüç´`^~¨-]/g;

// Dispositivo sem teclado físico. O teclado nativo (e o corretor que vem junto) só
// pode ser suprimido quando existe o TecladoVirtual para colocar no lugar: errar
// essa conta dos dois lados é seguro - ou aparece o teclado da tela, ou o do sistema
const ehDispositivoDeToque = (): boolean => {
  if (typeof window === 'undefined') return false;
  if (typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches) return true;
  return (typeof navigator !== 'undefined' && navigator.maxTouchPoints > 0) || 'ontouchstart' in window;
};

interface Props {
  id?: string;
  value: string;
  onChange: (valor: string) => void;
  // Notifica cada tentativa bloqueada de inserir texto sem digitar (colar, corretor, arrastar)
  onBurla?: (tipo: TipoBurla) => void;
  disabled?: boolean;
  maxLength?: number;
  placeholder?: string;
  // Classes visuais do input - cada tela mantém o próprio estilo de campo
  className?: string;
  ariaLabel?: string;
  inputRef?: React.MutableRefObject<HTMLInputElement | null>;
}

// Campo de resposta blindado contra o corretor ortográfico e colagem:
// - em telas de toque, inputMode="none" impede o teclado nativo de abrir (e com ele o
//   corretor) e o TecladoVirtual entra no lugar; no desktop o teclado físico digita
//   normalmente, com a composição de acentos do próprio sistema (ABNT2: ´ + a = á)
// - a guarda em beforeinput bloqueia colar, arrastar e a correção do spellcheck
// - o texto só pode crescer 1 caractere por evento: mesmo que algum teclado ignore
//   o inputMode e injete uma palavra inteira, ela é descartada
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

  // Composição de acento em andamento (tecla morta do teclado físico)
  const compondoRef = useRef(false);
  // Valor de antes da composição começar: é contra ele que se mede o crescimento
  const valorPreComposicaoRef = useRef('');

  // Só suprime o teclado do sistema quando o teclado da tela está disponível.
  // Reavalia quando o dispositivo muda de modo (notebook 2-em-1 virando tablet)
  const [toque, setToque] = useState(ehDispositivoDeToque);
  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined;
    const mq = window.matchMedia('(pointer: coarse)');
    const atualizar = () => setToque(ehDispositivoDeToque());
    // Safari antigo não tem addEventListener na media query: sem o guarda, o erro
    // derrubaria a tela inteira do jogo por causa de um detalhe de teclado
    if (typeof mq.addEventListener !== 'function') return undefined;
    mq.addEventListener('change', atualizar);
    return () => mq.removeEventListener('change', atualizar);
  }, []);

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

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const bruto = e.target.value;
    // Acento a meio caminho: o texto da composição segue para o estado quase como
    // veio. Devolver outro valor aqui faria o React reescrever o input no meio da
    // composição, e o navegador a cancelaria - era o que impedia digitar á, ã, ê...
    if (compondoRef.current) {
      onChange(bruto.toLowerCase().replace(CARACTERES_INVALIDOS_COMPONDO, '').slice(0, maxLength));
      return;
    }
    if (bruto.length > value.length + 1) {
      // Palavra inteira inserida de uma vez (corretor/autofill que escapou da
      // guarda): descarta e restaura o valor no DOM, já que sem mudança de
      // estado o React não re-renderiza o input controlado
      e.target.value = value;
      onBurlaRef.current?.('insercao-multipla');
      return;
    }
    onChange(sanitizar(bruto));
  };

  const handleCompositionStart = () => {
    compondoRef.current = true;
    valorPreComposicaoRef.current = value;
  };

  // Fim da composição: o acento já virou vogal acentuada. Só agora dá para sanitizar
  // e conferir o crescimento - uma composição rende UM caractere, então a regra de
  // "no máximo 1 por vez" continua valendo contra o corretor que sugere a palavra inteira
  const handleCompositionEnd = (e: React.CompositionEvent<HTMLInputElement>) => {
    compondoRef.current = false;
    const anterior = valorPreComposicaoRef.current;
    const composto = sanitizar(e.currentTarget.value);
    if (composto.length > anterior.length + 1) {
      onChange(anterior);
      onBurlaRef.current?.('insercao-multipla');
      return;
    }
    onChange(composto);
  };

  const inserirLetra = useCallback(
    (letra: string) => {
      if (!disabled) onChange(sanitizar(value + letra));
    },
    [disabled, value, onChange, sanitizar],
  );

  const apagar = useCallback(() => {
    if (!disabled) onChange(value.slice(0, -1));
  }, [disabled, value, onChange]);

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
        inputMode={toque ? 'none' : undefined}
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
      <TecladoVirtual onLetra={inserirLetra} onApagar={apagar} disabled={disabled} visivel={toque} />
    </div>
  );
};

export default EntradaPalavra;
