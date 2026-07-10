import React, { useEffect, useRef, useState } from 'react';
import { EstadoJogo } from './hooks/useSalaWebSocket';
import { RODADA_RAPIDA_LIMITE, RelogioRodada } from './relogio-rodada';
import { falarPalavra } from './utils/falar-palavra';
import { RankingNuvem } from './ranking-nuvem';
import { VinhetaPodio } from './vinheta-podio';
import { EntradaPalavra } from 'app/shared/components/entrada-palavra/entrada-palavra';

// Configuração do jogo escolhida pelo professor: quantidade de palavras e
// TEMPO por dificuldade (fácil/médio/difícil)
interface GameConfig {
  tempoFacil: number;
  tempoMedio: number;
  tempoDificil: number;
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
  initialGameConfig?: GameConfig;
}

type Cfg = {
  tempoFacil: number;
  tempoMedio: number;
  tempoDificil: number;
  qtdFacil: number;
  qtdMedio: number;
  qtdDificil: number;
};

// Quantidade de palavras por dificuldade (steppers)
const DIFICULDADES: Array<{ key: 'qtdFacil' | 'qtdMedio' | 'qtdDificil'; label: string; cor: string }> = [
  { key: 'qtdFacil', label: 'Fáceis', cor: '#4ade80' },
  { key: 'qtdMedio', label: 'Médias', cor: '#fbbf24' },
  { key: 'qtdDificil', label: 'Difíceis', cor: '#f87171' },
];

// Tempo de rodada por dificuldade (sliders)
const TEMPOS: Array<{ key: 'tempoFacil' | 'tempoMedio' | 'tempoDificil'; label: string; cor: string }> = [
  { key: 'tempoFacil', label: 'Fácil', cor: '#4ade80' },
  { key: 'tempoMedio', label: 'Médio', cor: '#fbbf24' },
  { key: 'tempoDificil', label: 'Difícil', cor: '#f87171' },
];

const DEFAULT_CFG: Cfg = { tempoFacil: 20, tempoMedio: 30, tempoDificil: 45, qtdFacil: 5, qtdMedio: 5, qtdDificil: 5 };
const RANKING_DURATION = 8;

// Tela do professor durante a partida: lobby de espera com configurações, tela de jogo com timer,
// ranking entre palavras com contagem regressiva de 8s, e tela de encerramento com placar final
export const SalaJogoProfessor: React.FC<Props> = ({
  estado,
  codigoSala,
  conectado,
  onIniciar,
  onProxima,
  onResponder,
  initialGameConfig,
}) => {
  // ─── Estado local do componente ───────────────────────────────────────────
  const [cfg, setCfg] = useState<Cfg>(initialGameConfig ?? DEFAULT_CFG);

  const [resposta, setResposta] = useState('');
  const [jaRespondeu, setJaRespondeu] = useState(false);
  const [falando, setFalando] = useState(false);
  const [tempoRestante, setTempoRestante] = useState(0);
  const [copied, setCopied] = useState(false);

  const [showRanking, setShowRanking] = useState(false);
  const [rankingTimer, setRankingTimer] = useState(0);
  // Vinheta de suspense com o pódio, exibida uma única vez quando a partida encerra
  const [vinhetaFimConcluida, setVinhetaFimConcluida] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const rankingTriggeredRef = useRef(false);
  // Posições da rodada anterior no top 5 — usado pela animação de ultrapassagem
  const posRef = useRef<Map<string, number>>(new Map());

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
  // (a pausa de 1s e a escolha da melhor voz pt-BR ficam no módulo falar-palavra)
  const handleFalar = () => {
    if (!estado?.palavraAtual) return;
    setFalando(true);
    falarPalavra(estado.palavraAtual.texto, { onEnd: () => setFalando(false) });
  };

  // Envia a resposta do professor (ele também pode jogar junto com os alunos)
  const handleEnviar = (e: React.FormEvent) => {
    e.preventDefault();
    if (!resposta.trim() || jaRespondeu) return;
    onResponder(resposta.trim());
    setJaRespondeu(true);
  };

  // Reseta o estado de resposta e fala a palavra automaticamente ao mudar de palavra
  useEffect(() => {
    setResposta('');
    setJaRespondeu(false);
    setFalando(false);
    setShowRanking(false);
    rankingTriggeredRef.current = false;
    if (estado?.palavraAtual?.texto) {
      // O módulo já aplica a pausa de 1s antes de falar; rate menor para o ditado
      setFalando(true);
      falarPalavra(estado.palavraAtual.texto, { rate: 0.5, onEnd: () => setFalando(false) });
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

  // Conta regressiva do ranking (8s) — ao chegar a zero avança para a próxima palavra
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
            {/* Um tempo de rodada para cada dificuldade */}
            {TEMPOS.map(({ key, label, cor }) => (
              <div className="sj-cfg-field" key={key}>
                <div className="sj-cfg-field-label">
                  <span className="sj-cfg-diff-dot" style={{ background: cor }} /> Tempo — {label} <strong>{cfg[key]}s</strong>
                </div>
                <input
                  type="range"
                  min={10}
                  max={60}
                  step={5}
                  value={cfg[key]}
                  onChange={e => setCfg(prev => ({ ...prev, [key]: Number(e.target.value) }))}
                  className="sj-range"
                />
              </div>
            ))}
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
          disabled={
            !conectado || (cfg.qtdFacil + cfg.qtdMedio + cfg.qtdDificil === 0 && (initialGameConfig?.palavrasExtrasIds?.length ?? 0) === 0)
          }
          // Preserva as palavras extras escolhidas na tela de criação da sala
          onClick={() => onIniciar({ ...cfg, palavrasExtrasIds: initialGameConfig?.palavrasExtrasIds ?? [] })}
        >
          {conectado ? '▶ Iniciar partida' : 'Conectando...'}
        </button>
      </div>
    );
  }

  /* ── ENCERRADA ───────────────────────────────────────────── */
  if (estado.tipo === 'ENCERRADA') {
    // Antes do placar final, roda a vinheta de suspense revelando o pódio
    if (!vinhetaFimConcluida) {
      return <VinhetaPodio placar={estado.placar} onFim={() => setVinhetaFimConcluida(true)} />;
    }
    return (
      <div className="sj-ended">
        <h2 className="sj-ended-title">Atividade encerrada!</h2>
        <div className="sj-final-placar">
          {estado.placar.map((p, i) => (
            <div key={p.login} className="sj-final-row">
              <span className="sj-final-rank">{i + 1}º</span>
              <span className="sj-final-nome">
                {p.nome || p.login}
                {p.alertas > 0 && (
                  <span className="sj-alerta-burla" title={`${p.alertas} resposta(s) suspeita(s) de colar/corretor nesta partida`}>
                    ⚠ {p.alertas}
                  </span>
                )}
              </span>
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

        {/* Palavra correta da rodada — visível para todos ao fim do tempo */}
        {estado.palavraAtual && (
          <div className="sj-palavra-correta">
            <span className="sj-palavra-correta-label">Palavra correta</span>
            <span className="sj-palavra-correta-val">{estado.palavraAtual.texto}</span>
          </div>
        )}

        <div className="sj-ranking-countdown">
          <span className="sj-ranking-next-label">Próxima palavra em</span>
          <span className="sj-ranking-next-val">{rankingTimer}s</span>
          <div className="sj-timer-bar-bg" style={{ marginTop: 10 }}>
            <div className="sj-timer-bar-fill" style={{ width: `${rankingPct}%`, background: '#6366f1', transition: 'width 1s linear' }} />
          </div>
        </div>

        {estado.placar.length === 0 ? (
          <p className="sj-no-alunos">Nenhum participante no placar ainda.</p>
        ) : (
          <RankingNuvem placar={estado.placar} posRef={posRef} />
        )}
      </div>
    );
  }

  /* ── EM JOGO ─────────────────────────────────────────────── */
  return (
    <div className="sj-game-centered">
      {/* Vinheta de 3s no início de cada rodada: relógio voando, ponteiros rápidos se o tempo for curto */}
      <RelogioRodada tempoLimite={estado.tempoLimite} palavraId={estado.palavraAtual?.id} />
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
          <EntradaPalavra
            inputRef={inputRef}
            className="sj-word-input"
            value={resposta}
            onChange={setResposta}
            placeholder="escreva a palavra ouvida..."
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
          <div className={`sj-timer-bar-bg${estado.tempoLimite <= RODADA_RAPIDA_LIMITE ? ' sj-timer-bar--curto' : ''}`}>
            <div className="sj-timer-bar-fill" style={{ width: `${pct}%`, background: timerDanger ? '#E24B4A' : '#1D9E75' }} />
          </div>
        </div>
      </div>
    </div>
  );
};

export default SalaJogoProfessor;
