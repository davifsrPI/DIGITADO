import './minhas-salas.scss';

import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useBodyClass } from 'app/shared/util/use-body-class';

// A descrição vem do backend como objeto JSON: o texto livre + o modo da sala (1v1/normal)
interface DescricaoSala {
  descricao?: string | null;
  modo?: '1v1' | 'normal';
}

// O código de acesso identifica a sala, é a chave primária no banco
interface Sala {
  codigo: string;
  nome: string;
  descricao?: DescricaoSala | string | null;
  // A sala já teve uma partida encerrada e guardada? É o que libera o botão
  // "Ver estatísticas"
  temEstatisticas?: boolean;
}

// Texto exibível da descrição, tolera o formato antigo (string pura) e o novo (objeto)
const textoDescricao = (d: Sala['descricao']): string | null => (typeof d === 'string' ? d : (d?.descricao ?? null));

// A sala é de duelo 1v1? (lido do JSON da descrição)
const ehDuelo = (d: Sala['descricao']): boolean => typeof d === 'object' && d?.modo === '1v1';

// Página do professor para gerenciar suas salas: lista todas e permite entrar
// como professor direto para a tela de jogo.
//
// Sala NÃO FECHA MAIS. Aqui havia um cadeado por card e um filtro
// abertas/fechadas: a sala fechava sozinha quando esvaziava e, fechada, sumia
// da listagem e nem o dono entrava - levando junto o caminho para o desempenho
// da turma. Toda sala fica aberta, então o filtro e o cadeado saíram.
export const MinhasSalas = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [salas, setSalas] = useState<Sala[]>([]);
  const [loading, setLoading] = useState(true);
  // Acabou de chegar da tela de criação pelo "criar e deixar pronta": a sala nova
  // ganha um aviso no topo e um destaque no card, senão o professor voltaria para
  // uma lista igual à de antes, sem saber se a sala foi mesmo criada
  const salaCriada = (location.state as { salaCriada?: string } | null)?.salaCriada ?? null;

  // Adiciona classe ao body para aplicar o fundo específico desta página
  useBodyClass('minhas-salas-page');

  // Busca as salas do usuário logado
  useEffect(() => {
    setLoading(true);
    // meus=true: esta tela lista as salas DO USUÁRIO logado. Sem o parâmetro o
    // backend devolve ao admin a listagem crua das telas CRUD, sem o campo
    // temEstatisticas, e o botão "Ver estatísticas" some do card
    axios
      .get<Sala[]>('/api/salas', { params: { meus: 'true' } })
      .then(res => setSalas(res.data))
      .catch(() => setSalas([]))
      .finally(() => setLoading(false));
  }, []);

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

        {salaCriada && (
          <div className="ms-criada">
            <span className="ms-criada-icone">✓</span>
            <div className="ms-criada-texto">
              <strong>Sala {salaCriada} criada e pronta.</strong> As palavras já estão guardadas nela - no dia da aula, entre como professor
              e confira a lista antes de começar.
            </div>
          </div>
        )}

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
              <div key={sala.codigo} className={`ms-card${sala.codigo === salaCriada ? ' ms-card--nova' : ''}`}>
                <div className="ms-card-nome">
                  {ehDuelo(sala.descricao) && <span title="Duelo 1v1">⚔️ </span>}
                  {sala.nome}
                </div>
                {textoDescricao(sala.descricao) && <div className="ms-card-desc">{textoDescricao(sala.descricao)}</div>}
                <div className="ms-card-codigo">{sala.codigo}</div>
                <button className="ms-entrar-btn" onClick={() => navigate(`/sala/${sala.codigo}`, { state: { isProfessor: true } })}>
                  Entrar como professor →
                </button>
                {/* Desempenho da última partida, lido do snapshot no banco - e é
                    de lá que sai também o resumo de cada aluno, inclusive nas
                    salas jogadas antes desta tela existir */}
                {sala.temEstatisticas && (
                  <button className="ms-estatisticas-btn" onClick={() => navigate(`/sala/${sala.codigo}/estatisticas`)}>
                    📊 Ver estatísticas
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default MinhasSalas;
