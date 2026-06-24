import React, { useEffect, useState } from 'react';
import { EstadoJogo } from './hooks/useSalaWebSocket';

type Dificuldade = 'FACIL' | 'MEDIO' | 'DIFICIL';

interface Props {
  estado: EstadoJogo | null;
  codigoSala: string;
  conectado: boolean;
  onIniciar: (cfg: { tempoLimite: number; qtdFacil: number; qtdMedio: number; qtdDificil: number; palavrasExtrasIds: number[] }) => void;
  onProxima: () => void;
  onPausar: () => void;
  onEncerrar: () => void;
}

const DIFFS: Array<{ key: Dificuldade; color: string; label: string }> = [
  { key: 'FACIL', color: '#3B6D11', label: 'Fáceis' },
  { key: 'MEDIO', color: '#854F0B', label: 'Médias' },
  { key: 'DIFICIL', color: '#A32D2D', label: 'Difíceis' },
];

const DIFF_BG: Record<Dificuldade, string> = {
  FACIL: '#EAF3DE',
  MEDIO: '#FAEEDA',
  DIFICIL: '#FCEBEB',
};

export const SalaJogoProfessor: React.FC<Props> = ({ estado, codigoSala, conectado, onIniciar, onProxima, onPausar, onEncerrar }) => {
  const [tempo, setTempo] = useState(30);
  const [qtd, setQtd] = useState<Record<Dificuldade, number>>({ FACIL: 5, MEDIO: 5, DIFICIL: 5 });
  const [tempoRestante, setTempoRestante] = useState(0);
  const [confirmEncerrar, setConfirmEncerrar] = useState(false);

  const adj = (k: Dificuldade, d: number) => setQtd(prev => ({ ...prev, [k]: Math.max(0, Math.min(30, prev[k] + d)) }));

  useEffect(() => {
    if (!estado || (estado.tipo !== 'NOVA_PALAVRA' && estado.tipo !== 'INICIADA')) {
      setTempoRestante(0);
      return;
    }
    const calc = () => {
      const elapsed = Date.now() - estado.timestampInicio;
      setTempoRestante(Math.max(0, estado.tempoLimite - Math.floor(elapsed / 1000)));
    };
    calc();
    const id = setInterval(calc, 500);
    return () => clearInterval(id);
  }, [estado?.timestampInicio, estado?.tempoLimite, estado?.tipo]);

  const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
  const pausada = estado?.tipo === 'PAUSADA';
  const emAndamento = ativo || pausada;
  const pct = estado && estado.tempoLimite > 0 ? (tempoRestante / estado.tempoLimite) * 100 : 0;

  if (!estado || estado.tipo === 'AGUARDANDO') {
    const total = qtd.FACIL + qtd.MEDIO + qtd.DIFICIL;
    return (
      <div className="sj-prof-lobby">
        <div className="sj-prof-lobby-left">
          <div className="sj-codigo-card">
            <div className="sj-codigo-label">CÓDIGO DA SALA</div>
            <div className="sj-codigo-val">{codigoSala}</div>
            <div className="sj-codigo-hint">Compartilhe com seus alunos</div>
          </div>

          <div className="sj-config-card">
            <h3 className="sj-config-title">Configurar atividade</h3>

            <div className="sj-config-field">
              <label>
                Tempo por palavra: <strong>{tempo}s</strong>
              </label>
              <input
                type="range"
                min={10}
                max={60}
                step={5}
                value={tempo}
                onChange={e => setTempo(Number(e.target.value))}
                className="sj-range"
              />
            </div>

            <div className="sj-diffs">
              {DIFFS.map(({ key, color, label }) => (
                <div className="sj-diff-row" key={key}>
                  <span className="sj-diff-dot" style={{ background: color }} />
                  <span className="sj-diff-label">{label}</span>
                  <button type="button" className="sj-adj-btn" onClick={() => adj(key, -1)}>
                    −
                  </button>
                  <span className="sj-diff-val">{qtd[key]}</span>
                  <button type="button" className="sj-adj-btn" onClick={() => adj(key, 1)}>
                    +
                  </button>
                </div>
              ))}
              <div className="sj-diff-total">
                Total: <strong>{total}</strong> palavras
              </div>
            </div>

            <button
              className="sj-iniciar-btn"
              disabled={total === 0 || !conectado}
              onClick={() =>
                onIniciar({ tempoLimite: tempo, qtdFacil: qtd.FACIL, qtdMedio: qtd.MEDIO, qtdDificil: qtd.DIFICIL, palavrasExtrasIds: [] })
              }
            >
              {conectado ? '▶ Iniciar atividade' : 'Conectando...'}
            </button>
          </div>
        </div>

        <div className="sj-prof-lobby-right">
          <div className="sj-alunos-card">
            <h3 className="sj-config-title">
              Alunos na sala
              <span className="sj-alunos-count">{estado?.alunosConectados.length ?? 0}</span>
            </h3>
            {!estado?.alunosConectados || estado.alunosConectados.length === 0 ? (
              <p className="sj-no-alunos">Nenhum aluno conectado ainda.</p>
            ) : (
              <ul className="sj-alunos-list">
                {estado.alunosConectados.map(a => (
                  <li key={a.login} className="sj-aluno-item">
                    <span className="sj-aluno-avatar">{(a.nome || a.login).charAt(0).toUpperCase()}</span>
                    <span className="sj-aluno-nome">{a.nome || a.login}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    );
  }

  if (estado.tipo === 'ENCERRADA') {
    return (
      <div className="sj-ended">
        <h2>Atividade encerrada!</h2>
        <div className="sj-final-placar">
          {estado.placar.map((p, i) => (
            <div key={p.login} className="sj-final-row">
              <span className="sj-final-rank">{i + 1}º</span>
              <span className="sj-final-nome">{p.nome || p.login}</span>
              <span className="sj-final-pts">{p.pontos} pts</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  const diff = (estado.palavraAtual?.dificuldade ?? 'FACIL') as Dificuldade;

  return (
    <div className="sj-prof-game">
      <div className="sj-prof-topbar">
        <div>
          <div className="sj-sala-nome">{estado.nomeSala}</div>
          <div className="sj-palavra-prog">
            {pausada ? '⏸ Pausada · ' : ''}palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
          </div>
        </div>
        <div className="sj-topbar-right">
          <span className="sj-conectados">{estado.alunosConectados.length} aluno(s)</span>
          <span className="sj-codigo-pill">{codigoSala}</span>
        </div>
      </div>

      <div className="sj-prof-main">
        <div className="sj-prof-left">
          <div className="sj-word-card">
            <div className="sj-word-label">PALAVRA ATUAL</div>
            <div className="sj-word-text">{estado.palavraAtual?.texto ?? '—'}</div>
            {estado.palavraAtual && (
              <div className="sj-word-meta">
                <span className="sj-diff-badge" style={{ background: DIFF_BG[diff], color: DIFFS.find(d => d.key === diff)?.color }}>
                  {diff === 'FACIL' ? 'Fácil' : diff === 'MEDIO' ? 'Médio' : 'Difícil'}
                </span>
                {estado.palavraAtual.categoria && <span className="sj-cat-tag">{estado.palavraAtual.categoria}</span>}
              </div>
            )}
          </div>

          <div className="sj-timer-section">
            <div className="sj-timer-row">
              <span className="sj-timer-label">Tempo restante</span>
              <span className={`sj-timer-val${tempoRestante <= 5 ? ' sj-timer-danger' : ''}`}>{tempoRestante}s</span>
            </div>
            <div className="sj-timer-bar-bg">
              <div className="sj-timer-bar-fill" style={{ width: `${pct}%`, background: tempoRestante <= 5 ? '#E24B4A' : '#1D9E75' }} />
            </div>
          </div>

          <div className="sj-progress-row">
            <span className="sj-progress-label">
              palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
            </span>
            <div className="sj-progress-dots">
              {Array.from({ length: estado.totalPalavras }, (_, i) => (
                <div
                  key={i}
                  className={`sj-pdot${i < estado.indiceAtual ? ' sj-pdot--done' : i === estado.indiceAtual ? ' sj-pdot--current' : ''}`}
                />
              ))}
            </div>
          </div>

          <div className="sj-controls">
            {ativo ? (
              <button className="sj-ctrl-btn" onClick={onPausar}>
                ⏸ Pausar
              </button>
            ) : (
              <button className="sj-ctrl-btn" onClick={onProxima}>
                ▶ Retomar
              </button>
            )}
            <button className="sj-ctrl-btn sj-ctrl-primary" onClick={onProxima} disabled={estado.indiceAtual >= estado.totalPalavras - 1}>
              ⏭ Próxima palavra
            </button>
            {!confirmEncerrar ? (
              <button className="sj-ctrl-btn sj-ctrl-danger" onClick={() => setConfirmEncerrar(true)}>
                ⏹ Encerrar
              </button>
            ) : (
              <div className="sj-confirm-row">
                <span>Confirmar?</span>
                <button
                  className="sj-ctrl-btn sj-ctrl-danger"
                  onClick={() => {
                    onEncerrar();
                    setConfirmEncerrar(false);
                  }}
                >
                  Sim
                </button>
                <button className="sj-ctrl-btn" onClick={() => setConfirmEncerrar(false)}>
                  Não
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="sj-prof-right">
          <div className="sj-placar-title">🏆 Placar ao vivo</div>
          {estado.placar.map((p, i) => (
            <div key={p.login} className="sj-placar-row">
              <span className="sj-placar-rank">{i + 1}</span>
              <span className="sj-placar-avatar">{(p.nome || p.login).charAt(0).toUpperCase()}</span>
              <span className="sj-placar-nome">{p.nome || p.login}</span>
              <span className={`sj-status-chip sj-status-${p.statusAtual?.toLowerCase() ?? 'aguardando'}`}>
                {p.statusAtual === 'ACERTOU' ? 'acertou' : p.statusAtual === 'ERROU' ? 'errou' : 'aguardando'}
              </span>
              <span className="sj-placar-pts">{p.pontos}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default SalaJogoProfessor;
