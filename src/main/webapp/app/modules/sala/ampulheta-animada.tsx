import React from 'react';

/**
 * Ampulheta animada em SVG puro para a tela de espera do aluno:
 * a areia escoa do bulbo de cima para o de baixo com um fio caindo;
 * quando esvazia, a ampulheta dá um pulo, gira 180° e recomeça o ciclo.
 * Toda a coreografia fica no CSS (classes amp-*), num ciclo de 4s.
 */
export const AmpulhetaAnimada: React.FC = () => (
  <svg className="amp-svg" viewBox="0 0 120 160" width="96" height="128" aria-hidden="true">
    <defs>
      <linearGradient id="amp-grad-areia" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stopColor="#fde68a" />
        <stop offset="100%" stopColor="#f59e0b" />
      </linearGradient>
      <linearGradient id="amp-grad-madeira" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stopColor="#b45309" />
        <stop offset="100%" stopColor="#78350f" />
      </linearGradient>
      {/* Recortes dos bulbos (curvas suaves até o gargalo) - a areia só aparece dentro deles */}
      <clipPath id="amp-clip-topo">
        <path d="M30,20 H90 C90,48 66,62 63,74 L63,80 H57 L57,74 C54,62 30,48 30,20 Z" />
      </clipPath>
      <clipPath id="amp-clip-base">
        <path d="M57,80 H63 L63,86 C66,98 90,112 90,140 H30 C30,112 54,98 57,86 Z" />
      </clipPath>
    </defs>

    {/* Pilares laterais de madeira */}
    <rect x="20" y="14" width="7" height="132" rx="3.5" fill="url(#amp-grad-madeira)" />
    <rect x="93" y="14" width="7" height="132" rx="3.5" fill="url(#amp-grad-madeira)" />

    {/* Areia de cima - o nível desce (com uma leve depressão no centro) até esvaziar */}
    <g clipPath="url(#amp-clip-topo)">
      <path className="amp-areia-topo" d="M28,22 q32,12 64,0 v62 h-64 z" fill="url(#amp-grad-areia)" />
    </g>

    {/* Fio de areia: jato contínuo afunilado (largo no gargalo, fino embaixo)
        com grãos caindo em ritmo uniforme por cima, dando movimento ao fluxo */}
    <g className="amp-fio-grupo">
      <path className="amp-fio" d="M58.2,78 L61.8,78 L60.7,136 L59.3,136 Z" fill="url(#amp-grad-areia)" />
      <circle className="amp-grao amp-grao-1" cx="60" cy="80" r="1.3" />
      <circle className="amp-grao amp-grao-2" cx="60" cy="80" r="1.3" />
      <circle className="amp-grao amp-grao-3" cx="60" cy="80" r="1.3" />
      <circle className="amp-grao amp-grao-4" cx="60" cy="80" r="1.3" />
    </g>

    {/* Areia de baixo - monte arredondado que cresce até encher */}
    <g clipPath="url(#amp-clip-base)">
      <path className="amp-areia-base" d="M28,88 q32,-16 64,0 v60 h-64 z" fill="url(#amp-grad-areia)" />
    </g>

    {/* Vidro por cima da areia - translúcido com contorno */}
    <path className="amp-vidro" d="M30,20 H90 C90,48 66,62 63,74 L63,80 H57 L57,74 C54,62 30,48 30,20 Z" />
    <path className="amp-vidro" d="M57,80 H63 L63,86 C66,98 90,112 90,140 H30 C30,112 54,98 57,86 Z" />

    {/* Reflexos do vidro */}
    <path className="amp-brilho" d="M36,28 C36,44 47,54 51,62" />
    <path className="amp-brilho" d="M36,132 C36,118 46,108 50,100" />

    {/* Tampas de madeira com friso de luz */}
    <rect x="16" y="6" width="88" height="12" rx="6" fill="url(#amp-grad-madeira)" />
    <rect x="20" y="8.5" width="80" height="3" rx="1.5" className="amp-madeira-friso" />
    <rect x="16" y="142" width="88" height="12" rx="6" fill="url(#amp-grad-madeira)" />
    <rect x="20" y="144.5" width="80" height="3" rx="1.5" className="amp-madeira-friso" />
  </svg>
);

export default AmpulhetaAnimada;
