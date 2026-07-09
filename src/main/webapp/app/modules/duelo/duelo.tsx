import './duelo.scss';

import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';

// Os códigos de sala têm sempre 6 caracteres (mesma regra de criar-sala)
const CODE_LEN = 6;

// Duelo público retornado pelo backend — apenas campos públicos, sem dados do criador
interface DueloPublico {
  codigo: string;
  nome: string;
  descricao?: string;
  jogadores: number;
}

// Página do modo Duelo 1v1: lista os duelos PÚBLICOS abertos (globais, qualquer um entra),
// permite entrar num duelo PRIVADO digitando o código, e criar um duelo novo
// reaproveitando a tela de criação de salas (?modo=1v1).
export const Duelo = () => {
  const navigate = useNavigate();
  const [duelos, setDuelos] = useState<DueloPublico[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [codigoPrivado, setCodigoPrivado] = useState('');

  useEffect(() => {
    document.body.classList.add('duelo-page');
    return () => document.body.classList.remove('duelo-page');
  }, []);

  // Busca a lista de duelos públicos no backend; o polling de 10s mantém a lista viva
  // enquanto o jogador decide (duelos enchem e somem rápido)
  const carregarDuelos = async () => {
    try {
      const { data } = await axios.get<DueloPublico[]>('/api/salas/1v1/publicas');
      setDuelos(data);
      setErro(null);
    } catch {
      setErro('Não foi possível carregar os duelos. Tente novamente.');
    } finally {
      setCarregando(false);
    }
  };

  useEffect(() => {
    carregarDuelos();
    const timer = setInterval(carregarDuelos, 10000);
    return () => clearInterval(timer);
  }, []);

  const entrarPrivado = (e: React.FormEvent) => {
    e.preventDefault();
    const code = codigoPrivado.trim().toUpperCase();
    if (code.length === CODE_LEN) navigate(`/sala/${code}`);
  };

  return (
    <div className="duelo-wrapper">
      <div className="duelo-bg">
        <div className="du-shape one"></div>
        <div className="du-shape two"></div>
      </div>

      <div className="duelo-content">
        <button className="duelo-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <div className="duelo-header">
          <h1>
            ⚔️ Duelo <span className="duelo-accent">1v1</span>
          </h1>
          <p>Desafie outro jogador em uma partida direta — quem digitar melhor vence</p>
        </div>

        <Link to="/sala/new?modo=1v1" className="duelo-create-btn">
          + Criar um duelo
        </Link>

        <div className="duelo-grid">
          {/* ---- DUELOS PÚBLICOS ---- */}
          <div className="duelo-card">
            <div className="duelo-card-head">
              <h2>🌎 Duelos públicos</h2>
              <button className="duelo-refresh" onClick={carregarDuelos} aria-label="Atualizar lista">
                🔄
              </button>
            </div>
            <p className="duelo-card-sub">Qualquer jogador pode entrar — clique e boa sorte!</p>

            {erro && <div className="duelo-erro">{erro}</div>}
            {carregando && <div className="duelo-vazio">Carregando duelos...</div>}
            {!carregando && !erro && duelos.length === 0 && (
              <div className="duelo-vazio">
                Nenhum duelo público aberto agora.
                <br />
                Crie o seu e espere um oponente!
              </div>
            )}

            <ul className="duelo-lista">
              {duelos.map(d => (
                <li key={d.codigo} className="duelo-item">
                  <div className="duelo-item-info">
                    <strong>{d.nome}</strong>
                    <span className="duelo-item-meta">
                      {d.jogadores}/2 jogadores{d.descricao ? ` · ${d.descricao}` : ''}
                    </span>
                  </div>
                  <button className="duelo-join-btn" onClick={() => navigate(`/sala/${d.codigo}`)}>
                    Entrar →
                  </button>
                </li>
              ))}
            </ul>
          </div>

          {/* ---- DUELO PRIVADO ---- */}
          <div className="duelo-card">
            <h2>🔒 Duelo privado</h2>
            <p className="duelo-card-sub">Recebeu um código? Digite abaixo para entrar no duelo</p>
            <form onSubmit={entrarPrivado} className="duelo-priv-form">
              <input
                type="text"
                className="duelo-priv-input"
                placeholder="CÓDIGO"
                value={codigoPrivado}
                onChange={e =>
                  setCodigoPrivado(
                    e.target.value
                      .toUpperCase()
                      .replace(/[^A-Z0-9]/g, '')
                      .slice(0, CODE_LEN),
                  )
                }
                maxLength={CODE_LEN}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="characters"
                spellCheck={false}
                aria-label="Código do duelo privado"
              />
              <button type="submit" className="duelo-join-btn" disabled={codigoPrivado.length < CODE_LEN}>
                Entrar no duelo →
              </button>
            </form>
          </div>
        </div>

        <div className="duelo-conquistas-hint">
          🏅 Duelos 1v1 valem conquistas exclusivas — incluindo <strong>“Vença de um Desenvolvedor”</strong> (+1000 XP)!
        </div>
      </div>
    </div>
  );
};

export default Duelo;
