import React, { useCallback, useEffect, useRef, useState } from 'react';
import { EstadoJogo, FeedbackAluno, PlacarEntry } from './hooks/useSalaWebSocket';
import { MENSAGEM_ERRO, validarResposta } from './utils/validarResposta';

interface Props {
  estado: EstadoJogo | null;
  feedback: FeedbackAluno | null;
  meuLogin: string;
  onResponder: (resposta: string) => void;
  conectado: boolean;
}

function falarPalavra(texto: string) {
  if (!window.speechSynthesis) return;
  window.speechSynthesis.cancel();
  const utter = new SpeechSynthesisUtterance(texto);
  utter.lang = 'pt-BR';
  utter.rate = 0.85;
  window.speechSynthesis.speak(utter);
}

export const SalaJogoAluno: React.FC<Props> = ({ estado, feedback, meuLogin, onResponder, conectado }) => {
  const [resposta, setResposta] = useState('');
  const [falando, setFalando] = useState(false);
  const [jaRespondeu, setJaRespondeu] = useState(false);
  const [tempoRestante, setTempoRestante] = useState(0);
  const [validacaoLocal, setValidacaoLocal] = useState<ReturnType<typeof validarResposta> | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const palavraAtualId = useRef<number | null>(null);

  useEffect(() => {
    if (!estado) return;
    if (estado.tipo === 'NOVA_PALAVRA' || estado.tipo === 'INICIADA') {
      const novaId = estado.palavraAtual?.id ?? null;
      if (novaId !== palavraAtualId.current) {
        palavraAtualId.current = novaId;
        setResposta('');
        setJaRespondeu(false);
        setValidacaoLocal(null);
        setFalando(false);
        if (estado.palavraAtual) {
          setTimeout(() => falarPalavra(estado.palavraAtual.texto), 400);
          setFalando(true);
          setTimeout(() => setFalando(false), 3000);
        }
        inputRef.current?.focus();
      }
    }
  }, [estado?.palavraAtual?.id, estado?.tipo]);

  useEffect(() => {
    if (!estado || (estado.tipo !== 'NOVA_PALAVRA' && estado.tipo !== 'INICIADA')) {
      setTempoRestante(0);
      return;
    }
    const calcTempo = () => {
      const elapsed = Date.now() - estado.timestampInicio;
      const restante = Math.max(0, estado.tempoLimite - Math.floor(elapsed / 1000));
      setTempoRestante(restante);
    };
    calcTempo();
    const id = setInterval(calcTempo, 500);
    return () => clearInterval(id);
  }, [estado?.timestampInicio, estado?.tempoLimite, estado?.tipo]);

  const handleFalar = useCallback(() => {
    if (!estado?.palavraAtual) return;
    falarPalavra(estado.palavraAtual.texto);
    setFalando(true);
    setTimeout(() => setFalando(false), 2500);
  }, [estado?.palavraAtual]);

  const handleEnviar = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (!resposta.trim() || jaRespondeu) return;
      if (!estado?.palavraAtual) return;
      const v = validarResposta(resposta, estado.palavraAtual.texto);
      setValidacaoLocal(v);
      onResponder(resposta.trim());
      setJaRespondeu(true);
    },
    [resposta, jaRespondeu, estado?.palavraAtual, onResponder],
  );

  const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
  const meuPlacar = estado?.placar.find(p => p.login === meuLogin);
  const minhaPosicao = estado?.placar.findIndex(p => p.login === meuLogin) ?? -1;

  if (!estado || estado.tipo === 'AGUARDANDO') {
    return (
      <div className="sj-waiting">
        <div className="sj-waiting-icon">⌛</div>
        <h2>Aguardando o professor iniciar...</h2>
        <p className="sj-waiting-sub">{conectado ? `Conectado à sala ${estado?.nomeSala ?? ''}` : 'Conectando...'}</p>
        {estado && estado.alunosConectados.length > 0 && (
          <div className="sj-connected-list">
            <span className="sj-connected-label">{estado.alunosConectados.length} aluno(s) na sala</span>
          </div>
        )}
      </div>
    );
  }

  if (estado.tipo === 'ENCERRADA') {
    return (
      <div className="sj-ended">
        <h2>Atividade encerrada!</h2>
        <div className="sj-final-placar">
          {estado.placar.map((p, i) => (
            <div key={p.login} className={`sj-final-row${p.login === meuLogin ? ' sj-final-me' : ''}`}>
              <span className="sj-final-rank">{i + 1}º</span>
              <span className="sj-final-nome">
                {p.nome || p.login}
                {p.login === meuLogin ? ' (você)' : ''}
              </span>
              <span className="sj-final-pts">{p.pontos} pts</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  const pct = estado.tempoLimite > 0 ? (tempoRestante / estado.tempoLimite) * 100 : 0;
  const timerDanger = tempoRestante <= 5;

  return (
    <div className="sj-aluno-body">
      <div className="sj-aluno-topbar">
        <div>
          <div className="sj-sala-nome">{estado.nomeSala}</div>
          <div className="sj-palavra-prog">
            palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
          </div>
        </div>
        <div className="sj-score-block">
          <div className="sj-score-pts">{meuPlacar?.pontos ?? 0} pts</div>
          {minhaPosicao >= 0 && <div className="sj-score-pos">{minhaPosicao + 1}º lugar</div>}
        </div>
      </div>

      <div className="sj-aluno-main">
        <div className={`sj-timer-num${timerDanger ? ' sj-timer-danger' : ''}`}>{tempoRestante}</div>
        <div className="sj-timer-label">segundos para responder</div>
        <div className="sj-timer-bar-bg">
          <div className="sj-timer-bar-fill" style={{ width: `${pct}%`, background: timerDanger ? '#E24B4A' : '#378ADD' }} />
        </div>

        <div className="sj-audio-section">
          <p className="sj-audio-label">Ouça a palavra e escreva abaixo</p>
          <button
            type="button"
            className={`sj-audio-btn${falando ? ' sj-audio-btn--playing' : ''}`}
            onClick={handleFalar}
            disabled={!ativo || !estado.palavraAtual}
            aria-label="Ouvir palavra"
          >
            <span className="sj-audio-icon">{falando ? '🔊' : '🔉'}</span>
          </button>
          <span className="sj-audio-hint">Clique para ouvir · pode ouvir quantas vezes quiser</span>
        </div>

        <form className="sj-input-form" onSubmit={handleEnviar}>
          <label className="sj-input-label" htmlFor="sj-resposta">
            Digite a palavra que você ouviu:
          </label>
          <input
            id="sj-resposta"
            ref={inputRef}
            className={`sj-word-input${validacaoLocal && !validacaoLocal.correta ? ' sj-input-error' : ''}${validacaoLocal?.correta ? ' sj-input-ok' : ''}`}
            type="text"
            value={resposta}
            onChange={e => setResposta(e.target.value)}
            placeholder="escreva aqui..."
            autoComplete="off"
            spellCheck={false}
            disabled={jaRespondeu || !ativo}
          />
          <button type="submit" className="sj-send-btn" disabled={!resposta.trim() || jaRespondeu || !ativo}>
            {jaRespondeu ? 'Resposta enviada ✓' : 'Enviar resposta →'}
          </button>
        </form>

        {feedback && (
          <div className={`sj-feedback${feedback.correta ? ' sj-feedback--ok' : ' sj-feedback--err'}`}>
            <span className="sj-feedback-icon">{feedback.correta ? '✓' : '✗'}</span>
            <div className="sj-feedback-body">
              {feedback.correta ? (
                <>
                  <strong>{feedback.ordem === 1 ? '1º a acertar!' : `${feedback.ordem}º a acertar`}</strong> · palavra correta
                </>
              ) : (
                <>
                  <strong>Errou</strong> ·{' '}
                  {feedback.tipoErro ? MENSAGEM_ERRO[feedback.tipoErro as keyof typeof MENSAGEM_ERRO] : 'Resposta incorreta'}
                </>
              )}
            </div>
            {feedback.correta && <span className="sj-feedback-pts">+{feedback.pontos} pts</span>}
          </div>
        )}

        {validacaoLocal && !validacaoLocal.correta && jaRespondeu && !feedback && (
          <div className="sj-feedback sj-feedback--warn">
            <span className="sj-feedback-icon">⚠</span>
            <div className="sj-feedback-body">
              {MENSAGEM_ERRO[validacaoLocal.tipoErro]}
              {' · '}similaridade: {Math.round(validacaoLocal.similaridade * 100)}%
            </div>
          </div>
        )}

        <div className="sj-mini-rank">
          <div className="sj-mini-rank-title">🏆 Placar ao vivo</div>
          {estado.placar.slice(0, 5).map((p, i) => (
            <div key={p.login} className={`sj-mini-row${p.login === meuLogin ? ' sj-mini-me' : ''}`}>
              <span className="sj-mini-n">{i + 1}</span>
              <span className="sj-mini-nome">
                {p.nome || p.login}
                {p.login === meuLogin ? ' (você)' : ''}
              </span>
              <span className={`sj-mini-status sj-status-${p.statusAtual?.toLowerCase() ?? 'aguardando'}`}>
                {p.statusAtual === 'ACERTOU' ? '✓' : p.statusAtual === 'ERROU' ? '✗' : '…'}
              </span>
              <span className="sj-mini-pts">{p.pontos}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default SalaJogoAluno;
