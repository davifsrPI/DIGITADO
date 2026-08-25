import React from 'react';

// Linha do tempo do desempenho: uma coluna por mês com o VOLUME de respostas
// (barra ao fundo) e a TAXA de acerto (linha à frente).
//
// As duas leituras juntas de propósito: 90% de acerto em 4 respostas não é a
// mesma notícia que 90% em 400, e só a linha esconderia isso. Os pontos vêm
// prontos do backend, aqui não se calcula percentual nenhum.

export interface PontoEvolucao {
  mes: string; // 'YYYY-MM'
  total: number;
  acertos: number;
  taxa: number;
  jogadores?: number;
}

// '2026-08' -> 'ago/26'
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const rotuloMes = (mes: string): string => {
  const [ano, m] = mes.split('-');
  const indice = Number(m) - 1;
  if (isNaN(indice) || indice < 0 || indice > 11) return mes;
  return `${MESES[indice]}/${ano.slice(2)}`;
};

// Área de desenho (viewBox fixo; o SVG escala com a largura do card)
const L = 40;
const R = 14;
const T = 14;
const B = 34;
const LARGURA = 640;
const ALTURA = 230;
const AREA_W = LARGURA - L - R;
const AREA_H = ALTURA - T - B;

interface Props {
  pontos: PontoEvolucao[];
  // Cor da linha de taxa (o painel do titular e o do admin usam tons diferentes)
  cor?: string;
  vazio?: string;
}

export const GraficoEvolucao: React.FC<Props> = ({
  pontos,
  cor = '#818cf8',
  vazio = 'Sem dados suficientes para montar a linha do tempo.',
}) => {
  if (pontos.length === 0) {
    return <p className="dp-vazio">{vazio}</p>;
  }

  // X de cada mês: centro da sua fatia (funciona com 1 ponto só)
  const passo = AREA_W / pontos.length;
  const x = (i: number) => L + passo * i + passo / 2;
  const y = (taxa: number) => T + AREA_H - (Math.max(0, Math.min(100, taxa)) / 100) * AREA_H;

  const maxVolume = Math.max(...pontos.map(p => p.total), 1);
  const larguraBarra = Math.min(38, passo * 0.55);

  const linha = pontos.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(p.taxa).toFixed(1)}`).join(' ');
  // Área sob a linha, fechada na base, dá volume visual à curva
  const area = `${linha} L ${x(pontos.length - 1).toFixed(1)} ${T + AREA_H} L ${x(0).toFixed(1)} ${T + AREA_H} Z`;
  const idGradiente = `dp-grad-${cor.replace('#', '')}`;

  return (
    <svg className="dp-grafico" viewBox={`0 0 ${LARGURA} ${ALTURA}`} role="img" aria-label="Evolução da taxa de acerto por mês">
      <defs>
        <linearGradient id={idGradiente} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={cor} stopOpacity="0.35" />
          <stop offset="100%" stopColor={cor} stopOpacity="0" />
        </linearGradient>
      </defs>

      {/* Grade de 0 a 100% */}
      {[0, 25, 50, 75, 100].map(v => (
        <g key={v}>
          <line x1={L} y1={y(v)} x2={LARGURA - R} y2={y(v)} stroke="rgba(255,255,255,0.08)" strokeWidth={1} />
          <text x={L - 8} y={y(v) + 4} textAnchor="end" className="dp-grafico-eixo" fill="rgba(255,255,255,0.35)">
            {v}%
          </text>
        </g>
      ))}

      {/* Volume de respostas do mês, ao fundo */}
      {pontos.map((p, i) => {
        const altura = (p.total / maxVolume) * AREA_H;
        return (
          <rect
            key={`v-${p.mes}`}
            x={x(i) - larguraBarra / 2}
            y={T + AREA_H - altura}
            width={larguraBarra}
            height={altura}
            rx={4}
            fill="rgba(255,255,255,0.07)"
          >
            <title>{`${rotuloMes(p.mes)}: ${p.total} resposta(s), ${p.acertos} acerto(s)`}</title>
          </rect>
        );
      })}

      {/* Taxa de acerto */}
      <path d={area} fill={`url(#${idGradiente})`} />
      <path d={linha} fill="none" stroke={cor} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
      {pontos.map((p, i) => (
        <g key={`p-${p.mes}`}>
          <circle cx={x(i)} cy={y(p.taxa)} r={4.5} fill="#0f172a" stroke={cor} strokeWidth={2.5} />
          <title>{`${rotuloMes(p.mes)}: ${p.taxa}% de acerto em ${p.total} resposta(s)`}</title>
        </g>
      ))}

      {/* Meses */}
      {pontos.map((p, i) => (
        <text key={`m-${p.mes}`} x={x(i)} y={ALTURA - 12} textAnchor="middle" className="dp-grafico-eixo" fill="rgba(255,255,255,0.45)">
          {rotuloMes(p.mes)}
        </text>
      ))}
    </svg>
  );
};

export default GraficoEvolucao;
