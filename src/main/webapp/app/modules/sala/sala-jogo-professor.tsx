import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { EstadoJogo } from './hooks/useSalaWebSocket';
import { RODADA_RAPIDA_LIMITE, RelogioRodada } from './relogio-rodada';
import { falarPalavra } from './utils/falar-palavra';
import { RankingNuvem } from './ranking-nuvem';
import { VinhetaPodio } from './vinheta-podio';
import { IconeAudio } from 'app/shared/components/icone-audio/icone-audio';
import { CORES_DIFICULDADE, LABELS_DIFICULDADE } from 'app/shared/util/dificuldade-constants';

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
  // Palavras já sorteadas na tela de criação da sala - quando presentes, a 1ª
  // partida usa exatamente essas em vez de sortear na hora
  palavrasIds?: number[];
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
  // Login do próprio professor: usado para EXCLUÍ-LO das contagens ao vivo e do
  // ranking - ele comanda a partida, não compete com os alunos
  meuLogin?: string;
}

// Relatório da partida (visão do professor)
// Espelhos dos records RespostaDetalhe/RelatorioPalavra do JogoSalaService,
// servidos por GET /api/salas/{codigo}/relatorio (restrito ao dono da sala):
// cada palavra já jogada com as respostas digitadas e os totais de acerto.

// Uma resposta individual: quem respondeu, o texto exato digitado e o resultado
interface RespostaDetalhe {
  login: string;
  nome: string;
  texto: string;
  correta: boolean;
  ordem: number;
}

// Consolidado de uma palavra da partida (o % de acerto é calculado aqui no front)
interface RelatorioPalavra {
  indice: number;
  texto: string;
  dificuldade: string | null;
  totalRespostas: number;
  totalAcertos: number;
  respostas: RespostaDetalhe[];
}

// Cores/rótulos por dificuldade - paleta compartilhada de todas as telas
// (COR_/LABEL_ mantêm os nomes usados no JSX; a fonte é dificuldade-constants)
const COR_DIFICULDADE: Record<string, string> = CORES_DIFICULDADE;
const LABEL_DIFICULDADE: Record<string, string> = LABELS_DIFICULDADE;

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
  { key: 'qtdFacil', label: 'Fáceis', cor: CORES_DIFICULDADE.FACIL },
  { key: 'qtdMedio', label: 'Médias', cor: CORES_DIFICULDADE.MEDIO },
  { key: 'qtdDificil', label: 'Difíceis', cor: CORES_DIFICULDADE.DIFICIL },
];

// Tempo de rodada por dificuldade (sliders)
const TEMPOS: Array<{ key: 'tempoFacil' | 'tempoMedio' | 'tempoDificil'; label: string; cor: string }> = [
  { key: 'tempoFacil', label: 'Fácil', cor: CORES_DIFICULDADE.FACIL },
  { key: 'tempoMedio', label: 'Médio', cor: CORES_DIFICULDADE.MEDIO },
  { key: 'tempoDificil', label: 'Difícil', cor: CORES_DIFICULDADE.DIFICIL },
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
  initialGameConfig,
  meuLogin,
}) => {
  // Estado local do componente
  const [cfg, setCfg] = useState<Cfg>(initialGameConfig ?? DEFAULT_CFG);

  const [falando, setFalando] = useState(false);
  const [tempoRestante, setTempoRestante] = useState(0);
  const [copied, setCopied] = useState(false);

  const [showRanking, setShowRanking] = useState(false);
  const [rankingTimer, setRankingTimer] = useState(0);
  // Vinheta de suspense com o pódio, exibida uma única vez quando a partida encerra
  const [vinhetaFimConcluida, setVinhetaFimConcluida] = useState(false);

  // Relatório da partida vindo do servidor: uma entrada por palavra já jogada,
  // com as respostas digitadas. Alimenta o painel de palavras durante o jogo e
  // o relatório completo na tela de encerramento.
  const [relatorio, setRelatorio] = useState<RelatorioPalavra[]>([]);

  // Fechamento definitivo da sala ao fim da partida (botão "Encerrar e fechar"):
  // true enquanto o PATCH está em andamento - trava o botão contra clique duplo
  const [fechandoSala, setFechandoSala] = useState(false);
  const [erroFecharSala, setErroFecharSala] = useState<string | null>(null);
  const navigate = useNavigate();

  // Fecha a sala DE VEZ: marca ativo=false no banco via PATCH - o endpoint já é
  // restrito ao professor dono (ou admin). Sala inativa sai das listagens e não
  // recebe novas entradas. Com a sala fechada, o professor volta ao lobby;
  // o relatório da partida continua disponível até ele sair da tela.
  const fecharSala = async () => {
    if (fechandoSala) return;
    setFechandoSala(true);
    setErroFecharSala(null);
    try {
      await axios.patch(`/api/salas/${codigoSala}`, { codigo: codigoSala, ativo: false });
      navigate('/lobby');
    } catch {
      // Falhou (ex.: rede) - reabilita o botão e avisa; nada foi alterado no banco
      setFechandoSala(false);
      setErroFecharSala('Não foi possível fechar a sala. Tente novamente.');
    }
  };

  const rankingTriggeredRef = useRef(false);
  // Posições da rodada anterior no top 5 - usado pela animação de ultrapassagem
  const posRef = useRef<Map<string, number>>(new Map());

  // Busca o relatório no endpoint restrito ao dono da sala. Chamado a cada troca
  // de palavra e no encerramento - NÃO a cada resposta: o consolidado das rodadas
  // anteriores não muda no meio de uma rodada, e os números ao vivo da rodada em
  // curso são derivados do placar que o WebSocket já entrega de graça.
  const carregarRelatorio = useCallback(() => {
    axios
      .get<RelatorioPalavra[]>(`/api/salas/${codigoSala}/relatorio`)
      .then(res => setRelatorio(res.data))
      .catch(() => {
        // Sem relatório (ex.: servidor reiniciou no meio) - o painel segue com o que tem
      });
  }, [codigoSala]);

  useEffect(() => {
    if (!estado) return;
    // INICIADA/NOVA_PALAVRA: nova rodada entrou no relatório; ENCERRADA: consolida
    // a última rodada para a tela final
    if (estado.tipo === 'INICIADA' || estado.tipo === 'NOVA_PALAVRA' || estado.tipo === 'ENCERRADA') {
      carregarRelatorio();
    }
  }, [estado?.indiceAtual, estado?.tipo, carregarRelatorio]);

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

  // Fala a palavra automaticamente ao mudar de rodada (o professor dita para a
  // turma pela caixa de som da sala; cada aluno também tem o botão no aparelho)
  useEffect(() => {
    setFalando(false);
    setShowRanking(false);
    rankingTriggeredRef.current = false;
    if (estado?.palavraAtual?.texto) {
      // O módulo já aplica a pausa de 1s antes de falar; rate menor para o ditado
      setFalando(true);
      falarPalavra(estado.palavraAtual.texto, { rate: 0.5, onEnd: () => setFalando(false) });
    }
  }, [estado?.palavraAtual?.id]);

  // Conta o tempo restante da rodada - recalcula a cada 500ms a partir do timestampInicio
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

  // Conta regressiva do ranking (8s) - ao chegar a zero avança para a próxima palavra
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

  /* LOBBY */
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
                  <span className="sj-cfg-diff-dot" style={{ background: cor }} /> Tempo - {label} <strong>{cfg[key]}s</strong>
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
          // Preserva as palavras extras e as palavras pré-sorteadas na tela de criação.
          // Se o professor mudou as QUANTIDADES aqui no lobby, a lista pré-sorteada não
          // corresponde mais à configuração - descarta e deixa o servidor sortear na hora.
          onClick={() => {
            const qtdsIntactas =
              !!initialGameConfig &&
              cfg.qtdFacil === initialGameConfig.qtdFacil &&
              cfg.qtdMedio === initialGameConfig.qtdMedio &&
              cfg.qtdDificil === initialGameConfig.qtdDificil;
            onIniciar({
              ...cfg,
              palavrasExtrasIds: initialGameConfig?.palavrasExtrasIds ?? [],
              palavrasIds: qtdsIntactas ? (initialGameConfig?.palavrasIds ?? []) : [],
            });
          }}
        >
          {conectado ? '▶ Iniciar partida' : 'Conectando...'}
        </button>
      </div>
    );
  }

  // Placar sem o próprio professor: ele comanda a partida, não compete - não deve
  // aparecer no pódio nem no ranking que os alunos disputam
  const placarAlunos = estado.placar.filter(p => p.login !== meuLogin);

  /* ENCERRADA */
  if (estado.tipo === 'ENCERRADA') {
    // Antes do placar final, roda a vinheta de suspense revelando o pódio
    if (!vinhetaFimConcluida) {
      return <VinhetaPodio placar={placarAlunos} onFim={() => setVinhetaFimConcluida(true)} />;
    }
    return (
      <div className="sj-ended">
        <h2 className="sj-ended-title">Atividade encerrada!</h2>

        {/* Ranking completo da partida */}
        <h3 className="sj-rel-secao">Ranking da partida</h3>
        {placarAlunos.length === 0 ? (
          <p className="sj-no-alunos">Nenhum aluno participou desta partida.</p>
        ) : (
          <div className="sj-final-placar">
            {placarAlunos.map((p, i) => (
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
        )}

        {/* Relatório da partida: cada palavra com quem escreveu o quê
            Os dados vêm do endpoint restrito ao dono da sala (carregados no
            useEffect quando o estado vira ENCERRADA) */}
        <h3 className="sj-rel-secao">Relatório por palavra</h3>
        {relatorio.length === 0 ? (
          <p className="sj-no-alunos">Sem respostas registradas nesta partida.</p>
        ) : (
          <div className="sj-rel-lista">
            {relatorio.map(r => {
              const pctAcerto = r.totalRespostas > 0 ? Math.round((r.totalAcertos / r.totalRespostas) * 100) : 0;
              return (
                <div key={r.indice} className="sj-rel-card">
                  <div className="sj-rel-header">
                    <span className="sj-rel-num">{r.indice + 1}</span>
                    <span className="sj-rel-palavra">{r.texto}</span>
                    {r.dificuldade && (
                      <span className="sj-rel-dif" style={{ color: COR_DIFICULDADE[r.dificuldade] }}>
                        {LABEL_DIFICULDADE[r.dificuldade] ?? r.dificuldade}
                      </span>
                    )}
                    <span className="sj-rel-stats">
                      {r.totalRespostas} resposta{r.totalRespostas === 1 ? '' : 's'} · {pctAcerto}% de acerto
                    </span>
                  </div>
                  {r.respostas.length === 0 ? (
                    <p className="sj-rel-vazio">Ninguém respondeu esta palavra.</p>
                  ) : (
                    <ul className="sj-rel-respostas">
                      {/* Cada linha: quem respondeu e o texto LITERAL que digitou */}
                      {r.respostas.map(resp => (
                        <li key={resp.login} className={`sj-rel-resp${resp.correta ? ' sj-rel-resp--certa' : ' sj-rel-resp--errada'}`}>
                          <span className="sj-rel-resp-icone">{resp.correta ? '✓' : '✗'}</span>
                          <span className="sj-rel-resp-nome">{resp.nome || resp.login}</span>
                          <span className="sj-rel-resp-texto">&ldquo;{resp.texto}&rdquo;</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Encerrar e fechar a sala
            Fecha a sala em definitivo (ativo=false no banco): ela some das
            listagens e não aceita novas entradas. Ação exclusiva do professor,
            disponível só aqui - depois que a partida terminou. */}
        {erroFecharSala && <div className="sj-fechar-erro">{erroFecharSala}</div>}
        <button type="button" className="sj-fechar-sala-btn" onClick={() => void fecharSala()} disabled={fechandoSala}>
          {fechandoSala ? (
            'Fechando sala...'
          ) : (
            <>
              {/* Cadeado em SVG (nada de emoji - mesma decisão do ícone de áudio):
                  herda a cor do botão e escala nítido em qualquer tela */}
              <svg className="sj-fechar-icone" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <rect x="5" y="10.5" width="14" height="9" rx="2.2" fill="currentColor" />
                <path d="M8.5 10V8a3.5 3.5 0 0 1 7 0v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
              Encerrar e fechar sala
            </>
          )}
        </button>
      </div>
    );
  }

  /* RANKING entre palavras */
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

        {/* Palavra correta da rodada - visível para todos ao fim do tempo */}
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

        {placarAlunos.length === 0 ? (
          <p className="sj-no-alunos">Nenhum participante no placar ainda.</p>
        ) : (
          <RankingNuvem placar={placarAlunos} posRef={posRef} />
        )}
      </div>
    );
  }

  /* EM JOGO - painel do professor
     O professor não digita respostas: ele acompanha a rodada. O painel mostra
     a palavra atual (só ele vê o texto - os alunos recebem apenas o áudio nos
     seus aparelhos), os números ao vivo da rodada e a lista de palavras já
     jogadas com a taxa de acerto de cada uma. */

  // Números AO VIVO da rodada atual, derivados do placar que o WebSocket
  // broadcast a cada resposta (sem nenhuma requisição extra):
  // status ACERTOU/ERROU = já respondeu; AGUARDANDO = ainda não.
  const respondidas = placarAlunos.filter(p => p.statusAtual === 'ACERTOU' || p.statusAtual === 'ERROU').length;
  const acertosAoVivo = placarAlunos.filter(p => p.statusAtual === 'ACERTOU').length;
  const pctAoVivo = respondidas > 0 ? Math.round((acertosAoVivo / respondidas) * 100) : 0;
  const totalAlunos = estado.alunosConectados.filter(a => a.login !== meuLogin).length;

  return (
    <div className="sj-game-centered">
      {/* Vinheta de 3s no início de cada rodada: relógio voando, ponteiros rápidos se o tempo for curto */}
      <RelogioRodada tempoLimite={estado.tempoLimite} palavraId={estado.palavraAtual?.id} />
      <div className="sj-game-topbar">
        <div className="sj-sala-nome">{estado.nomeSala}</div>
        <div className="sj-topbar-right">
          <span className="sj-conectados">{totalAlunos} aluno(s)</span>
          <span className="sj-codigo-pill">{codigoSala}</span>
        </div>
      </div>

      <div className="sj-game-card sj-dash-card">
        <p className="sj-game-progress">
          palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
        </p>

        {/* Palavra atual em destaque - visível apenas nesta tela do professor */}
        {estado.palavraAtual && (
          <div className="sj-dash-atual">
            <span className="sj-dash-atual-label">Palavra atual</span>
            <div className="sj-dash-atual-row">
              <span className="sj-dash-atual-texto">{estado.palavraAtual.texto}</span>
              {estado.palavraAtual.dificuldade && (
                <span className="sj-dash-atual-dif" style={{ color: COR_DIFICULDADE[estado.palavraAtual.dificuldade] }}>
                  {LABEL_DIFICULDADE[estado.palavraAtual.dificuldade] ?? estado.palavraAtual.dificuldade}
                </span>
              )}
            </div>
          </div>
        )}

        {/* Botão de ditado: o professor repete o áudio para a turma quando quiser */}
        <div className="sj-audio-section">
          <button
            type="button"
            className={`sj-audio-btn${falando ? ' sj-audio-btn--playing' : ''}`}
            onClick={handleFalar}
            disabled={!ativo || !estado.palavraAtual}
            aria-label="Ouvir palavra"
          >
            <IconeAudio tocando={falando} className="sj-audio-svg" />
            <span className="sj-audio-label-text">{falando ? 'Reproduzindo...' : 'Repetir palavra'}</span>
          </button>
        </div>

        {/* Estatística ao vivo da rodada: alunos, quantos já responderam e % de acerto */}
        <div className="sj-dash-tiles">
          <div className="sj-dash-tile">
            <strong>{totalAlunos}</strong>
            <span>aluno{totalAlunos === 1 ? '' : 's'}</span>
          </div>
          <div className="sj-dash-tile">
            <strong>
              {respondidas}/{totalAlunos}
            </strong>
            <span>responderam</span>
          </div>
          <div className="sj-dash-tile">
            <strong>{pctAoVivo}%</strong>
            <span>de acerto</span>
          </div>
        </div>

        <div className="sj-timer-section">
          <div className="sj-timer-row">
            <span className="sj-timer-label">Tempo restante</span>
            <span className={`sj-timer-val${timerDanger ? ' sj-timer-danger' : ''}`}>{tempoRestante}s</span>
          </div>
          <div className={`sj-timer-bar-bg${estado.tempoLimite <= RODADA_RAPIDA_LIMITE ? ' sj-timer-bar--curto' : ''}`}>
            <div className="sj-timer-bar-fill" style={{ width: `${pct}%`, background: timerDanger ? '#E24B4A' : '#1D9E75' }} />
          </div>
        </div>

        {/* Palavras da partida: as já jogadas com % consolidado (do relatório) e a
            atual com os números ao vivo (do placar) - nunca antecipa as próximas */}
        {relatorio.length > 0 && (
          <div className="sj-dash-list">
            <span className="sj-dash-list-title">Palavras da partida</span>
            {relatorio.map(r => {
              const ehAtual = r.indice === estado.indiceAtual;
              const respostas = ehAtual ? respondidas : r.totalRespostas;
              const acertos = ehAtual ? acertosAoVivo : r.totalAcertos;
              const pctPalavra = respostas > 0 ? Math.round((acertos / respostas) * 100) : 0;
              return (
                <div key={r.indice} className={`sj-dash-row${ehAtual ? ' sj-dash-row--atual' : ''}`}>
                  <span className="sj-dash-num">{r.indice + 1}</span>
                  <div className="sj-dash-info">
                    <div className="sj-dash-word-line">
                      <span className="sj-dash-word">{r.texto}</span>
                      {r.dificuldade && (
                        <span className="sj-dash-dif" style={{ color: COR_DIFICULDADE[r.dificuldade] }}>
                          {LABEL_DIFICULDADE[r.dificuldade] ?? r.dificuldade}
                        </span>
                      )}
                      {ehAtual && <span className="sj-dash-agora">em andamento</span>}
                    </div>
                    {/* Barra de acerto: verde proporcional ao % de quem acertou */}
                    <div className="sj-dash-bar-bg">
                      <div className="sj-dash-bar" style={{ width: `${pctPalavra}%` }} />
                    </div>
                  </div>
                  <div className="sj-dash-nums">
                    <strong>{pctPalavra}%</strong>
                    <span>
                      {respostas} resposta{respostas === 1 ? '' : 's'}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default SalaJogoProfessor;
