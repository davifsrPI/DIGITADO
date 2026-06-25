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

const DEFAULT_CFG = { tempoLimite: 30, qtdFacil: 5, qtdMedio: 5, qtdDificil: 5, palavrasExtrasIds: [] as number[] };

export const SalaJogoProfessor: React.FC<Props> = ({ estado, codigoSala, conectado, onIniciar, onProxima, onPausar, onEncerrar }) => {
  const [tempoRestante, setTempoRestante] = useState(0);
  const [confirmEncerrar, setConfirmEncerrar] = useState(false);
  const [copied, setCopied] = useState(false);

  const copiarCodigo = () => {
    navigator.clipboard.writeText(codigoSala).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

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
  const pct = estado && estado.tempoLimite > 0 ? (tempoRestante / estado.tempoLimite) * 100 : 0;

  /* ── LOBBY (aguardando) ─────────────────────────────────────────── */
  if (!estado || estado.tipo === 'AGUARDANDO') {
    const alunos = estado?.alunosConectados ?? [];
    return (
      <div className="sj-lobby">
        <div className="sj-lobby-header">
          <div className="sj-lobby-badge">Aguardando alunos</div>
          <h1 className="sj-lobby-title">Sala pronta!</h1>
          <p className="sj-lobby-sub">Compartilhe o código abaixo com seus alunos para eles entrarem</p>
        </div>

        <button className="sj-codigo-block" onClick={copiarCodigo} title="Clique para copiar">
          <span className="sj-codigo-label">CÓDIGO DA SALA</span>
          <span className="sj-codigo-val">{codigoSala}</span>
          <span className="sj-codigo-copy">{copied ? '✓ Copiado!' : 'clique para copiar'}</span>
        </button>

        <div className="sj-alunos-panel">
          <div className="sj-alunos-header">
            <span className="sj-alunos-title">Alunos conectados</span>
            <span className="sj-alunos-badge">{alunos.length}</span>
          </div>
          {alunos.length === 0 ? (
            <p className="sj-no-alunos">Nenhum aluno entrou ainda...</p>
          ) : (
            <ul className="sj-alunos-list">
              {alunos.map(a => (
                <li key={a.login} className="sj-aluno-item">
                  <span className="sj-aluno-avatar">{(a.nome || a.login).charAt(0).toUpperCase()}</span>
                  <span className="sj-aluno-nome">{a.nome || a.login}</span>
                  <span className="sj-aluno-dot" />
                </li>
              ))}
            </ul>
          )}
        </div>

        <button className="sj-iniciar-btn" disabled={!conectado} onClick={() => onIniciar(DEFAULT_CFG)}>
          {conectado ? '▶ Iniciar partida' : 'Conectando...'}
        </button>
      </div>
    );
  }

  /* ── ENCERRADA ──────────────────────────────────────────────────── */
  if (estado.tipo === 'ENCERRADA') {
    return (
      <div className="sj-ended">
        <h2 className="sj-ended-title">Atividade encerrada!</h2>
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

  /* ── EM JOGO ────────────────────────────────────────────────────── */
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
