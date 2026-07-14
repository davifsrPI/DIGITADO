import './icone-audio.scss';

import React from 'react';

interface Props {
  // true enquanto a síntese de voz está reproduzindo — anima as ondas
  tocando?: boolean;
  className?: string;
}

// Alto-falante em SVG — substitui os emojis 🔉/🔊 dos botões de áudio, que são
// renderizados pela fonte do sistema e ficam serrilhados/desalinhados no celular.
// Herda a cor do texto do botão (currentColor) e escala nítido em qualquer tela.
export const IconeAudio = ({ tocando = false, className = '' }: Props) => (
  <svg
    className={`ia-icone${tocando ? ' ia-tocando' : ''}${className ? ` ${className}` : ''}`}
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M4 9.5v5a1 1 0 0 0 1 1h2.6l4.1 3.3a1 1 0 0 0 1.6-.8V6a1 1 0 0 0-1.6-.8L7.6 8.5H5a1 1 0 0 0-1 1z" fill="currentColor" />
    <path className="ia-wave ia-wave--1" d="M16 9.4a3.8 3.8 0 0 1 0 5.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    <path className="ia-wave ia-wave--2" d="M18.4 7a7.4 7.4 0 0 1 0 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
);

export default IconeAudio;
