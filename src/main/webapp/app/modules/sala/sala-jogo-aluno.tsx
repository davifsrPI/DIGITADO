import React, { useCallback, useEffect, useRef, useState } from 'react';
import { EstadoJogo, FeedbackAluno } from './hooks/useSalaWebSocket';
import { MENSAGEM_ERRO, validarResposta } from './utils/validarResposta';
import { RODADA_RAPIDA_LIMITE, RelogioRodada } from './relogio-rodada';
import { falarPalavra } from './utils/falar-palavra';
import { RankingNuvem } from './ranking-nuvem';
import { VinhetaPodio } from './vinheta-podio';
import { AmpulhetaAnimada } from './ampulheta-animada';
import { EntradaPalavra } from 'app/shared/components/entrada-palavra/entrada-palavra';
import { IconeAudio } from 'app/shared/components/icone-audio/icone-audio';

// Configuração enviada ao iniciar a partida — mesmo shape usado pela tela do professor
interface GameConfig {
  tempoFacil: number;
  tempoMedio: number;
  tempoDificil: number;
  qtdFacil: number;
  qtdMedio: number;
  qtdDificil: number;
  palavrasExtrasIds: number[];
  palavrasIds?: number[];
}

// Config padrão do duelo quando a da tela de criação se perdeu (reload da página)
const DUELO_CFG_PADRAO: GameConfig = {
  tempoFacil: 20,
  tempoMedio: 30,
  tempoDificil: 45,
  qtdFacil: 5,
  qtdMedio: 5,
  qtdDificil: 5,
  palavrasExtrasIds: [],
};

// Tempo da tela de ranking entre palavras antes do avanço automático (só no duelo)
const RANKING_DURACAO = 8;

interface Props {
  estado: EstadoJogo | null;
  feedback: FeedbackAluno | null;
  meuLogin: string;
  onResponder: (resposta: string, tentativasBurla?: number) => void;
  conectado: boolean;
  // ─── Modo CRIADOR do duelo 1v1 ───
  // APENAS no 1v1 quem criou a sala joga junto: além de digitar como qualquer
  // jogador, ele inicia o duelo e seu cliente avança as rodadas automaticamente
  // (papéis que na sala de turma pertencem ao professor).
  criadorDuelo?: boolean;
  codigoSala?: string;
  onIniciar?: (cfg: GameConfig) => void;
  onProxima?: () => void;
  initialGameConfig?: GameConfig;
}

// Tela do aluno durante a partida: aguarda o professor iniciar, recebe a palavra via áudio,
// digita a resposta e vê o feedback individual e o placar ao vivo dos colegas
export const SalaJogoAluno: React.FC<Props> = ({
  estado,
  feedback,
  meuLogin,
  onResponder,
  conectado,
  criadorDuelo,
  codigoSala,
  onIniciar,
  onProxima,
  initialGameConfig,
}) => {
  const [resposta, setResposta] = useState('');
  const [falando, setFalando] = useState(false);
  const [jaRespondeu, setJaRespondeu] = useState(false);
  const [tempoRestante, setTempoRestante] = useState(0);
  const [validacaoLocal, setValidacaoLocal] = useState<ReturnType<typeof validarResposta> | null>(null);
  // Ranking exibido quando o tempo da rodada acaba (mesma tela que o professor vê)
  const [showRanking, setShowRanking] = useState(false);
  // Contagem regressiva do ranking — usada SÓ pelo criador do duelo, cujo cliente
  // avança para a próxima palavra (nas salas de turma quem avança é o professor)
  const [rankingTimer, setRankingTimer] = useState(0);
  // "✓ Copiado!" temporário ao clicar no código do duelo (tela de espera do criador)
  const [copied, setCopied] = useState(false);
  // Pontuação/posição "congeladas" no início da rodada — só atualizam quando o tempo acaba,
  // para o aluno não descobrir o resultado dos colegas pelo placar enquanto digita
  const [scoreCongelado, setScoreCongelado] = useState<{ pontos: number; posicao: number }>({ pontos: 0, posicao: -1 });
  // Vinheta de suspense com o pódio, exibida uma única vez quando a partida encerra
  const [vinhetaFimConcluida, setVinhetaFimConcluida] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  // Tentativas de burla (colar, corretor) bloqueadas pelo EntradaPalavra na rodada atual
  const burlasRef = useRef(0);
  const palavraAtualId = useRef<number | null>(null);
  const rankingTriggeredRef = useRef(false);
  // Posições da rodada anterior no top 5 — usado pela animação de ultrapassagem
  const posRef = useRef<Map<string, number>>(new Map());

  // Detecta mudança de palavra e reseta o estado de resposta — fala a palavra automaticamente
  useEffect(() => {
    if (!estado) return;
    if (estado.tipo === 'NOVA_PALAVRA' || estado.tipo === 'INICIADA') {
      const novaId = estado.palavraAtual?.id ?? null;
      if (novaId !== palavraAtualId.current) {
        palavraAtualId.current = novaId;
        setResposta('');
        setJaRespondeu(false);
        burlasRef.current = 0;
        setValidacaoLocal(null);
        setFalando(false);
        setShowRanking(false);
        rankingTriggeredRef.current = false;
        // Congela a pontuação/posição atuais para exibir durante toda a rodada
        const idx = estado.placar.findIndex(p => p.login === meuLogin);
        setScoreCongelado({ pontos: idx >= 0 ? estado.placar[idx].pontos : 0, posicao: idx });
        if (estado.palavraAtual) {
          // O módulo já aplica a pausa de 1s antes de falar
          setFalando(true);
          falarPalavra(estado.palavraAtual.texto, { onEnd: () => setFalando(false) });
        }
        inputRef.current?.focus();
      }
    }
  }, [estado?.palavraAtual?.id, estado?.tipo]);

  // Conta o tempo restante recalculando a cada 500ms a partir do timestampInicio do servidor
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

  // Reproduz a palavra ao clicar no botão de áudio e atualiza o ícone enquanto fala
  const handleFalar = useCallback(() => {
    if (!estado?.palavraAtual) return;
    setFalando(true);
    falarPalavra(estado.palavraAtual.texto, { onEnd: () => setFalando(false) });
  }, [estado?.palavraAtual]);

  // Envia a resposta: faz validação local para feedback imediato antes de receber o do servidor
  const handleEnviar = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (!resposta.trim() || jaRespondeu) return;
      if (!estado?.palavraAtual) return;
      const v = validarResposta(resposta, estado.palavraAtual.texto);
      setValidacaoLocal(v);
      onResponder(resposta.trim(), burlasRef.current);
      setJaRespondeu(true);
    },
    [resposta, jaRespondeu, estado?.palavraAtual, onResponder],
  );

  const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';

  // Quando o tempo acaba, exibe a tela de ranking (igual à do professor) até chegar a próxima palavra.
  // Confere contra o timestamp do servidor: no início da rodada tempoRestante ainda é 0 (valor
  // inicial do estado, antes de o timer calcular), e sem essa checagem o ranking apareceria na hora.
  useEffect(() => {
    if (tempoRestante === 0 && ativo && estado?.palavraAtual != null && !rankingTriggeredRef.current) {
      const tempoEsgotado = Date.now() - estado.timestampInicio >= estado.tempoLimite * 1000;
      if (tempoEsgotado) {
        rankingTriggeredRef.current = true;
        setShowRanking(true);
        if (criadorDuelo) setRankingTimer(RANKING_DURACAO);
      }
    }
  }, [tempoRestante, ativo, estado]);

  // Avanço automático do duelo: o cliente do CRIADOR conta os segundos do ranking
  // e pede a próxima palavra ao servidor — o oponente só recebe o broadcast
  useEffect(() => {
    if (!criadorDuelo || !showRanking) return;
    if (rankingTimer <= 0) {
      onProxima?.();
      return;
    }
    const id = setTimeout(() => setRankingTimer(t => t - 1), 1000);
    return () => clearTimeout(id);
  }, [criadorDuelo, showRanking, rankingTimer]);

  if (!estado || estado.tipo === 'AGUARDANDO') {
    // Tela de espera do CRIADOR do duelo 1v1: ele compartilha o código, vê o
    // oponente chegar e inicia o duelo — jogando junto, como qualquer jogador
    if (criadorDuelo) {
      const jogadores = estado?.alunosConectados?.length ?? 0;
      const temOponente = jogadores >= 2;
      const copiarCodigo = () => {
        if (!codigoSala) return;
        navigator.clipboard.writeText(codigoSala).then(() => {
          setCopied(true);
          setTimeout(() => setCopied(false), 2000);
        });
      };
      return (
        <div className="sj-waiting">
          <div className="sj-waiting-icon">
            <AmpulhetaAnimada />
          </div>
          <h2>{temOponente ? 'Oponente na sala!' : 'Aguardando um oponente...'}</h2>
          <p className="sj-waiting-sub">{conectado ? `${jogadores}/2 jogadores conectados` : 'Conectando...'}</p>
          {codigoSala && (
            <button type="button" className="sj-codigo-block" onClick={copiarCodigo} title="Clique para copiar">
              <span className="sj-codigo-label">CÓDIGO DO DUELO</span>
              <span className="sj-codigo-val">{codigoSala}</span>
              <span className="sj-codigo-copy">{copied ? '✓ Copiado!' : 'clique para copiar'}</span>
            </button>
          )}
          <button
            type="button"
            className="sj-iniciar-btn"
            disabled={!conectado || !temOponente}
            onClick={() => onIniciar?.(initialGameConfig ?? DUELO_CFG_PADRAO)}
          >
            {!conectado ? 'Conectando...' : temOponente ? '⚔ Iniciar duelo' : 'Aguardando oponente para iniciar...'}
          </button>
          <p className="sj-waiting-sub">Você também joga: ao iniciar, ouça as palavras e digite mais rápido que o oponente!</p>
        </div>
      );
    }
    return (
      <div className="sj-waiting">
        <div className="sj-waiting-icon">
          <AmpulhetaAnimada />
        </div>
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
    // Antes do placar final, roda a vinheta de suspense revelando o pódio
    if (!vinhetaFimConcluida) {
      return <VinhetaPodio placar={estado.placar} meuLogin={meuLogin} onFim={() => setVinhetaFimConcluida(true)} />;
    }
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

  /* ── RANKING entre palavras (tempo esgotado) ─────────────── */
  if (showRanking && ativo) {
    return (
      <div className="sj-ranking-screen">
        <div className="sj-ranking-header">
          <div className="sj-lobby-badge">Ranking da rodada</div>
          <h2 className="sj-ranking-title">
            palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
          </h2>
        </div>

        {estado.palavraAtual && (
          <div className="sj-palavra-correta">
            <span className="sj-palavra-correta-label">Palavra correta</span>
            <span className="sj-palavra-correta-val">{estado.palavraAtual.texto}</span>
            {/* Só o aluno vê a própria similaridade */}
            {jaRespondeu && validacaoLocal ? (
              validacaoLocal.correta ? (
                <span className="sj-similaridade sj-similaridade--ok">✓ Você acertou!</span>
              ) : (
                <span className="sj-similaridade sj-similaridade--err">
                  ✗ Você errou · similaridade: {Math.round(validacaoLocal.similaridade * 100)}%
                </span>
              )
            ) : (
              <span className="sj-similaridade sj-similaridade--warn">Você não respondeu a tempo</span>
            )}
          </div>
        )}

        {/* Só o criador do duelo vê a contagem — é o cliente dele que avança a rodada */}
        {criadorDuelo && (
          <div className="sj-ranking-countdown">
            <span className="sj-ranking-next-label">Próxima palavra em</span>
            <span className="sj-ranking-next-val">{rankingTimer}s</span>
          </div>
        )}

        <RankingNuvem placar={estado.placar} meuLogin={meuLogin} posRef={posRef} />
      </div>
    );
  }

  const pct = estado.tempoLimite > 0 ? (tempoRestante / estado.tempoLimite) * 100 : 0;
  const timerDanger = tempoRestante <= 5;
  const rodadaRapida = estado.tempoLimite <= RODADA_RAPIDA_LIMITE;

  return (
    <div className="sj-aluno-body">
      {/* Vinheta de 3s no início de cada rodada: relógio voando, ponteiros rápidos se o tempo for curto */}
      <RelogioRodada tempoLimite={estado.tempoLimite} palavraId={estado.palavraAtual?.id} />
      <div className="sj-aluno-topbar">
        <div>
          <div className="sj-sala-nome">{estado.nomeSala}</div>
          <div className="sj-palavra-prog">
            palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
          </div>
        </div>
        {/* Pontuação congelada no início da rodada — só atualiza quando o tempo acaba */}
        <div className="sj-score-block">
          <div className="sj-score-pts">{scoreCongelado.pontos} pts</div>
          {scoreCongelado.posicao >= 0 && <div className="sj-score-pos">{scoreCongelado.posicao + 1}º lugar</div>}
        </div>
      </div>

      <div className="sj-aluno-main">
        <div className={`sj-timer-num${timerDanger ? ' sj-timer-danger' : ''}`}>{tempoRestante}</div>
        <div className="sj-timer-label">segundos para responder</div>
        <div className={`sj-timer-bar-bg${rodadaRapida ? ' sj-timer-bar--curto' : ''}`}>
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
            <IconeAudio tocando={falando} className="sj-audio-svg" />
          </button>
          <span className="sj-audio-hint">Clique para ouvir · pode ouvir quantas vezes quiser</span>
        </div>

        <form className="sj-input-form" onSubmit={handleEnviar}>
          <label className="sj-input-label" htmlFor="sj-resposta">
            Digite a palavra que você ouviu:
          </label>
          <EntradaPalavra
            id="sj-resposta"
            inputRef={inputRef}
            className={`sj-word-input${validacaoLocal && !validacaoLocal.correta ? ' sj-input-error' : ''}${validacaoLocal?.correta ? ' sj-input-ok' : ''}`}
            value={resposta}
            onChange={setResposta}
            onBurla={() => {
              burlasRef.current += 1;
            }}
            placeholder="escreva aqui..."
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
                  {validacaoLocal && <> · similaridade: {Math.round(validacaoLocal.similaridade * 100)}%</>}
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
      </div>
    </div>
  );
};

export default SalaJogoAluno;
