import './ranking.scss';

import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';

interface RankingEntry {
  posicao: number;
  nome: string;
  xp: number;
  eu: boolean;
}

interface RankingMundial {
  top: RankingEntry[];
  meuXp: number;
  minhaPosicao?: number;
}

const MEDALHAS = ['🥇', '🥈', '🥉'];

// Ranking Mundial: classificação de todos os usuários pelo XP acumulado
// (hoje alimentado pelos acertos na Palavra do Dia — 300 XP cada)
export const Ranking = () => {
  const [dados, setDados] = useState<RankingMundial | null>(null);
  const [erro, setErro] = useState(false);

  useEffect(() => {
    document.body.classList.add('ranking-page');
    return () => document.body.classList.remove('ranking-page');
  }, []);

  useEffect(() => {
    axios
      .get<RankingMundial>('/api/ranking-mundial')
      .then(res => setDados(res.data))
      .catch(() => setErro(true));
  }, []);

  const top3 = dados?.top.slice(0, 3) ?? [];
  const demais = dados?.top.slice(3) ?? [];
  const estouNoTop = dados?.top.some(e => e.eu) ?? false;

  return (
    <div className="rk-wrapper">
      <div className="rk-bg">
        <div className="rk-shape one" />
        <div className="rk-shape two" />
      </div>

      <div className="rk-content">
        <Link to="/lobby" className="rk-back">
          ← Voltar ao lobby
        </Link>

        <div className="rk-header">
          <div className="rk-badge">🌍 Ranking Mundial</div>
          <h1 className="rk-title">Os melhores do DIGITADO</h1>
          <p className="rk-sub">Ganhe XP acertando a palavra do dia e desbloqueando conquistas</p>
        </div>

        {erro && <p className="rk-vazio">Não foi possível carregar o ranking. Tente novamente.</p>}
        {!erro && !dados && <p className="rk-vazio">Carregando...</p>}

        {dados && (
          <>
            {/* Pódio dos 3 primeiros */}
            {top3.length > 0 && (
              <div className="rk-podio">
                {[1, 0, 2].map(idx => {
                  const e = top3[idx];
                  if (!e) return null;
                  return (
                    <div key={e.posicao} className={`rk-podio-col rk-podio-${idx + 1}${e.eu ? ' rk-eu' : ''}`}>
                      <span className="rk-podio-medalha">{MEDALHAS[idx]}</span>
                      <span className="rk-podio-avatar">{e.nome.charAt(0).toUpperCase()}</span>
                      <span className="rk-podio-nome">
                        {e.nome}
                        {e.eu ? ' (você)' : ''}
                      </span>
                      <span className="rk-podio-xp">{e.xp} XP</span>
                      <div className="rk-podio-pilar">{e.posicao}º</div>
                    </div>
                  );
                })}
              </div>
            )}

            {/* Demais posições */}
            {demais.length > 0 && (
              <div className="rk-lista">
                {demais.map(e => (
                  <div key={e.posicao} className={`rk-row${e.eu ? ' rk-eu' : ''}`}>
                    <span className="rk-row-pos">{e.posicao}º</span>
                    <span className="rk-row-avatar">{e.nome.charAt(0).toUpperCase()}</span>
                    <span className="rk-row-nome">
                      {e.nome}
                      {e.eu ? ' (você)' : ''}
                    </span>
                    <span className="rk-row-xp">{e.xp} XP</span>
                  </div>
                ))}
              </div>
            )}

            {dados.top.length === 0 && <p className="rk-vazio">Ninguém pontuou ainda — acerte a palavra do dia e seja o primeiro! 🚀</p>}

            {/* Resumo do próprio usuário quando está fora do top exibido */}
            {!estouNoTop && dados.minhaPosicao != null && (
              <div className="rk-meu-resumo">
                Sua posição: <strong>{dados.minhaPosicao}º</strong> · <strong>{dados.meuXp} XP</strong>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default Ranking;
