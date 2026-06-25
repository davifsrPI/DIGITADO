import React, { useEffect, useRef, useState } from 'react';
import { EstadoJogo } from './hooks/useSalaWebSocket';

// Configuração do jogo escolhida pelo professor (quantidade de palavras por dificuldade e tempo)
interface GameConfig {
  tempoLimite: number;
  qtdFacil: number;
  qtdMedio: number;
  qtdDificil: number;
  palavrasExtrasIds: number[];
}

interface Props {
  estado: EstadoJogo | null;
  codigoSala: string;
  conectado: boolean;
  onIniciar: (cfg: GameConfig) => void;
  onProxima: () => void;
  onPausar: () => void;
  onEncerrar: () => void;
  onResponder: (resposta: string) => void;
  autoStart?: boolean;
  initialGameConfig?: GameConfig;
}

type Cfg = { tempoLimite: number; qtdFacil: number; qtdMedio: number; qtdDificil: number };

const DIFICULDADES: Array<{ key: keyof Omit<Cfg, 'tempoLimite'>; label: string; cor: string }> = [
  { key: 'qtdFacil', label: 'Fáceis', cor: '#4ade80' },
  { key: 'qtdMedio', label: 'Médias', cor: '#fbbf24' },
  { key: 'qtdDificil', label: 'Difíceis', cor: '#f87171' },
];

const DEFAULT_CFG: Cfg = { tempoLimite: 30, qtdFacil: 5, qtdMedio: 5, qtdDificil: 5 };
const RANKING_DURATION = 25;

// Usa a API de síntese de voz do browser para falar a palavra em português
function falarPalavra(texto: string) {
  if (!window.speechSynthesis) return;
  window.speechSynthesis.cancel();
  const utter = new SpeechSynthesisUtterance(texto);
  utter.lang = 'pt-BR';
  utter.rate = 0.85;
  window.speechSynthesis.speak(utter);
}

// Tela do professor durante a partida: lobby de espera com configurações, tela de jogo com timer,
// ranking entre palavras com contagem regressiva de 25s, e tela de encerramento com placar final
export const SalaJogoProfessor: React.FC<Props> = ({
  estado,
  codigoSala,
  conectado,
  onIniciar,
  onProxima,
  onResponder,
  autoStart,
  initialGameConfig,
}) => {
  // ─── Estado local do componente ───────────────────────────────────────────
  const [cfg, setCfg] = useState<Cfg>(initialGameConfig ?? DEFAULT_CFG);
  const didAutoStart = useRef(false);

  const [resposta, setResposta] = useState('');
  const [jaRespondeu, setJaRespondeu] = useState(false);
  const [falando, setFalando] = useState(false);
  const [tempoRestante, setTempoRestante] = useState(0);
  const [copied, setCopied] = useState(false);

  const [showRanking, setShowRanking] = useState(false);
  const [rankingTimer, setRankingTimer] = useState(0);

  const inputRef = useRef<HTMLInputElement>(null);
  const rankingTriggeredRef = useRef(false);

  // Incrementa/decrementa a quantidade de palavras de uma dificuldade, entre 0 e 30
  const adj = (campo: keyof Cfg, delta: number) => setCfg(prev => ({ ...prev, [campo]: Math.max(0, Math.min(30, prev[campo] + delta)) }));

  // Copia o código da sala para a área de transferência e mostra confirmação por 2s
  const copiarCodigo = () => {
    navigator.clipboard.writeText(codigoSala).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  // Reproduz a palavra atual via síntese de voz e controla o estado visual do botão
  const handleFalar = () => {
    if (!estado?.palavraAtual) return;
    setFalando(true);
    window.speechSynthesis.cancel();
    const utter = new SpeechSynthesisUtterance(estado.palavraAtual.texto);
    utter.lang = 'pt-BR';
    utter.rate = 0.85;
    utter.onend = () => setFalando(false);
    utter.onerror = () => setFalando(false);
    window.speechSynthesis.speak(utter);
  };

  // Envia a resposta do professor (ele também pode jogar junto com os alunos)
  const handleEnviar = (e: React.FormEvent) => {
    e.preventDefault();
    if (!resposta.trim() || jaRespondeu) return;
    onResponder(resposta.trim());
    setJaRespondeu(true);
  };

  // Inicia o jogo automaticamente assim que a conexão WebSocket é estabelecida
  // (quando o professor vem da tela de criação com autoStart=true)
  useEffect(() => {
    if (autoStart && conectado && !didAutoStart.current && (!estado || estado.tipo === 'AGUARDANDO')) {
      didAutoStart.current = true;
      onIniciar(initialGameConfig ?? { ...cfg, palavrasExtrasIds: [] });
    }
  }, [conectado, estado?.tipo]);

  // Reseta o estado de resposta e fala a palavra automaticamente ao mudar de palavra
  useEffect(() => {
    setResposta('');
    setJaRespondeu(false);
    setFalando(false);
    setShowRanking(false);
    rankingTriggeredRef.current = false;
    if (estado?.palavraAtual?.texto) {
      setTimeout(() => falarPalavra(estado.palavraAtual.texto), 300);
      setFalando(true);
      setTimeout(() => setFalando(false), 3000);
    }
    setTimeout(() => inputRef.current?.focus(), 100);
  }, [estado?.palavraAtual?.id]);

  // Conta o tempo restante da rodada — recalcula a cada 500ms a partir do timestampInicio
  useEffect(() => {
    const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
    if (!ativo) {
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

  // Quando o tempo acaba, exibe a tela de ranking e inicia a contagem para a próxima palavra
  useEffect(() => {
    const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
    if (tempoRestante === 0 && ativo && estado?.palavraAtual != null && !rankingTriggeredRef.current) {
      rankingTriggeredRef.current = true;
      setShowRanking(true);
      setRankingTimer(RANKING_DURATION);
    }
  }, [tempoRestante]);

  // Conta regressiva do ranking (25s) — ao chegar a zero avança para a próxima palavra
  useEffect(() => {
    if (!showRanking) return;
    if (rankingTimer <= 0) {
      setShowRanking(false);
      onProxima();
      return;
    }
    const id = setTimeout(() => setRankingTimer(t => t - 1), 1000);
    return () => clearTimeout(id);
  }, [showRanking, rankingTimer]);

  const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
  const pct = estado && estado.tempoLimite > 0 ? (tempoRestante / estado.tempoLimite) * 100 : 0;
  const timerDanger = tempoRestante <= 5;

  /* ── LOBBY ───────────────────────────────────────────────── */
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

        <div className="sj-lobby-cols">
          <div className="sj-cfg-card">
            <h3 className="sj-cfg-title">Configurar atividade</h3>
            <div className="sj-cfg-field">
              <div className="sj-cfg-field-label">
                Tempo por palavra <strong>{cfg.tempoLimite}s</strong>
              </div>
              <input
                type="range"
                min={10}
                max={60}
                step={5}
                value={cfg.tempoLimite}
                onChange={e => setCfg(prev => ({ ...prev, tempoLimite: Number(e.target.value) }))}
                className="sj-range"
              />
              <div className="sj-range-labels">
                <span>10s</span>
                <span>60s</span>
              </div>
            </div>
            <div className="sj-cfg-diffs">
              {DIFICULDADES.map(({ key, label, cor }) => (
                <div className="sj-cfg-diff-row" key={key}>
                  <span className="sj-cfg-diff-dot" style={{ background: cor }} />
                  <span className="sj-cfg-diff-label">{label}</span>
                  <div className="sj-cfg-stepper">
                    <button type="button" className="sj-step-btn" onClick={() => adj(key, -1)}>
                      −
                    </button>
                    <span className="sj-step-val">{cfg[key]}</span>
                    <button type="button" className="sj-step-btn" onClick={() => adj(key, 1)}>
                      +
                    </button>
                  </div>
                </div>
              ))}
              <div className="sj-cfg-total">
                Total: <strong>{cfg.qtdFacil + cfg.qtdMedio + cfg.qtdDificil}</strong> palavras
              </div>
            </div>
          </div>

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
        </div>

        <button
          className="sj-iniciar-btn"
          disabled={!conectado || cfg.qtdFacil + cfg.qtdMedio + cfg.qtdDificil === 0}
          onClick={() => onIniciar({ ...cfg, palavrasExtrasIds: [] })}
        >
          {conectado ? '▶ Iniciar partida' : 'Conectando...'}
        </button>
      </div>
    );
  }

  /* ── ENCERRADA ───────────────────────────────────────────── */
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

  /* ── RANKING entre palavras ──────────────────────────────── */
  if (showRanking) {
    const rankingPct = (rankingTimer / RANKING_DURATION) * 100;
    return (
      <div className="sj-ranking-screen">
        <div className="sj-ranking-header">
          <div className="sj-lobby-badge">Ranking da rodada</div>
          <h2 className="sj-ranking-title">
            palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
          </h2>
        </div>

        <div className="sj-ranking-countdown">
          <span className="sj-ranking-next-label">Próxima palavra em</span>
          <span className="sj-ranking-next-val">{rankingTimer}s</span>
          <div className="sj-timer-bar-bg" style={{ marginTop: 10 }}>
            <div className="sj-timer-bar-fill" style={{ width: `${rankingPct}%`, background: '#6366f1', transition: 'width 1s linear' }} />
          </div>
        </div>

        <div className="sj-ranking-list">
          {estado.placar.length === 0 ? (
            <p className="sj-no-alunos">Nenhum participante no placar ainda.</p>
          ) : (
            estado.placar.map((p, i) => (
              <div key={p.login} className="sj-ranking-row">
                <span className="sj-ranking-rank">{i + 1}º</span>
                <span className="sj-ranking-avatar">{(p.nome || p.login).charAt(0).toUpperCase()}</span>
                <span className="sj-ranking-nome">{p.nome || p.login}</span>
                <span className={`sj-status-chip sj-status-${(p.statusAtual ?? 'aguardando').toLowerCase()}`}>
                  {p.statusAtual === 'ACERTOU' ? 'acertou' : p.statusAtual === 'ERROU' ? 'errou' : 'aguardando'}
                </span>
                <span className="sj-ranking-pts">{p.pontos} pts</span>
              </div>
            ))
          )}
        </div>
      </div>
    );
  }

  /* ── EM JOGO ─────────────────────────────────────────────── */
  return (
    <div className="sj-game-centered">
      <div className="sj-game-topbar">
        <div className="sj-sala-nome">{estado.nomeSala}</div>
        <div className="sj-topbar-right">
          <span className="sj-conectados">{estado.alunosConectados.length} aluno(s)</span>
          <span className="sj-codigo-pill">{codigoSala}</span>
        </div>
      </div>

      <div className="sj-game-card">
        <p className="sj-game-progress">
          palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
        </p>

        <div className="sj-audio-section">
          <button
            type="button"
            className={`sj-audio-btn${falando ? ' sj-audio-btn--playing' : ''}`}
            onClick={handleFalar}
            disabled={!ativo || !estado.palavraAtual}
            aria-label="Ouvir palavra"
          >
            <span className="sj-audio-icon">{falando ? '🔊' : '🔉'}</span>
            <span className="sj-audio-label-text">{falando ? 'Reproduzindo...' : 'Ouvir palavra'}</span>
          </button>
          <span className="sj-audio-hint">pode ouvir quantas vezes quiser</span>
        </div>

        <form className="sj-input-form" onSubmit={handleEnviar}>
          <input
            ref={inputRef}
            className="sj-word-input"
            type="text"
            value={resposta}
            onChange={e => setResposta(e.target.value)}
            placeholder="escreva a palavra ouvida..."
            autoComplete="off"
            spellCheck={false}
            disabled={jaRespondeu || !ativo}
          />
          <button type="submit" className="sj-send-btn" disabled={!resposta.trim() || jaRespondeu || !ativo}>
            {jaRespondeu ? 'Resposta enviada ✓' : 'Enviar resposta →'}
          </button>
        </form>

        <div className="sj-timer-section">
          <div className="sj-timer-row">
            <span className="sj-timer-label">Tempo restante</span>
            <span className={`sj-timer-val${timerDanger ? ' sj-timer-danger' : ''}`}>{tempoRestante}s</span>
          </div>
          <div className="sj-timer-bar-bg">
            <div className="sj-timer-bar-fill" style={{ width: `${pct}%`, background: timerDanger ? '#E24B4A' : '#1D9E75' }} />
          </div>
        </div>
      </div>
    </div>
  );
};

export default SalaJogoProfessor;
