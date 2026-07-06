import React, { useEffect, useState } from 'react';

interface Props {
  tempoLimite: number;
  palavraId?: number;
}

// Rodada é considerada "rápida" quando o professor configurou 20s ou menos por palavra
export const RODADA_RAPIDA_LIMITE = 20;

// Vinheta de início de rodada: um relógio voa pela tela com os ponteiros girando
// por 3 segundos — girando MUITO rápido quando o tempo da rodada é curto, para
// avisar os jogadores de que precisam se apressar. Some sozinha e não bloqueia
// cliques (pointer-events: none no CSS).
export const RelogioRodada: React.FC<Props> = ({ tempoLimite, palavraId }) => {
  const [visivel, setVisivel] = useState(false);

  // Reaparece a cada palavra nova e se esconde após 3s
  useEffect(() => {
    if (palavraId == null) return undefined;
    setVisivel(true);
    const t = setTimeout(() => setVisivel(false), 3000);
    return () => clearTimeout(t);
  }, [palavraId]);

  if (!visivel) return null;

  const rapida = tempoLimite <= RODADA_RAPIDA_LIMITE;

  return (
    <div className={`rr-overlay${rapida ? ' rr-overlay--rapida' : ''}`} aria-hidden="true">
      <div className="rr-clock">
        <div className="rr-hand rr-hand--min" />
        <div className="rr-hand rr-hand--hr" />
        <div className="rr-dot" />
      </div>
      <div className="rr-label">{rapida ? `⚡ Rodada rápida — ${tempoLimite}s!` : `🕒 ${tempoLimite} segundos por palavra`}</div>
    </div>
  );
};

export default RelogioRodada;
