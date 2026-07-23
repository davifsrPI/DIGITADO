import './minhas-salas.scss';

import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useBodyClass } from 'app/shared/util/use-body-class';

type Filtro = 'todas' | 'abertas' | 'fechadas';

// A descrição vem do backend como objeto JSON: o texto livre + o modo da sala (1v1/normal)
interface DescricaoSala {
  descricao?: string | null;
  modo?: '1v1' | 'normal';
}

// O código de acesso identifica a sala - é a chave primária no banco
interface Sala {
  codigo: string;
  nome: string;
  descricao?: DescricaoSala | string | null;
  ativo: boolean;
}

// Texto exibível da descrição - tolera o formato antigo (string pura) e o novo (objeto)
const textoDescricao = (d: Sala['descricao']): string | null => (typeof d === 'string' ? d : (d?.descricao ?? null));

// A sala é de duelo 1v1? (lido do JSON da descrição)
const ehDuelo = (d: Sala['descricao']): boolean => typeof d === 'object' && d?.modo === '1v1';

// Página do professor para gerenciar suas salas: lista, filtra por status (abertas/fechadas)
// e permite entrar como professor direto para a tela de jogo
export const MinhasSalas = () => {
  const navigate = useNavigate();
  const [salas, setSalas] = useState<Sala[]>([]);
  const [loading, setLoading] = useState(true);
  const [filtro, setFiltro] = useState<Filtro>('todas');

  // Adiciona classe ao body para aplicar o fundo específico desta página
  useBodyClass('minhas-salas-page');

  // Busca as salas do professor sempre que o filtro muda - passa o parâmetro ativo quando necessário
  useEffect(() => {
    setLoading(true);
    const params: Record<string, string> = {};
    if (filtro === 'abertas') params.ativo = 'true';
    if (filtro === 'fechadas') params.ativo = 'false';
    axios
      .get<Sala[]>('/api/salas', { params })
      .then(res => setSalas(res.data))
      .catch(() => setSalas([]))
      .finally(() => setLoading(false));
  }, [filtro]);

  // Alterna o status da sala entre aberta/fechada via PATCH e atualiza o estado local
  const toggleAtivo = async (sala: Sala) => {
    try {
      await axios.patch(`/api/salas/${sala.codigo}`, { codigo: sala.codigo, ativo: !sala.ativo });
      setSalas(prev => prev.map(s => (s.codigo === sala.codigo ? { ...s, ativo: !s.ativo } : s)));
    } catch {
      // silent
    }
  };

  return (
    <div className="ms-wrapper">
      <div className="ms-bg">
        <div className="ms-shape one" />
        <div className="ms-shape two" />
        <div className="ms-shape three" />
      </div>

      <div className="ms-center">
        <button className="ms-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <div className="ms-header">
          <h1 className="ms-title">Minhas Salas</h1>
          <Link to="/sala/new" className="ms-new-btn">
            + Nova sala
          </Link>
        </div>

        <div className="ms-filters">
          {(['todas', 'abertas', 'fechadas'] as Filtro[]).map(f => (
            <button key={f} className={`ms-filter-btn${filtro === f ? ' ms-filter-btn--active' : ''}`} onClick={() => setFiltro(f)}>
              {f.charAt(0).toUpperCase() + f.slice(1)}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="ms-loading">Carregando salas...</div>
        ) : salas.length === 0 ? (
          <div className="ms-empty">
            <div className="ms-empty-icon">🏫</div>
            <p>Nenhuma sala encontrada.</p>
            <Link to="/sala/new" className="ms-new-btn">
              Criar primeira sala
            </Link>
          </div>
        ) : (
          <div className="ms-grid">
            {salas.map(sala => (
              <div key={sala.codigo} className={`ms-card${sala.ativo ? '' : ' ms-card--closed'}`}>
                <div className="ms-card-top">
                  <span className={`ms-badge${sala.ativo ? ' ms-badge--open' : ' ms-badge--closed'}`}>
                    {sala.ativo ? 'Aberta' : 'Fechada'}
                  </span>
                  <button className="ms-toggle-btn" onClick={() => toggleAtivo(sala)} title={sala.ativo ? 'Fechar sala' : 'Reabrir sala'}>
                    {sala.ativo ? '🔓' : '🔒'}
                  </button>
                </div>
                <div className="ms-card-nome">
                  {ehDuelo(sala.descricao) && <span title="Duelo 1v1">⚔️ </span>}
                  {sala.nome}
                </div>
                {textoDescricao(sala.descricao) && <div className="ms-card-desc">{textoDescricao(sala.descricao)}</div>}
                <div className="ms-card-codigo">{sala.codigo}</div>
                <button
                  className="ms-entrar-btn"
                  onClick={() => navigate(`/sala/${sala.codigo}`, { state: { isProfessor: true } })}
                  disabled={!sala.ativo}
                >
                  {sala.ativo ? 'Entrar como professor →' : 'Sala fechada'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default MinhasSalas;
