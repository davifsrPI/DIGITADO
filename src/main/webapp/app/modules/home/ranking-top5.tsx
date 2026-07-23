import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faMedal } from '@fortawesome/free-solid-svg-icons';

interface RankingEntry {
  posicao: number;
  nome: string;
  xp: number;
}

interface RankingMundial {
  top: RankingEntry[];
}

// Medalhas de ouro, prata e bronze do TOP 3 (ícone único com cores distintas)
const CORES_MEDALHAS = ['#fbbf24', '#94a3b8', '#b45309'];
const MEDALHAS = CORES_MEDALHAS.map((cor, i) => <FontAwesomeIcon key={i} icon={faMedal} style={{ color: cor }} />);

// Seção "Ranking Mundial" da tela inicial: mostra o TOP 5 do ranking público
// (endpoint aberto - visitante sem conta também vê) com link para o ranking
// completo. Se o backend falhar ou ninguém tiver pontuado, a seção não aparece.
export const RankingTop5 = () => {
  const [top5, setTop5] = useState<RankingEntry[]>([]);

  useEffect(() => {
    axios
      .get<RankingMundial>('/api/public/ranking-mundial')
      .then(res => setTop5((res.data.top ?? []).slice(0, 5)))
      .catch(() => setTop5([]));
  }, []);

  if (top5.length === 0) return null;

  return (
    <section className="rt5-section">
      <div className="section-label">Ranking Mundial</div>
      <h2 className="section-title">Os 5 melhores do momento</h2>

      <div className="rt5-card">
        {top5.map((e, i) => (
          <div key={e.posicao} className={`rt5-row${i === 0 ? ' rt5-row--lider' : ''}`}>
            <span className="rt5-pos">{i < 3 ? MEDALHAS[i] : `${e.posicao}º`}</span>
            <span className="rt5-avatar">{e.nome.charAt(0).toUpperCase()}</span>
            <span className="rt5-nome">{e.nome}</span>
            <span className="rt5-xp">{e.xp} XP</span>
          </div>
        ))}

        <Link to="/ranking" className="rt5-ver-todos">
          Ver ranking completo →
        </Link>
      </div>
    </section>
  );
};

export default RankingTop5;
