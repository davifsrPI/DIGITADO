import React, { useCallback, useEffect, useRef } from 'react';
import { TecladoVirtual } from './teclado-virtual';
import './entrada-palavra.scss';

// Tipos de tentativa de burla que a guarda detecta e bloqueia
export type TipoBurla = 'colagem' | 'arrasto' | 'correcao-automatica' | 'insercao-multipla';

// Só letras do português (com acentos) e hífen de palavras compostas passam
const CARACTERES_INVALIDOS = /[^a-záàâãéêíóôõúüç-]/g;

interface Props {
  id?: string;
  value: string;
  onChange: (valor: string) => void;
  // Notifica cada tentativa bloqueada de inserir texto sem digitar (colar, corretor, arrastar)
  onBurla?: (tipo: TipoBurla) => void;
  disabled?: boolean;
  maxLength?: number;
  placeholder?: string;
  // Classes visuais do input — cada tela mantém o próprio estilo de campo
  className?: string;
  ariaLabel?: string;
  inputRef?: React.MutableRefObject<HTMLInputElement | null>;
}

// Campo de resposta blindado contra o corretor ortográfico e colagem:
// - inputMode="none" impede o teclado nativo do celular de abrir (e com ele o corretor);
//   no touch entra o TecladoVirtual, no desktop o teclado físico digita normalmente
//   com composição de acentos do próprio sistema (ABNT2: ´ + a = á)
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
        onPaste={e => {
          e.preventDefault();
          onBurlaRef.current?.('colagem');
        }}
        onDrop={e => {
          e.preventDefault();
          onBurlaRef.current?.('arrasto');
        }}
        placeholder={placeholder}
        inputMode="none"
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
      <TecladoVirtual onLetra={inserirLetra} onApagar={apagar} disabled={disabled} />
    </div>
  );
};

export default EntradaPalavra;
