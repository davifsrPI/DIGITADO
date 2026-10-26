import React, { useState } from 'react';

import { CAPACIDADE_HISTORICO, formatarNumero } from './monitor-util';

// Gráfico do painel "Desempenho", no desenho do Gerenciador de Tarefas: histórico
// encostado à direita (o ponto mais novo na borda), grade de 10 x 10 que anda para
// a esquerda a cada leitura, área clara com a linha por cima.
//
// O SVG estica para o tamanho da caixa (preserveAspectRatio="none") e as linhas
// usam vector-effect="non-scaling-stroke" para não engrossarem ao esticar. O que
// precisa ficar redondo (pontos do cursor, dica) é HTML por cima.

export type EstiloSerie = 'area' | 'area-forte' | 'tracejada';

export interface SerieGrafico {
  nome: string;
  valores: (number | null)[];
  estilo: EstiloSerie;
}

interface GraficoProps {
  series: SerieGrafico[];
  maximo: number;
  cor: string;
  /** Quando cada ponto foi lido, para a dica dizer "há 12 s". */
  instantes?: number[];
  /** Quantas leituras já chegaram: faz a grade andar junto com o gráfico. */
  deslocamento?: number;
  formatar?: (valor: number) => string;
  /** Miniatura da lista lateral: sem grade nem dica. */
  mini?: boolean;
  descricao: string;
}

const LARGURA = 600;
const ALTURA = 200;
const PONTOS_POR_COLUNA = CAPACIDADE_HISTORICO / 10;
const PASSO = LARGURA / (CAPACIDADE_HISTORICO - 1);

const xDoPonto = (indice: number, total: number) => (CAPACIDADE_HISTORICO - total + indice) * PASSO;

const yDoValor = (valor: number, maximo: number) => ALTURA - (Math.max(0, Math.min(valor, maximo)) / maximo) * ALTURA;

/** Trechos contínuos (sem leitura nula no meio): cada um vira um caminho próprio. */
const trechos = (valores: (number | null)[]): { inicio: number; pontos: number[] }[] => {
  const resultado: { inicio: number; pontos: number[] }[] = [];
  valores.forEach((valor, indice) => {
    if (valor === null || !Number.isFinite(valor)) {
      return;
    }
    const ultimo = resultado[resultado.length - 1];
    if (ultimo && ultimo.inicio + ultimo.pontos.length === indice) {
      ultimo.pontos.push(valor);
    } else {
      resultado.push({ inicio: indice, pontos: [valor] });
    }
  });
  return resultado;
};

export const caminhos = (valores: (number | null)[], maximo: number): { linha: string; area: string }[] =>
  trechos(valores)
    .filter(t => t.pontos.length > 1)
    .map(({ inicio, pontos }) => {
      const coords = pontos.map((v, i) => `${xDoPonto(inicio + i, valores.length).toFixed(1)} ${yDoValor(v, maximo).toFixed(1)}`);
      const xInicio = xDoPonto(inicio, valores.length).toFixed(1);
      const xFim = xDoPonto(inicio + pontos.length - 1, valores.length).toFixed(1);
      const linha = `M ${coords.join(' L ')}`;
      return { linha, area: `${linha} L ${xFim} ${ALTURA} L ${xInicio} ${ALTURA} Z` };
    });

const OPACIDADE_AREA: Record<EstiloSerie, number> = { area: 0.12, 'area-forte': 0.32, tracejada: 0 };

/** "#117DBB" + 0.12 -> "rgba(17, 125, 187, 0.12)": o preenchimento claro da cor do recurso. */
export const comAlfa = (hex: string, alfa: number): string => {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16));
  return `rgba(${r}, ${g}, ${b}, ${alfa})`;
};

/** O quadradinho da legenda, no mesmo estilo da série no gráfico (área clara, forte ou só tracejado). */
export const AmostraDeCor = ({ cor, estilo }: { cor: string; estilo: EstiloSerie }) => (
  <span
    className="pd-amostra"
    aria-hidden="true"
    style={{ background: comAlfa(cor, OPACIDADE_AREA[estilo]), borderTop: `2px ${estilo === 'area' ? 'solid' : 'dashed'} ${cor}` }}
  />
);

export const GraficoDesempenho = ({
  series,
  maximo,
  cor,
  instantes = [],
  deslocamento = 0,
  formatar = v => formatarNumero(v, 1),
  mini = false,
  descricao,
}: GraficoProps) => {
  const [cursor, setCursor] = useState<number | null>(null);
  const total = Math.max(0, ...series.map(s => s.valores.length));
  const escala = maximo > 0 ? maximo : 1;

  const aoMover = (evento: React.PointerEvent<HTMLDivElement>) => {
    const caixa = evento.currentTarget.getBoundingClientRect();
    const fracao = (evento.clientX - caixa.left) / caixa.width;
    const indice = Math.round(fracao * (CAPACIDADE_HISTORICO - 1)) - (CAPACIDADE_HISTORICO - total);
    setCursor(indice >= 0 && indice < total ? indice : null);
  };

  // Linhas verticais a cada 6 pontos, contadas a partir da borda direita e
  // deslocadas pelo número de leituras: a grade anda com o tempo
  const verticais: number[] = [];
  for (let x = LARGURA - (deslocamento % PONTOS_POR_COLUNA) * PASSO; x > 0; x -= PONTOS_POR_COLUNA * PASSO) {
    if (x < LARGURA) {
      verticais.push(x);
    }
  }

  const xCursor = cursor !== null ? (xDoPonto(cursor, total) / LARGURA) * 100 : 0;
  const idade = cursor !== null && instantes.length === total ? Math.round((instantes[total - 1] - instantes[cursor]) / 1000) : null;

  return (
    <div
      className={`pd-grafico${mini ? ' pd-grafico-mini' : ''}`}
      style={{ borderColor: cor }}
      onPointerMove={mini ? undefined : aoMover}
      onPointerLeave={mini ? undefined : () => setCursor(null)}
    >
      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} preserveAspectRatio="none" role="img" aria-label={descricao}>
        {!mini && (
          <g stroke={cor} strokeOpacity={0.16} strokeWidth={1}>
            {Array.from({ length: 9 }, (_, i) => (ALTURA / 10) * (i + 1)).map(y => (
              <line key={`h${y}`} x1={0} x2={LARGURA} y1={y} y2={y} vectorEffect="non-scaling-stroke" />
            ))}
            {verticais.map(x => (
              <line key={`v${x.toFixed(1)}`} x1={x} x2={x} y1={0} y2={ALTURA} vectorEffect="non-scaling-stroke" />
            ))}
          </g>
        )}
        {series.map(serie =>
          caminhos(serie.valores, escala).map(({ linha, area }, i) => (
            <g key={`${serie.nome}-${i}`}>
              {serie.estilo !== 'tracejada' && <path d={area} fill={cor} fillOpacity={OPACIDADE_AREA[serie.estilo]} stroke="none" />}
              <path
                d={linha}
                fill="none"
                stroke={cor}
                strokeWidth={serie.estilo === 'area' ? 2 : 1.5}
                strokeDasharray={serie.estilo === 'area' ? undefined : '5 4'}
                strokeLinejoin="round"
                vectorEffect="non-scaling-stroke"
              />
            </g>
          )),
        )}
      </svg>

      {cursor !== null && (
        <>
          <div className="pd-cursor" style={{ left: `${xCursor}%` }} />
          {series.map(serie => {
            const valor = serie.valores[cursor];
            return valor === null || valor === undefined ? null : (
              <span
                key={serie.nome}
                className="pd-cursor-ponto"
                style={{ left: `${xCursor}%`, top: `${(yDoValor(valor, escala) / ALTURA) * 100}%`, borderColor: cor }}
              />
            );
          })}
          <div className={`pd-dica${xCursor > 60 ? ' pd-dica-esquerda' : ''}`} style={{ left: `${xCursor}%` }} role="status">
            <div className="pd-dica-quando">{idade === null ? '' : idade <= 0 ? 'agora' : `há ${idade} s`}</div>
            {series.map(serie => {
              const valor = serie.valores[cursor];
              return (
                <div key={serie.nome} className="pd-dica-linha">
                  <AmostraDeCor cor={cor} estilo={serie.estilo} />
                  <span className="pd-dica-nome">{serie.nome}</span>
                  <strong>{valor === null || valor === undefined ? '—' : formatar(valor)}</strong>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
};

export default GraficoDesempenho;
