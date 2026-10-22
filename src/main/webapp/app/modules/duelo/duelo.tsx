import './duelo.scss';

import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import TelaCarregamento from 'app/shared/layout/loading/tela-carregamento';
import { useBodyClass } from 'app/shared/util/use-body-class';
import { extrairCodigoColado, TAMANHO_CODIGO_SALA as CODE_LEN } from 'app/shared/util/codigo-sala';
import { verificarSala } from 'app/shared/util/verificar-sala';

// A descrição agora é um objeto JSON vindo do backend: o texto livre + o modo da sala
interface DescricaoSala {
  descricao?: string | null;
  modo?: '1v1' | 'normal';
}

// Duelo público retornado pelo backend, apenas campos públicos, sem dados do criador
interface DueloPublico {
  codigo: string;
  nome: string;
  descricao?: DescricaoSala | null;
  jogadores: number;
}

// Página do modo Duelo 1v1: lista os duelos PÚBLICOS abertos (globais, qualquer um entra),
// permite entrar num duelo PRIVADO digitando o código, e criar um duelo novo
// reaproveitando a tela de criação de salas (?modo=1v1).
export const Duelo = () => {
  const navigate = useNavigate();
  const [duelos, setDuelos] = useState<DueloPublico[]>([]);
  const [carregando, setCarregando] = useState(true);
  // true enquanto uma atualização MANUAL (botão) está em andamento, a lista atual
  // continua na tela; só o botão muda para "Atualizando..."
  const [atualizando, setAtualizando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [codigoPrivado, setCodigoPrivado] = useState('');
  const [verificando, setVerificando] = useState(false);
  const [erroCodigo, setErroCodigo] = useState<string | null>(null);

  useBodyClass('duelo-page');

  // Busca a lista de duelos públicos no backend; o polling de 10s mantém a lista viva
  // enquanto o jogador decide (duelos enchem e somem rápido)
  const carregarDuelos = async (manual = false) => {
    if (manual) setAtualizando(true);
    try {
      const { data } = await axios.get<DueloPublico[]>('/api/salas/1v1/publicas');
      setDuelos(data);
      setErro(null);
    } catch {
      setErro('Não foi possível carregar os duelos. Tente novamente.');
    } finally {
      setCarregando(false);
      if (manual) setAtualizando(false);
    }
  };

  useEffect(() => {
    carregarDuelos();
    const timer = setInterval(carregarDuelos, 10000);
    return () => clearInterval(timer);
  }, []);

  // Confere o código antes de sair da tela, como no lobby: código errado fica aqui,
  // com o aviso. Código de sala de TURMA segue para a entrada da turma.
  const entrarPrivado = async (e: React.FormEvent) => {
    e.preventDefault();
    const code = codigoPrivado.trim().toUpperCase();
    if (code.length !== CODE_LEN || verificando) return;
    setVerificando(true);
    setErroCodigo(null);
    const resultado = await verificarSala(code);
    setVerificando(false);
    if (resultado.status === 'ok') {
      navigate(resultado.tipo === 'UM_V_UM' ? `/sala/${code}` : `/sala/${code}/entrar`);
    } else if (resultado.status === 'inexistente') {
      setErroCodigo(`Não encontramos nenhum duelo com o código ${code}.`);
    } else {
      setErroCodigo('Não foi possível conferir o código agora. Tente de novo.');
    }
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
          <p>Desafie outro jogador em uma partida direta: quem digitar melhor vence</p>
        </div>

        <Link to="/sala/new?modo=1v1" className="duelo-create-btn">
          + Criar um duelo
        </Link>

        <div className="duelo-grid">
          {/* ---- DUELOS PÚBLICOS ---- */}
          <div className="duelo-card">
            <div className="duelo-card-head">
              <h2>🌎 Duelos públicos</h2>
              {/* Recarrega SOMENTE a lista de duelos (nada de reload da página):
                  refaz o GET e troca o estado; o resto da tela permanece intacto */}
              <button
                type="button"
                className="duelo-refresh"
                onClick={() => void carregarDuelos(true)}
                disabled={atualizando}
                aria-label="Atualizar lista de duelos"
              >
                <span className={`duelo-refresh-icone${atualizando ? ' duelo-refresh-icone--girando' : ''}`} aria-hidden="true">
                  🔄
                </span>
                {atualizando ? 'Atualizando...' : 'Atualizar'}
              </button>
            </div>
            <p className="duelo-card-sub">Qualquer jogador pode entrar, clique e boa sorte!</p>

            {erro && <div className="duelo-erro">{erro}</div>}
            {carregando && <TelaCarregamento embutido mensagem="Buscando duelos abertos..." />}
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
                      {d.jogadores}/2 jogadores{d.descricao?.descricao ? ` · ${d.descricao.descricao}` : ''}
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
                onChange={e => {
                  setErroCodigo(null);
                  setCodigoPrivado(
                    e.target.value
                      .toUpperCase()
                      .replace(/[^A-Z0-9]/g, '')
                      .slice(0, CODE_LEN),
                  );
                }}
                // Mensagem colada inteira: traz só o código (mesma regra do lobby)
                onPaste={e => {
                  e.preventDefault();
                  setErroCodigo(null);
                  setCodigoPrivado(extrairCodigoColado(e.clipboardData.getData('text')));
                }}
                maxLength={CODE_LEN}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="characters"
                spellCheck={false}
                aria-label="Código do duelo privado"
              />
              <button type="submit" className="duelo-join-btn" disabled={codigoPrivado.length < CODE_LEN || verificando}>
                {verificando ? 'Conferindo...' : 'Entrar no duelo →'}
              </button>
            </form>
            {erroCodigo && (
              <div className="duelo-erro" role="alert">
                {erroCodigo}
              </div>
            )}
          </div>
        </div>

        <div className="duelo-conquistas-hint">
          🏅 Duelos 1v1 valem conquistas exclusivas, incluindo <strong>“Vença de um Desenvolvedor”</strong> (+1000 XP)!
        </div>
      </div>
    </div>
  );
};

export default Duelo;
