import React from 'react';

// O "acelerador" do desempenho: um velocímetro que vai de 0% a 100% de acerto,
// dividido nas três faixas que o backend usa para classificar (RUIM / MÉDIO / BOM).
//
// O ponteiro marca a taxa RECENTE, "como estou hoje", e não a média de sempre;
// quem decide a faixa é o backend (HistoricoRespostaService.classificar), a tela
// só desenha o que recebe. Os limites abaixo são espelho dos de lá: mudou um,
// muda o outro.

export type NivelDesempenho = 'RUIM' | 'MEDIO' | 'BOM';

// Mesmos limites do HistoricoRespostaService (LIMITE_MEDIO / LIMITE_BOM)
const LIMITE_MEDIO = 50;
const LIMITE_BOM = 75;

export const CORES_NIVEL: Record<NivelDesempenho, string> = {
  RUIM: '#f87171',
  MEDIO: '#fbbf24',
  BOM: '#4ade80',
};

// Rótulo da faixa exibido no chip abaixo do ponteiro
const LABELS_NIVEL: Record<NivelDesempenho, string> = {
  RUIM: 'Ruim',
  MEDIO: 'Médio',
  BOM: 'Bom',
};

// Geometria do arco: semicírculo de 180° (esquerda = 0%, direita = 100%)
const CX = 150;
const CY = 150;
const RAIO = 112;
const ESPESSURA = 20;

// Converte um valor 0-100 no ponto correspondente da circunferência
const ponto = (valor: number, raio: number) => {
  const angulo = ((180 - (Math.max(0, Math.min(100, valor)) / 100) * 180) * Math.PI) / 180;
  return { x: CX + raio * Math.cos(angulo), y: CY - raio * Math.sin(angulo) };
};

// Trecho do arco entre dois valores (sweep 1 = sentido horário na tela)
const arco = (de: number, ate: number, raio: number) => {
  const a = ponto(de, raio);
  const b = ponto(ate, raio);
  return `M ${a.x.toFixed(2)} ${a.y.toFixed(2)} A ${raio} ${raio} 0 0 1 ${b.x.toFixed(2)} ${b.y.toFixed(2)}`;
};

interface Props {
  // Valor apontado pelo ponteiro (taxa de acerto recente, 0-100)
  valor: number;
  nivel: NivelDesempenho;
  // Texto explicativo vindo do backend
  mensagem?: string;
  // Diferença em pontos percentuais contra a média de sempre (positivo = melhorando)
  tendencia?: number;
}

export const Acelerador: React.FC<Props> = ({ valor, nivel, mensagem, tendencia }) => {
  const seguro = Math.max(0, Math.min(100, Math.round(valor)));
  const agulha = ponto(seguro, RAIO - 26);
  const cor = CORES_NIVEL[nivel];

  return (
    <div className="dp-acelerador">
      <svg className="dp-acelerador-svg" viewBox="0 0 300 178" role="img" aria-label={`Taxa de acerto recente: ${seguro}%`}>
        {/* Faixas: vermelho até 50%, âmbar até 75%, verde daí em diante */}
        <path d={arco(0, LIMITE_MEDIO, RAIO)} stroke={CORES_NIVEL.RUIM} strokeWidth={ESPESSURA} fill="none" strokeLinecap="round" />
        <path d={arco(LIMITE_MEDIO, LIMITE_BOM, RAIO)} stroke={CORES_NIVEL.MEDIO} strokeWidth={ESPESSURA} fill="none" />
        <path d={arco(LIMITE_BOM, 100, RAIO)} stroke={CORES_NIVEL.BOM} strokeWidth={ESPESSURA} fill="none" strokeLinecap="round" />

        {/* Marcas de 0 a 100 de 25 em 25 */}
        {[0, 25, 50, 75, 100].map(marca => {
          const de = ponto(marca, RAIO - ESPESSURA / 2 - 4);
          const ate = ponto(marca, RAIO - ESPESSURA / 2 - 12);
          return (
            <line
              key={marca}
              x1={de.x}
              y1={de.y}
              x2={ate.x}
              y2={ate.y}
              stroke="rgba(255,255,255,0.25)"
              strokeWidth={2}
              strokeLinecap="round"
            />
          );
        })}

        {/* Ponteiro + eixo */}
        <line x1={CX} y1={CY} x2={agulha.x} y2={agulha.y} stroke={cor} strokeWidth={5} strokeLinecap="round" />
        <circle cx={CX} cy={CY} r={11} fill="#0f172a" stroke={cor} strokeWidth={4} />

        {/* Leitura */}
        <text x={CX} y={CY - 38} textAnchor="middle" className="dp-acelerador-valor" fill="#fff">
          {seguro}%
        </text>
        <text x={CX} y={CY - 16} textAnchor="middle" className="dp-acelerador-legenda" fill="rgba(255,255,255,0.45)">
          acerto recente
        </text>

        {/* Extremos da escala */}
        <text x={CX - RAIO} y={CY + 22} textAnchor="middle" className="dp-acelerador-escala" fill="rgba(255,255,255,0.35)">
          0%
        </text>
        <text x={CX + RAIO} y={CY + 22} textAnchor="middle" className="dp-acelerador-escala" fill="rgba(255,255,255,0.35)">
          100%
        </text>
      </svg>

      <div className="dp-acelerador-info">
        <span className="dp-nivel-chip" style={{ color: cor, borderColor: cor, background: `${cor}1f` }}>
          {LABELS_NIVEL[nivel]}
        </span>
        {tendencia != null && tendencia !== 0 && (
          <span className={`dp-tendencia${tendencia > 0 ? ' dp-tendencia--sobe' : ' dp-tendencia--desce'}`}>
            {tendencia > 0 ? '▲' : '▼'} {Math.abs(tendencia)} pts vs. sua média
          </span>
        )}
      </div>

      {mensagem && <p className="dp-acelerador-msg">{mensagem}</p>}
    </div>
  );
};

export default Acelerador;
