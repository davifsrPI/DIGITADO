import './conquistas.scss';

import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useBodyClass } from 'app/shared/util/use-body-class';

// Uma conquista já com o estado do usuário logado, calculado pelo backend
interface ConquistaUsuario {
  id: number;
  nome: string;
  descricao?: string;
  xpRecompensa?: number;
  desbloqueada: boolean;
  progresso?: number;
  dataConquista?: string;
}

// Resumo + lista, tudo montado no backend a partir do token JWT
interface MinhasConquistas {
  total: number;
  desbloqueadas: number;
  xpGanho: number;
  conquistas: ConquistaUsuario[];
}

// Ícones decorativos das conquistas - escolhidos de forma determinística pelo id,
// apenas visual (nenhum dado é guardado ou calculado no front)
const ICONES = ['🏆', '⭐', '🚀', '🔥', '⚡', '🎯', '🧠', '📚', '⌨️', '💎', '🥇', '🎖️'];
const iconeDaConquista = (id: number) => ICONES[Math.abs(id) % ICONES.length];

const formatarData = (iso?: string) => {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });
};

// Tela "Minhas Conquistas" (estilo Steam): o front apenas exibe o que o backend retorna.
// A busca em /api/conquistas/minhas não envia parâmetro algum - o usuário é identificado
// exclusivamente pelo token no backend, e a consulta ao banco é feita lá.
export const Conquistas = () => {
  const navigate = useNavigate();
  const [dados, setDados] = useState<MinhasConquistas | null>(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState(false);

  // Aplica o fundo escuro específico desta página (mesmo padrão do lobby/minhas-salas)
  useBodyClass('conquistas-page');

  // Busca as conquistas do usuário no backend - sem cache, sem localStorage
  useEffect(() => {
    setLoading(true);
    axios
      .get<MinhasConquistas>('/api/conquistas/minhas')
      .then(res => {
        setDados(res.data);
        setErro(false);
      })
      .catch(() => setErro(true))
      .finally(() => setLoading(false));
  }, []);

  const percentual = dados && dados.total > 0 ? Math.round((dados.desbloqueadas / dados.total) * 100) : 0;

  return (
    <div className="cq-wrapper">
      <div className="cq-bg">
        <div className="cq-shape one" />
        <div className="cq-shape two" />
        <div className="cq-shape three" />
      </div>

      <div className="cq-center">
        <button className="cq-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <div className="cq-header">
          <h1 className="cq-title">🏅 Minhas Conquistas</h1>
          <p className="cq-subtitle">Acompanhe tudo o que você já desbloqueou no DIGITADO</p>
        </div>

        {loading ? (
          <div className="cq-loading">Carregando conquistas...</div>
        ) : erro ? (
          <div className="cq-empty">
            <div className="cq-empty-icon">⚠️</div>
            <p>Não foi possível carregar suas conquistas. Tente novamente mais tarde.</p>
          </div>
        ) : dados && dados.total > 0 ? (
          <>
            {/* Resumo estilo Steam: "Você desbloqueou X de Y conquistas (Z%)" */}
            <div className="cq-summary">
              <div className="cq-summary-top">
                <span className="cq-summary-text">
                  Você desbloqueou <strong>{dados.desbloqueadas}</strong> de <strong>{dados.total}</strong> conquistas ({percentual}%)
                </span>
                <span className="cq-xp-chip">⚡ {dados.xpGanho} XP</span>
              </div>
              <div className="cq-progress-track">
                <div className="cq-progress-fill" style={{ width: `${percentual}%` }} />
              </div>
            </div>

            {/* Lista de conquistas: desbloqueadas coloridas, bloqueadas apagadas */}
            <div className="cq-lista">
              {dados.conquistas.map(c => (
                <div key={c.id} className={`cq-item${c.desbloqueada ? '' : ' cq-item--locked'}`}>
                  <div className="cq-item-icon">{c.desbloqueada ? iconeDaConquista(c.id) : '🔒'}</div>
                  <div className="cq-item-info">
                    <div className="cq-item-nome">{c.nome}</div>
                    {c.descricao && <div className="cq-item-desc">{c.descricao}</div>}
                    {!c.desbloqueada && c.progresso != null && c.progresso > 0 && (
                      <div className="cq-item-progress">
                        <div className="cq-item-progress-track">
                          <div className="cq-item-progress-fill" style={{ width: `${Math.min(c.progresso, 100)}%` }} />
                        </div>
                        <span className="cq-item-progress-label">{Math.min(c.progresso, 100)}%</span>
                      </div>
                    )}
                  </div>
                  <div className="cq-item-meta">
                    {c.xpRecompensa != null && <span className="cq-item-xp">+{c.xpRecompensa} XP</span>}
                    {c.desbloqueada ? (
                      <span className="cq-item-data">Desbloqueada{c.dataConquista ? ` em ${formatarData(c.dataConquista)}` : ''}</span>
                    ) : (
                      <span className="cq-item-data cq-item-data--locked">Bloqueada</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="cq-empty">
            <div className="cq-empty-icon">🏅</div>
            <p>Nenhuma conquista disponível ainda. Continue jogando!</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default Conquistas;
