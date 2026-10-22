import 'app/modules/sala/sala-jogo-styles.scss';
import './individual.scss';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

import { useBodyClass } from 'app/shared/util/use-body-class';
import { EntradaPalavra } from 'app/shared/components/entrada-palavra/entrada-palavra';
import { IconeAudio } from 'app/shared/components/icone-audio/icone-audio';
import { ouvirPalavraDaRodada, pararAudioDaPalavra } from 'app/modules/sala/utils/ouvir-palavra';
import { compararLetras } from 'app/modules/sala/utils/validarResposta';
import { TIPOS_ERRO, rotuloTipoErro } from 'app/shared/util/tipos-erro';

// Espelhos dos records de IndividualVM (servidor)

interface PalavraIndividual {
  id: number;
  // Nulo enquanto a rodada está aberta: o som vem de GET /api/individual/audio
  texto: string | null;
  dificuldade: string | null;
  tempoLimite: number;
  timestampInicio: number;
  timestampServidor: number;
}

interface EstadoIndividual {
  rodada: number;
  vidas: number;
  vidasMaximas: number;
  acertos: number;
  sequencia: number;
  melhorSequencia: number;
  xpGanho: number;
  recorde: number;
  respondida: boolean;
  palavra: PalavraIndividual | null;
}

interface RespostaIndividual {
  rodada: number;
  texto: string;
  respostaDigitada: string | null;
  correta: boolean;
  tipoErro: string | null;
  similaridade: number;
  xp: number;
}

interface ResumoIndividual {
  encerramento: 'SEM_VIDAS' | 'DESISTIU' | 'ABANDONOU';
  palavras: number;
  acertos: number;
  erros: number;
  naoRespondidas: number;
  melhorSequencia: number;
  xpGanho: number;
  recordeAnterior: number;
  novoRecorde: boolean;
  respostas: RespostaIndividual[];
}

interface ResultadoIndividual {
  correta: boolean;
  foraDoTempo: boolean;
  textoCorreto: string;
  tipoErro: string | null;
  similaridade: number;
  xp: number;
  estado: EstadoIndividual;
  resumo: ResumoIndividual | null;
}

interface PartidaAnterior {
  data: string;
  palavras: number;
  acertos: number;
  melhorSequencia: number;
  xpGanho: number;
  encerramento: string;
}

interface Recordes {
  recorde: number;
  dataRecorde: string | null;
  emAndamento: boolean;
  ultimas: PartidaAnterior[];
}

const URL_AUDIO = '/api/individual/audio';

// "1 acerto", "2 acertos"
const acertos = (n: number) => `${n} acerto${n === 1 ? '' : 's'}`;

const formatarData = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' }) : '';

const ROTULO_ENCERRAMENTO: Record<string, string> = {
  SEM_VIDAS: 'sem vidas',
  DESISTIU: 'encerrada por você',
  ABANDONOU: 'abandonada',
};

// As vidas, como corações cheios e vazios
const Vidas: React.FC<{ vidas: number; maximo: number }> = ({ vidas, maximo }) => (
  <div className="ind-vidas" aria-label={`${vidas} de ${maximo} vidas`}>
    {Array.from({ length: maximo }).map((_, i) => (
      <span key={i} className={`ind-vida${i < vidas ? '' : ' ind-vida--perdida'}`} aria-hidden="true">
        {i < vidas ? '❤️' : '🤍'}
      </span>
    ))}
  </div>
);

/** Tela de entrada: as regras, o recorde e as últimas partidas. */
const IntroIndividual: React.FC<{
  recordes: Recordes | null;
  carregando: boolean;
  erro: string | null;
  onComecar: () => void;
  onContinuar: () => void;
}> = ({ recordes, carregando, erro, onComecar, onContinuar }) => (
  <div className="ind-intro">
    <div className="sj-lobby-badge">Modo individual</div>
    <h1 className="ind-titulo">🎯 Até onde você chega?</h1>
    <p className="ind-sub">
      Só você e o ditado. As palavras ficam mais difíceis a cada acerto - e as que você já errou voltam para revisão.
    </p>

    <ul className="ind-regras">
      <li>
        <strong>3 vidas.</strong> Cada erro ou tempo esgotado tira uma.
      </li>
      <li>
        <strong>Palavras sob medida.</strong> A próxima é escolhida pelo seu histórico: tentativas, acertos, tempo gasto e o quanto você
        chegou perto da palavra certa.
      </li>
      <li>
        <strong>XP a cada acerto:</strong> de 5 a 15 (palavra mais difícil para você vale mais) e +20 a cada 10 acertos.
      </li>
    </ul>

    {recordes && recordes.recorde > 0 && (
      <div className="ind-recorde">
        🏆 Seu recorde: <strong>{recordes.recorde}</strong> acerto{recordes.recorde === 1 ? '' : 's'}
        {recordes.dataRecorde ? ` · ${formatarData(recordes.dataRecorde)}` : ''}
      </div>
    )}

    {erro && <div className="ind-erro">⚠ {erro}</div>}

    <div className="ind-acoes">
      {recordes?.emAndamento && (
        <button type="button" className="sj-iniciar-btn ind-btn-secundario" onClick={onContinuar} disabled={carregando}>
          ↩ Continuar a partida
        </button>
      )}
      <button type="button" className="sj-iniciar-btn" onClick={onComecar} disabled={carregando}>
        {carregando ? 'Preparando...' : recordes?.emAndamento ? '▶ Começar outra partida' : '▶ Começar'}
      </button>
    </div>

    {recordes && recordes.ultimas.length > 0 && (
      <div className="ind-ultimas">
        <h3>Últimas partidas</h3>
        <ul>
          {recordes.ultimas.map((p, i) => (
            <li key={`${p.data}-${i}`}>
              <span>{formatarData(p.data)}</span>
              <span>
                <strong>{p.acertos}</strong> de {p.palavras} · melhor sequência {p.melhorSequencia}
              </span>
              <span className="ind-ultimas-xp">+{p.xpGanho} XP</span>
              <span className="ind-ultimas-fim">{ROTULO_ENCERRAMENTO[p.encerramento] ?? p.encerramento}</span>
            </li>
          ))}
        </ul>
      </div>
    )}
  </div>
);

/** A correção logo depois de responder: a palavra certa, o que errou e o XP. */
const CorrecaoIndividual: React.FC<{ resultado: ResultadoIndividual; resposta: string; onProxima: () => void; ocupado: boolean }> = ({
  resultado,
  resposta,
  onProxima,
  ocupado,
}) => {
  const botaoRef = useRef<HTMLButtonElement>(null);
  // Foco no botão: Enter já leva para a próxima palavra, sem tirar a mão do teclado
  useEffect(() => botaoRef.current?.focus(), []);
  const naoRespondeu = resultado.tipoErro === 'NAO_RESPONDEU';
  const tipo = resultado.tipoErro ? TIPOS_ERRO[resultado.tipoErro] : null;
  return (
    <div className="sj-ranking-screen ind-correcao">
      <div className="sj-palavra-correta">
        <span className="sj-palavra-correta-label">Palavra correta</span>
        <span className="sj-palavra-correta-val">{resultado.textoCorreto}</span>
        {resultado.correta ? (
          <span className="sj-similaridade sj-similaridade--ok">✓ Você acertou!{resultado.xp > 0 ? ` +${resultado.xp} XP` : ''}</span>
        ) : naoRespondeu ? (
          <span className="sj-similaridade sj-similaridade--warn">
            ⏱ {resultado.foraDoTempo ? 'Sua resposta chegou depois do tempo' : 'O tempo acabou sem resposta'} · perdeu uma vida
          </span>
        ) : (
          <>
            <span className="sj-similaridade sj-similaridade--err">
              ✗ Você errou · acertou {resultado.similaridade}% da palavra · perdeu uma vida
            </span>
            <span className="sj-diff">
              <span className="sj-diff-label">Você escreveu:</span>{' '}
              {compararLetras(resposta, resultado.textoCorreto).digitado.map((l, idx) => (
                <span key={idx} className={l.ok ? 'sj-diff-ok' : 'sj-diff-err'}>
                  {l.ch}
                </span>
              ))}
            </span>
          </>
        )}
        {!resultado.correta && tipo && (
          <span className="ind-tipo-erro" style={{ borderColor: tipo.cor }}>
            <strong style={{ color: tipo.cor }}>{tipo.rotulo}:</strong> {tipo.explicacao}
          </span>
        )}
      </div>
      <button ref={botaoRef} type="button" className="sj-iniciar-btn" onClick={onProxima} disabled={ocupado}>
        {resultado.resumo ? 'Ver resultado →' : 'Próxima palavra →'}
      </button>
    </div>
  );
};

/** O fim: até onde chegou, o recorde e palavra por palavra. */
const FimIndividual: React.FC<{ resumo: ResumoIndividual; onDeNovo: () => void; onLobby: () => void }> = ({
  resumo,
  onDeNovo,
  onLobby,
}) => (
  <div className="ind-fim">
    <h2 className="ind-titulo">{resumo.encerramento === 'SEM_VIDAS' ? 'Fim das vidas!' : 'Partida encerrada'}</h2>
    {resumo.novoRecorde ? (
      <div className="ind-recorde ind-recorde--novo">
        🏆 Novo recorde: {acertos(resumo.acertos)}!{resumo.recordeAnterior > 0 ? ` (antes: ${resumo.recordeAnterior})` : ''}
      </div>
    ) : (
      resumo.recordeAnterior > 0 && <div className="ind-recorde">Seu recorde continua {acertos(resumo.recordeAnterior)}.</div>
    )}
    <div className="ind-kpis">
      <div>
        <strong>{resumo.palavras}</strong>
        <span>palavras</span>
      </div>
      <div>
        <strong className="ind-ok">{resumo.acertos}</strong>
        <span>acertos</span>
      </div>
      <div>
        <strong className="ind-err">{resumo.erros}</strong>
        <span>erros</span>
      </div>
      <div>
        <strong>{resumo.naoRespondidas}</strong>
        <span>sem resposta</span>
      </div>
      <div>
        <strong>{resumo.melhorSequencia}</strong>
        <span>melhor sequência</span>
      </div>
      <div>
        <strong className="ind-xp">+{resumo.xpGanho}</strong>
        <span>XP</span>
      </div>
    </div>
    {resumo.respostas.length > 0 && (
      <ol className="ind-lista">
        {resumo.respostas.map(r => (
          <li key={r.rodada} className={r.correta ? 'ind-lista-ok' : 'ind-lista-err'}>
            <span className="ind-lista-palavra">{r.texto}</span>
            <span className="ind-lista-detalhe">
              {r.correta
                ? `✓ acertou${r.xp > 0 ? ` · +${r.xp} XP` : ''}`
                : r.tipoErro === 'NAO_RESPONDEU'
                  ? '⏱ não respondeu'
                  : `✗ "${r.respostaDigitada ?? ''}" · ${rotuloTipoErro(r.tipoErro)} · ${r.similaridade}%`}
            </span>
          </li>
        ))}
      </ol>
    )}
    <div className="ind-acoes">
      <button type="button" className="sj-iniciar-btn" onClick={onDeNovo}>
        ▶ Jogar de novo
      </button>
      <button type="button" className="sj-iniciar-btn ind-btn-secundario" onClick={onLobby}>
        Voltar ao lobby
      </button>
    </div>
  </div>
);

// Mensagem de erro do servidor para um pedido fora de ordem (ver PartidaIndividualResource)
const mensagemDeErro = (err: any, padrao: string): string =>
  err?.response?.data?.message === 'error.rodadainvalida' ? 'A partida mudou em outra aba. Recarregue a página.' : padrao;

/**
 * Modo individual: o jogador sozinho, até onde conseguir chegar.
 *
 * O servidor manda em tudo: escolhe a palavra (pela equação do histórico do
 * jogador), conta o tempo, confere a resposta, tira as vidas e credita o XP. A tela
 * só desenha e manda o que foi digitado - como nas salas, o texto da palavra nunca
 * chega antes da resposta; o som vem de /api/individual/audio.
 */
export const Individual: React.FC = () => {
  const navigate = useNavigate();
  useBodyClass('sala-jogo-page');

  const [fase, setFase] = useState<'intro' | 'jogando' | 'correcao' | 'fim'>('intro');
  const [recordes, setRecordes] = useState<Recordes | null>(null);
  const [estado, setEstado] = useState<EstadoIndividual | null>(null);
  const [resultado, setResultado] = useState<ResultadoIndividual | null>(null);
  const [resumo, setResumo] = useState<ResumoIndividual | null>(null);
  const [resposta, setResposta] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [falando, setFalando] = useState(false);
  const [audioBloqueado, setAudioBloqueado] = useState(false);
  const [bloqueioCorretor, setBloqueioCorretor] = useState(false);
  const [restante, setRestante] = useState(0);
  const burlasRef = useRef(0);
  const inputRef = useRef<HTMLInputElement>(null);
  // Diferença entre o relógio daqui e o do servidor: o tempo da rodada é dele
  const desvioRelogio = useRef(0);
  const enviandoRef = useRef(false);

  const carregarRecordes = useCallback(() => {
    axios
      .get<Recordes>('/api/individual/recordes')
      .then(res => setRecordes(res.data))
      .catch(() => setRecordes(null));
  }, []);

  useEffect(() => {
    carregarRecordes();
    return pararAudioDaPalavra;
  }, [carregarRecordes]);

  // Uma palavra nova: zera o campo, acerta o relógio com o servidor e fala a palavra
  const entrarNaRodada = useCallback((novo: EstadoIndividual) => {
    setEstado(novo);
    setResposta('');
    setResultado(null);
    setBloqueioCorretor(false);
    setAudioBloqueado(false);
    burlasRef.current = 0;
    enviandoRef.current = false;
    if (!novo.palavra) return;
    desvioRelogio.current = Date.now() - novo.palavra.timestampServidor;
    setFase('jogando');
    setFalando(true);
    ouvirPalavraDaRodada({
      urlAudio: URL_AUDIO,
      chave: novo.palavra.id,
      texto: novo.palavra.texto,
      onEnd: () => setFalando(false),
      onBloqueadoPeloNavegador: () => setAudioBloqueado(true),
    });
    setTimeout(() => inputRef.current?.focus(), 50);
  }, []);

  const pedir = async <T,>(requisicao: () => Promise<{ data: T }>, padrao: string): Promise<T | null> => {
    setOcupado(true);
    setErro(null);
    try {
      return (await requisicao()).data;
    } catch (err) {
      setErro(mensagemDeErro(err, padrao));
      return null;
    } finally {
      setOcupado(false);
    }
  };

  const comecar = async () => {
    const novo = await pedir(() => axios.post<EstadoIndividual>('/api/individual/iniciar'), 'Não foi possível começar a partida agora.');
    if (novo) {
      setResumo(null);
      entrarNaRodada(novo);
    }
  };

  // Volta para a partida que ficou aberta (recarregou a página, fechou a aba)
  const continuar = async () => {
    const atual = await pedir(() => axios.get<EstadoIndividual>('/api/individual/estado'), 'A partida anterior já acabou.');
    if (!atual) return;
    if (atual.respondida) {
      // A palavra da vez já foi respondida: segue direto para a próxima
      const proximo = await pedir(() => axios.post<EstadoIndividual>('/api/individual/proxima'), 'Não foi possível continuar.');
      if (proximo) entrarNaRodada(proximo);
    } else {
      entrarNaRodada(atual);
    }
  };

  // Manda a resposta (vazia = tempo esgotado). Uma vez só por palavra.
  const enviar = useCallback(
    async (texto: string) => {
      if (enviandoRef.current) return;
      enviandoRef.current = true;
      pararAudioDaPalavra();
      const res = await pedir(
        () =>
          axios.post<ResultadoIndividual>('/api/individual/responder', {
            resposta: texto,
            tentativasBurla: burlasRef.current,
          }),
        'Sua resposta não chegou ao servidor. Verifique a conexão.',
      );
      if (!res) {
        enviandoRef.current = false;
        return;
      }
      setResultado(res);
      setEstado(res.estado);
      if (res.resumo) setResumo(res.resumo);
      setFase('correcao');
    },
    // pedir só usa setters estáveis: a função pode ficar a mesma a partida inteira
    [],
  );

  const proxima = async () => {
    if (resumo) {
      setFase('fim');
      carregarRecordes();
      return;
    }
    const novo = await pedir(() => axios.post<EstadoIndividual>('/api/individual/proxima'), 'Não foi possível buscar a próxima palavra.');
    if (!novo) return;
    if (!novo.palavra) {
      // O acervo inteiro já foi ditado nesta partida: encerra com o que foi feito
      await encerrar();
      return;
    }
    entrarNaRodada(novo);
  };

  const encerrar = async () => {
    pararAudioDaPalavra();
    const fim = await pedir(() => axios.post<ResumoIndividual>('/api/individual/encerrar'), 'Não foi possível encerrar a partida.');
    if (fim) {
      setResumo(fim);
      setFase('fim');
      carregarRecordes();
    }
  };

  // Relógio da rodada, a partir do início que o SERVIDOR marcou. Zerou sem resposta:
  // manda a resposta vazia, e o servidor conta como não respondida.
  useEffect(() => {
    if (fase !== 'jogando' || !estado?.palavra) return;
    const { timestampInicio, tempoLimite } = estado.palavra;
    const calcular = () => {
      const decorrido = Date.now() - desvioRelogio.current - timestampInicio;
      const faltam = Math.max(0, tempoLimite - Math.floor(decorrido / 1000));
      setRestante(faltam);
      if (faltam === 0) void enviar('');
    };
    calcular();
    const id = setInterval(calcular, 500);
    return () => clearInterval(id);
  }, [fase, estado?.palavra, enviar]);

  const ouvirDeNovo = () => {
    if (!estado?.palavra) return;
    setFalando(true);
    setAudioBloqueado(false);
    ouvirPalavraDaRodada({
      urlAudio: URL_AUDIO,
      chave: estado.palavra.id,
      texto: estado.palavra.texto,
      pausaMs: 0,
      onEnd: () => setFalando(false),
      onBloqueadoPeloNavegador: () => setAudioBloqueado(true),
    });
  };

  const palavra = estado?.palavra;
  const pct = palavra && palavra.tempoLimite > 0 ? (restante / palavra.tempoLimite) * 100 : 0;
  const perigo = restante <= 5;

  return (
    <div className="sj-wrapper">
      <div className="sj-bg" />
      <div className="sj-container ind-container">
        <button className="sj-back-btn" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        {fase === 'intro' && (
          <IntroIndividual recordes={recordes} carregando={ocupado} erro={erro} onComecar={comecar} onContinuar={continuar} />
        )}

        {(fase === 'jogando' || fase === 'correcao') && estado && (
          <div className="ind-topo">
            <Vidas vidas={estado.vidas} maximo={estado.vidasMaximas} />
            <div className="ind-placar">
              <span>
                Palavra <strong>{estado.rodada}</strong>
              </span>
              <span>
                Acertos <strong>{estado.acertos}</strong>
              </span>
              <span>
                Sequência <strong>{estado.sequencia}</strong>
              </span>
              <span className="ind-xp">⚡ {estado.xpGanho} XP</span>
            </div>
            {fase === 'jogando' && (
              <button type="button" className="ind-encerrar" onClick={() => void encerrar()} disabled={ocupado}>
                Encerrar
              </button>
            )}
          </div>
        )}

        {fase === 'jogando' && palavra && (
          <div className="sj-aluno-main">
            <div className={`sj-timer-num${perigo ? ' sj-timer-danger' : ''}`}>{restante}</div>
            <div className="sj-timer-label">segundos para responder</div>
            <div className="sj-timer-bar-bg">
              <div className="sj-timer-bar-fill" style={{ width: `${pct}%`, background: perigo ? '#E24B4A' : '#378ADD' }} />
            </div>

            <div className="sj-audio-section">
              <p className="sj-audio-label">Ouça a palavra e escreva abaixo</p>
              <button
                type="button"
                className={`sj-audio-btn${falando ? ' sj-audio-btn--playing' : ''}`}
                onClick={ouvirDeNovo}
                aria-label="Ouvir palavra"
              >
                <IconeAudio tocando={falando} className="sj-audio-svg" />
              </button>
              <span className="sj-audio-hint">Clique para ouvir · pode ouvir quantas vezes quiser</span>
            </div>

            <form
              className="sj-input-form"
              onSubmit={e => {
                e.preventDefault();
                if (resposta.trim()) void enviar(resposta.trim());
              }}
            >
              <label className="sj-input-label" htmlFor="ind-resposta">
                Digite a palavra que você ouviu:
              </label>
              <EntradaPalavra
                id="ind-resposta"
                inputRef={inputRef}
                className="sj-word-input"
                value={resposta}
                onChange={setResposta}
                onBurla={() => {
                  burlasRef.current += 1;
                  setBloqueioCorretor(true);
                }}
                placeholder="escreva aqui..."
                disabled={ocupado}
              />
              <button type="submit" className="sj-send-btn" disabled={!resposta.trim() || ocupado}>
                Enviar resposta →
              </button>
              {bloqueioCorretor && (
                <div className="sj-feedback sj-feedback--warn">
                  <span className="sj-feedback-icon">⌨</span>
                  <div className="sj-feedback-body">
                    <strong>Escreva você mesmo</strong> · corretor, sugestão do teclado, copiar e colar não valem aqui
                  </div>
                </div>
              )}
              {audioBloqueado && (
                <div className="sj-feedback sj-feedback--warn">
                  <span className="sj-feedback-icon">🔊</span>
                  <div className="sj-feedback-body">
                    <strong>Toque no botão de ouvir</strong> · o navegador não toca o áudio sozinho na primeira vez
                  </div>
                </div>
              )}
              {erro && <div className="ind-erro">⚠ {erro}</div>}
            </form>
          </div>
        )}

        {fase === 'correcao' && resultado && (
          <>
            <CorrecaoIndividual resultado={resultado} resposta={resposta} onProxima={() => void proxima()} ocupado={ocupado} />
            {erro && <div className="ind-erro">⚠ {erro}</div>}
          </>
        )}

        {fase === 'fim' && resumo && (
          <FimIndividual
            resumo={resumo}
            onDeNovo={() => {
              setFase('intro');
              void comecar();
            }}
            onLobby={() => navigate('/lobby')}
          />
        )}
      </div>
    </div>
  );
};

export default Individual;
