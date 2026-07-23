import './ranking.scss';

import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faMedal, faRocket } from '@fortawesome/free-solid-svg-icons';

import { useAppSelector } from 'app/config/store';
import { useBodyClass } from 'app/shared/util/use-body-class';

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
  // Total de pessoas no ranking e se ainda há páginas para carregar
  total: number;
  temMais: boolean;
}

// Medalhas de ouro, prata e bronze do pódio (ícone único com cores distintas)
const CORES_MEDALHAS = ['#fbbf24', '#94a3b8', '#b45309'];
const MEDALHAS = CORES_MEDALHAS.map((cor, i) => <FontAwesomeIcon key={i} icon={faMedal} style={{ color: cor }} />);

// Ranking Mundial: classificação de todos os usuários pelo XP acumulado
// (hoje alimentado pelos acertos na Palavra do Dia - 300 XP cada)
export const Ranking = () => {
  // A página é PÚBLICA (visitante sem conta pode ver o ranking) - o login só
  // muda o destino do botão de voltar e o destaque da própria posição
  const account = useAppSelector(state => state.authentication.account);
  const logado = Boolean(account?.login);
  const [dados, setDados] = useState<RankingMundial | null>(null);
  const [erro, setErro] = useState(false);
  const [pagina, setPagina] = useState(0);
  const [carregandoMais, setCarregandoMais] = useState(false);

  useBodyClass('ranking-page');

  useEffect(() => {
    axios
      .get<RankingMundial>('/api/public/ranking-mundial')
      .then(res => setDados(res.data))
      .catch(() => setErro(true));
  }, []);

  // Busca a próxima página do backend e ANEXA as posições às já exibidas -
  // assim dá para navegar até o fim e ver todas as pessoas do ranking
  const carregarMais = () => {
    if (carregandoMais || !dados?.temMais) return;
    setCarregandoMais(true);
    axios
      .get<RankingMundial>('/api/public/ranking-mundial', { params: { page: pagina + 1 } })
      .then(res => {
        setPagina(p => p + 1);
        setDados(prev => (prev ? { ...res.data, top: [...prev.top, ...res.data.top] } : res.data));
      })
      .catch(() => setErro(true))
      .finally(() => setCarregandoMais(false));
  };

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
        {/* Visitante sem conta volta para a tela inicial; logado volta ao lobby */}
        <Link to={logado ? '/lobby' : '/'} className="rk-back">
          {logado ? '← Voltar ao lobby' : '← Voltar ao início'}
        </Link>

        <div className="rk-header">
          {/* Planeta desenhado em CSS girando de forma fluida: os continentes
              deslizam em loop atrás da máscara circular, com sombra de esfera */}
          <div className="rk-planeta" aria-hidden="true">
            <div className="rk-planeta-mapa" />
            <div className="rk-planeta-luz" />
          </div>
          <div className="rk-badge">Ranking Mundial</div>
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

            {dados.top.length === 0 && (
              <p className="rk-vazio">
                Ninguém pontuou ainda - acerte a palavra do dia e seja o primeiro! <FontAwesomeIcon icon={faRocket} />
              </p>
            )}

            {/* Ranking completo: mostra quantos aparecem e busca as próximas páginas até o fim */}
            {dados.top.length > 0 && (
              <div className="rk-paginacao">
                <span className="rk-contagem">
                  Mostrando <strong>{dados.top.length}</strong> de <strong>{dados.total}</strong> participantes
                </span>
                {dados.temMais && (
                  <button className="rk-mais-btn" onClick={carregarMais} disabled={carregandoMais}>
                    {carregandoMais ? 'Carregando...' : 'Carregar mais ↓'}
                  </button>
                )}
              </div>
            )}

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
