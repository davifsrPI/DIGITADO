import React, { useState } from 'react';

// Composição de acentos do português: acento pendente + vogal = caractere acentuado
const COMPOR: Record<string, Record<string, string>> = {
  '´': { a: 'á', e: 'é', i: 'í', o: 'ó', u: 'ú' },
  '`': { a: 'à' },
  '^': { a: 'â', e: 'ê', o: 'ô' },
  '~': { a: 'ã', o: 'õ' },
};

const LINHA1 = ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'];
const LINHA2 = ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'ç'];
const ACENTOS = ['´', '`', '^', '~'];
const LINHA3 = ['z', 'x', 'c', 'v', 'b', 'n', 'm'];

interface Props {
  onLetra: (letra: string) => void;
  onApagar: () => void;
  disabled?: boolean;
}

// Teclado na tela para o celular (só aparece em telas touch, via CSS pointer:coarse).
// Como o teclado nativo fica suprimido pelo inputMode="none" do campo, este é o
// único jeito de digitar no touch — sem corretor ortográfico no caminho.
// As teclas ´ ` ^ ~ funcionam como teclas mortas do ABNT2: toca o acento
// (fica marcado como pendente) e depois a vogal para compor á, ã, ê...
export const TecladoVirtual: React.FC<Props> = ({ onLetra, onApagar, disabled }) => {
  const [acentoPendente, setAcentoPendente] = useState<string | null>(null);

  // pointerdown com preventDefault: a tecla não rouba o foco do input
  const pressionar = (acao: () => void) => (e: React.PointerEvent) => {
    e.preventDefault();
    if (!disabled) acao();
  };

  const teclaLetra = (letra: string) => {
    if (acentoPendente) {
      const composto = COMPOR[acentoPendente]?.[letra];
      setAcentoPendente(null);
      // Acento + letra incompatível (ex: ~ + t): descarta o acento e insere a
      // letra pura — mesmo comportamento tolerante do teclado físico
      onLetra(composto ?? letra);
    } else {
      onLetra(letra);
    }
  };

  // Tocar o mesmo acento de novo cancela a pendência
  const teclaAcento = (acento: string) => setAcentoPendente(prev => (prev === acento ? null : acento));

  const renderLetra = (letra: string) => (
    <button key={letra} type="button" className="ep-tecla" onPointerDown={pressionar(() => teclaLetra(letra))} disabled={disabled}>
      {letra}
    </button>
  );

  return (
    <div className="ep-teclado" role="group" aria-label="Teclado virtual">
      <div className="ep-linha">{LINHA1.map(renderLetra)}</div>
      <div className="ep-linha">
        {LINHA2.map(renderLetra)}
        <button
          type="button"
          className="ep-tecla ep-tecla--larga"
          onPointerDown={pressionar(() => {
            setAcentoPendente(null);
            onApagar();
          })}
          disabled={disabled}
          aria-label="Apagar"
        >
          ⌫
        </button>
      </div>
      <div className="ep-linha">
        {ACENTOS.map(acento => (
          <button
            key={acento}
            type="button"
            className={`ep-tecla ep-tecla--acento${acentoPendente === acento ? ' ep-tecla--pendente' : ''}`}
            onPointerDown={pressionar(() => teclaAcento(acento))}
            disabled={disabled}
          >
            {acento}
          </button>
        ))}
        {LINHA3.map(renderLetra)}
      </div>
    </div>
  );
};

export default TecladoVirtual;
