import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { EstadoJogo } from './hooks/useSalaWebSocket';
import { RODADA_RAPIDA_LIMITE, RelogioRodada } from './relogio-rodada';
import { falarPalavra } from './utils/falar-palavra';
import { RankingNuvem } from './ranking-nuvem';
import { VinhetaPodio } from './vinheta-podio';
import { IconeAudio } from 'app/shared/components/icone-audio/icone-audio';
import { PosicaoRanking, RankingPartida, RelatorioPalavra, RelatorioPorPalavra } from './relatorio-partida';
import { MetricasPorAluno } from './metricas-aluno';
import { useNomesParticipantes } from './hooks/useNomesParticipantes';
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
  // Palavras já sorteadas na tela de criação da sala, quando presentes, a 1ª
  // partida usa exatamente essas em vez de sortear na hora
  palavrasIds?: number[];
}

interface Props {
  estado: EstadoJogo | null;
  codigoSala: string;
  conectado: boolean;
  onIniciar: (cfg: GameConfig) => void;
  // indiceAtual: a rodada que a tela está mostrando, para o servidor não virar
  // duas vezes a mesma rodada (o relógio dele também vira)
  onProxima: (indiceAtual?: number) => void;
  onPausar: () => void;
  onEncerrar: () => void;
  onResponder: (resposta: string) => void;
  initialGameConfig?: GameConfig;
  // Login do próprio professor: usado para EXCLUÍ-LO das contagens ao vivo e do
  // ranking, ele comanda a partida, não compete com os alunos
  meuLogin?: string;
  // Pede ao servidor o placar atualizado só para este aparelho (ver pedirEstado)
  onPedirEstado?: () => void;
}

// Relatório da partida (visão do professor): espelho dos records
// RespostaDetalhe/RelatorioPalavra do JogoSalaService, servidos por
// GET /api/salas/{codigo}/relatorio (restrito ao dono da sala), cada palavra já
// jogada com as respostas digitadas e os totais de acerto. O tipo e os blocos
// que o renderizam vivem em relatorio-partida, compartilhados com a tela
// "Ver estatísticas".

// Cores/rótulos por dificuldade, paleta compartilhada de todas as telas
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

// Faixas usadas para agrupar a LISTA DE PALAVRAS da sala. A chave aqui é a
// dificuldade que vem do servidor em cada palavra, não o campo da configuração
const DIFICULDADES_PALAVRAS: Array<{ key: string; label: string; cor: string }> = [
  { key: 'FACIL', label: 'Fáceis', cor: CORES_DIFICULDADE.FACIL },
  { key: 'MEDIO', label: 'Médias', cor: CORES_DIFICULDADE.MEDIO },
  { key: 'DIFICIL', label: 'Difíceis', cor: CORES_DIFICULDADE.DIFICIL },
];

// Tempo de rodada por dificuldade (sliders)
const TEMPOS: Array<{ key: 'tempoFacil' | 'tempoMedio' | 'tempoDificil'; label: string; cor: string }> = [
  { key: 'tempoFacil', label: 'Fácil', cor: CORES_DIFICULDADE.FACIL },
  { key: 'tempoMedio', label: 'Médio', cor: CORES_DIFICULDADE.MEDIO },
  { key: 'tempoDificil', label: 'Difícil', cor: CORES_DIFICULDADE.DIFICIL },
];

// As quantidades escolhidas na tela ainda são as mesmas da configuração gravada
// na sala? É o que decide se a lista de palavras já sorteada continua valendo
const mesmasQuantidades = (cfg: Cfg, salva?: GameConfig): boolean =>
  !!salva && cfg.qtdFacil === salva.qtdFacil && cfg.qtdMedio === salva.qtdMedio && cfg.qtdDificil === salva.qtdDificil;

const DEFAULT_CFG: Cfg = { tempoFacil: 20, tempoMedio: 30, tempoDificil: 45, qtdFacil: 5, qtdMedio: 5, qtdDificil: 5 };
// Fallback do tempo de ranking para o primeiro render, antes de o servidor mandar o
// valor dele (estado.tempoRanking) - quem define de verdade é o servidor
const RANKING_DURATION = 8;

// Palavras guardadas na sala, de GET /api/salas/{codigo}/palavras (restrito ao
// dono): as sorteadas na criação e as que o professor escolheu a mão no acervo
interface PalavraDaSala {
  id: number;
  texto: string;
  dificuldade: string | null;
}

interface PalavrasDaSala {
  sorteadas: PalavraDaSala[];
  extras: PalavraDaSala[];
}

// Snapshot da ÚLTIMA partida da sala, de GET /api/salas/{codigo}/estatisticas
// (mesmo formato da tela "Ver estatísticas")
interface UltimaPartida {
  dataEncerramento: string;
  totalPalavras: number;
  ranking: PosicaoRanking[];
  relatorio: RelatorioPalavra[];
}

// Data/hora do encerramento no formato brasileiro (ex: 24/08/2026 às 14:32)
const formatarDataPartida = (iso: string): string => {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return `${d.toLocaleDateString('pt-BR')} às ${d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`;
};

/**
 * Ranking entre uma palavra e a próxima, na visão de quem comanda a sala.
 *
 * A contagem mostrada vem do SERVIDOR: é ele quem vira a rodada. Antes esta tela
 * contava os 8 segundos e pedia a próxima palavra, então a aba do professor fechando
 * deixava a turma parada aqui para sempre.
 *
 * Componente separado para a tela principal não passar do limite de complexidade
 * que o projeto aceita.
 */
const TelaRankingProfessor: React.FC<{
  estado: EstadoJogo;
  palavraTexto: string | null;
  segundosParaProxima: number;
  placarAlunos: EstadoJogo['placar'];
  posRef: React.MutableRefObject<Map<string, number>>;
  // Nome verdadeiro e turma de cada aluno: o placar chega com o nome público
  nomes: Record<string, string>;
  turmas: Record<string, string>;
}> = ({ estado, palavraTexto, segundosParaProxima, placarAlunos, posRef, nomes, turmas }) => {
  // RANKING_DURATION só cobre o primeiro render, antes da primeira mensagem
  const rankingPct = (segundosParaProxima / (estado.tempoRanking || RANKING_DURATION)) * 100;
  return (
    <div className="sj-ranking-screen">
      <div className="sj-ranking-header">
        <div className="sj-lobby-badge">Ranking da rodada</div>
        <h2 className="sj-ranking-title">
          palavra {estado.indiceAtual + 1} de {estado.totalPalavras}
        </h2>
      </div>

      {/* Palavra correta da rodada, visível para todos ao fim do tempo */}
      {estado.palavraAtual && (
        <div className="sj-palavra-correta">
          <span className="sj-palavra-correta-label">Palavra correta</span>
          <span className="sj-palavra-correta-val">{palavraTexto ?? '···'}</span>
        </div>
      )}

      <div className="sj-ranking-countdown">
        <span className="sj-ranking-next-label">Próxima palavra em</span>
        <span className="sj-ranking-next-val">{segundosParaProxima}s</span>
        <div className="sj-timer-bar-bg" style={{ marginTop: 10 }}>
          <div className="sj-timer-bar-fill" style={{ width: `${rankingPct}%`, background: '#6366f1', transition: 'width 1s linear' }} />
        </div>
      </div>

      {placarAlunos.length === 0 ? (
        <p className="sj-no-alunos">Nenhum participante no placar ainda.</p>
      ) : (
        <RankingNuvem placar={placarAlunos} posRef={posRef} nomes={nomes} turmas={turmas} />
      )}
    </div>
  );
};

/**
 * Desempenho da ÚLTIMA partida da sala, aberto dentro do lobby.
 *
 * Componente separado para a tela principal não passar do limite de
 * complexidade que o projeto aceita (mesmo motivo do TelaRankingProfessor).
 */
const BlocoUltimaPartida: React.FC<{
  ultima: UltimaPartida | null;
  codigoSala: string;
  // Nome verdadeiro e turma de cada aluno - o snapshot guarda o nome público
  nomes: Record<string, string>;
  turmas: Record<string, string>;
}> = ({ ultima, codigoSala, nomes, turmas }) => {
  const [aberto, setAberto] = useState(false);
  if (!ultima) {
    return null;
  }
  return (
    <div className="sj-ultima-partida">
      <div className="sj-ultima-topo">
        <div className="sj-ultima-info">
          <span className="sj-ultima-titulo">Última partida desta sala</span>
          <span className="sj-ultima-sub">
            {ultima.totalPalavras} palavra{ultima.totalPalavras === 1 ? '' : 's'} · {ultima.ranking.length} participante
            {ultima.ranking.length === 1 ? '' : 's'} · {formatarDataPartida(ultima.dataEncerramento)}
          </span>
        </div>
        <button type="button" className="sj-ultima-btn" onClick={() => setAberto(v => !v)}>
          {aberto ? 'Ocultar' : '📊 Ver desempenho'}
        </button>
      </div>
      {aberto && (
        <div className="sj-ultima-corpo">
          <h3 className="sj-rel-secao">Ranking da partida</h3>
          <RankingPartida posicoes={ultima.ranking} nomes={nomes} turmas={turmas} />
          {/* O resumo de cada aluno, o mesmo que ele viu ao fim da partida */}
          <h3 className="sj-rel-secao">Resumo de cada aluno</h3>
          <MetricasPorAluno codigoSala={codigoSala} posicoes={ultima.ranking} nomes={nomes} turmas={turmas} />
          <h3 className="sj-rel-secao">Relatório por palavra</h3>
          <RelatorioPorPalavra relatorio={ultima.relatorio} nomes={nomes} />
        </div>
      )}
    </div>
  );
};

/**
 * Quem já está na sala esperando a partida começar.
 *
 * O professor vê o nome VERDADEIRO e a série de cada um (o apelido, quando o
 * aluno escolheu jogar com um, vem entre parênteses no próprio nome): o placar
 * que a turma enxerga usa o nome público, e quem comanda precisa saber quem é
 * quem. Ver useNomesParticipantes.
 */
const PainelAlunosConectados: React.FC<{
  alunos: EstadoJogo['alunosConectados'];
  nomes: Record<string, string>;
  turmas: Record<string, string>;
}> = ({ alunos, nomes, turmas }) => (
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
            <span className="sj-aluno-avatar">{(nomes[a.login] || a.nome || a.login).charAt(0).toUpperCase()}</span>
            <span className="sj-aluno-nome">{nomes[a.login] || a.nome || a.login}</span>
            {turmas[a.login] && <span className="sj-aluno-turma">{turmas[a.login]}</span>}
            <span className="sj-aluno-dot" />
          </li>
        ))}
      </ul>
    )}
  </div>
);

/**
 * Painel das palavras guardadas na sala, no lobby de quem comanda.
 *
 * A sala pode ter sido preparada dias antes ("criar e deixar pronta") e a
 * configuração guarda só os ids: sem esta lista, a única forma de conferir a
 * atividade era começar a partida. Nasce FECHADO de propósito - a tela do
 * professor costuma estar no projetor, e são as respostas do ditado.
 *
 * Componente separado para a tela principal não passar do limite de
 * complexidade que o projeto aceita (mesmo motivo do TelaRankingProfessor).
 */
const PainelPalavrasDaSala: React.FC<{
  palavras: PalavrasDaSala | null;
  // O professor mexeu nas quantidades aqui no lobby: a lista sorteada não
  // corresponde mais ao que ele pediu e a partida vai sortear de novo
  qtdsAlteradas: boolean;
  // A sala já jogou uma vez: a próxima partida evita repetir as palavras dela
  jaTevePartida: boolean;
}> = ({ palavras, qtdsAlteradas, jaTevePartida }) => {
  const [aberto, setAberto] = useState(false);
  if (!palavras || palavras.sorteadas.length + palavras.extras.length === 0) {
    return null;
  }
  const { sorteadas, extras } = palavras;
  // Palavra cuja faixa não é nenhuma das três conhecidas: entra num grupo à
  // parte em vez de sumir - aqui o professor tem que ver a atividade INTEIRA
  const semFaixa = sorteadas.filter(p => !DIFICULDADES_PALAVRAS.some(d => d.key === p.dificuldade));
  const chips = (lista: PalavraDaSala[], cor?: string, extra?: boolean) => (
    <div className="sj-palavras-lista">
      {lista.map(p => (
        <span
          className={`sj-palavra-chip${extra ? ' sj-palavra-chip--extra' : ''}`}
          key={p.id}
          style={cor ? { borderColor: cor } : undefined}
        >
          {p.texto}
        </span>
      ))}
    </div>
  );
  return (
    <div className="sj-palavras">
      <div className="sj-palavras-topo">
        <div className="sj-palavras-info">
          <span className="sj-palavras-titulo">Palavras desta sala</span>
          <span className="sj-palavras-sub">
            {sorteadas.length} sorteada{sorteadas.length === 1 ? '' : 's'}
            {extras.length > 0 && ` · ${extras.length} escolhida${extras.length === 1 ? '' : 's'} por você`}
          </span>
        </div>
        <button type="button" className="sj-ultima-btn" onClick={() => setAberto(v => !v)}>
          {aberto ? 'Ocultar' : '👁 Ver palavras'}
        </button>
      </div>
      {aberto && (
        <div className="sj-palavras-corpo">
          {/* A tela do professor costuma estar no projetor da sala */}
          <p className="sj-palavras-aviso">⚠ São as respostas do ditado — cuidado se a tela estiver projetada para a turma.</p>
          {qtdsAlteradas && (
            <p className="sj-palavras-aviso sj-palavras-aviso--mudou">
              As quantidades foram alteradas nesta tela: ao iniciar, a partida vai sortear palavras novas no lugar das sorteadas abaixo.
            </p>
          )}
          {jaTevePartida && (
            <p className="sj-palavras-aviso sj-palavras-aviso--mudou">
              Esta sala já teve uma partida: para não repetir as palavras da anterior, a próxima pode sortear outras.
            </p>
          )}
          {DIFICULDADES_PALAVRAS.map(({ key, label, cor }) => {
            const doGrupo = sorteadas.filter(p => p.dificuldade === key);
            if (doGrupo.length === 0) return null;
            return (
              <div className="sj-palavras-grupo" key={key}>
                <span className="sj-palavras-grupo-titulo">
                  <span className="sj-cfg-diff-dot" style={{ background: cor }} /> {label} · {doGrupo.length}
                </span>
                {chips(doGrupo, cor)}
              </div>
            );
          })}
          {semFaixa.length > 0 && (
            <div className="sj-palavras-grupo">
              <span className="sj-palavras-grupo-titulo">Outras · {semFaixa.length}</span>
              {chips(semFaixa)}
            </div>
          )}
          {extras.length > 0 && (
            <div className="sj-palavras-grupo">
              <span className="sj-palavras-grupo-titulo">✍ Escolhidas por você · {extras.length}</span>
              {chips(extras, undefined, true)}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// Tela do professor durante a partida: lobby de espera com configurações, tela de jogo com timer,
// ranking entre palavras com contagem regressiva de 8s, e tela de encerramento com placar final
export const SalaJogoProfessor: React.FC<Props> = ({
  estado,
  codigoSala,
  conectado,
  onIniciar,
  onProxima,
  onPedirEstado,
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

  // Desempenho da ÚLTIMA partida desta sala, lido do snapshot gravado no banco.
  // É o que impede a sala reaberta de parecer uma sala nova: o jogo em si vive
  // só na memória do servidor e é descartado quando a sala fecha, mas o lobby
  // continua mostrando (e abrindo) o que a turma fez na partida anterior.
  const [ultimaPartida, setUltimaPartida] = useState<UltimaPartida | null>(null);

  /**
   * Palavras guardadas na sala: as sorteadas na criação e as escolhidas a mão.
   *
   * A sala pode ter sido preparada dias antes ("criar e deixar pronta"), e a
   * configuração guarda só os ids - sem esta lista, a única forma de conferir a
   * atividade era começar a partida. Fica FECHADA por padrão: a tela do professor
   * costuma estar projetada para a turma, e são as respostas do ditado.
   */
  const [palavrasSala, setPalavrasSala] = useState<PalavrasDaSala | null>(null);

  const navigate = useNavigate();

  /**
   * Nome VERDADEIRO e turma de cada aluno (login → texto).
   *
   * O placar chega com o nome público: quem escolheu jogar de apelido aparece
   * para a turma só pelo apelido. Este mapa - que só o dono da sala consegue
   * buscar - devolve o nome real (com o apelido entre parênteses) para as telas
   * do professor, que é quem precisa saber quem é quem.
   */
  // Quem está na tela agora: o hook rebusca a lista quando aparece um login que
  // ainda não tem nome - é o que acontece com o aluno que entra depois de a sala
  // abrir, o caso normal numa aula
  const loginsNaTela = [...(estado?.placar ?? []).map(p => p.login), ...(estado?.alunosConectados ?? []).map(a => a.login)].filter(
    login => login !== meuLogin,
  );
  const { nomes: nomesReais, turmas } = useNomesParticipantes(codigoSala, true, loginsNaTela);

  /**
   * Texto da palavra da rodada.
   *
   * Ele vinha no estado do jogo, mas o estado é transmitido para o tópico da sala -
   * que os ALUNOS também assinam - e com isso a resposta chegava a eles antes de
   * responderem. O texto saiu de lá e passou a vir por um endpoint restrito ao dono
   * da sala, que é quem precisa dele para ditar a palavra à turma.
   *
   * Continua funcionando quando o servidor não tem sintetizador de voz: nesse caso o
   * texto volta a vir no estado, e o que estiver preenchido vale.
   */
  const [palavraTexto, setPalavraTexto] = useState<string | null>(null);
  useEffect(() => {
    const idDaPalavra = estado?.palavraAtual?.id;
    if (!idDaPalavra) {
      setPalavraTexto(null);
      return;
    }
    if (estado?.palavraAtual?.texto) {
      setPalavraTexto(estado.palavraAtual.texto);
      return;
    }
    let cancelado = false;
    axios
      .get<{ texto: string }>(`/api/salas/${codigoSala}/palavra-atual`)
      .then(res => {
        if (!cancelado) setPalavraTexto(res.data.texto);
      })
      .catch(() => {
        if (!cancelado) setPalavraTexto(null);
      });
    return () => {
      cancelado = true;
    };
  }, [codigoSala, estado?.palavraAtual?.id, estado?.palavraAtual?.texto]);

  const rankingTriggeredRef = useRef(false);
  // Posições da rodada anterior no top 5, usado pela animação de ultrapassagem
  const posRef = useRef<Map<string, number>>(new Map());

  // Busca o relatório no endpoint restrito ao dono da sala. Chamado a cada troca
  // de palavra e no encerramento, NÃO a cada resposta: o consolidado das rodadas
  // anteriores não muda no meio de uma rodada, e os números ao vivo da rodada em
  // curso são derivados do placar que o WebSocket já entrega de graça.
  const carregarRelatorio = useCallback(() => {
    axios
      .get<RelatorioPalavra[]>(`/api/salas/${codigoSala}/relatorio`)
      .then(res => setRelatorio(res.data))
      .catch(() => {
        // Sem relatório (ex.: servidor reiniciou no meio), o painel segue com o que tem
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

  // Carrega o snapshot da partida anterior ao abrir a sala. Só no lobby: é o
  // único lugar onde o bloco aparece, e assim não há requisição a cada rodada.
  // 404 (sala sem partida encerrada) simplesmente não mostra nada.
  useEffect(() => {
    if (estado && estado.tipo !== 'AGUARDANDO') return;
    axios
      .get<UltimaPartida>(`/api/salas/${codigoSala}/estatisticas`)
      .then(res => setUltimaPartida(res.data))
      .catch(() => setUltimaPartida(null));
  }, [codigoSala, estado?.tipo]);

  // Palavras guardadas na sala, também só no lobby: é lá que o professor confere a
  // atividade que preparou antes de mandar a turma começar
  useEffect(() => {
    if (estado && estado.tipo !== 'AGUARDANDO') return;
    axios
      .get<PalavrasDaSala>(`/api/salas/${codigoSala}/palavras`)
      .then(res => setPalavrasSala(res.data))
      .catch(() => setPalavrasSala(null));
  }, [codigoSala, estado?.tipo]);

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
    if (!palavraTexto) return;
    setFalando(true);
    falarPalavra(palavraTexto, { onEnd: () => setFalando(false) });
  };

  // Fala a palavra automaticamente ao mudar de rodada (o professor dita para a
  // turma pela caixa de som da sala; cada aluno também tem o botão no aparelho)
  useEffect(() => {
    setShowRanking(false);
    rankingTriggeredRef.current = false;
  }, [estado?.palavraAtual?.id]);

  // Dita a palavra para a turma ao mudar de rodada. Depende do TEXTO (que pode
  // chegar depois do estado, pelo endpoint do professor), não só do id da palavra.
  useEffect(() => {
    setFalando(false);
    if (palavraTexto) {
      // O módulo já aplica a pausa de 1s antes de falar; rate menor para o ditado
      setFalando(true);
      falarPalavra(palavraTexto, { rate: 0.5, onEnd: () => setFalando(false) });
    }
  }, [palavraTexto]);

  // Conta o tempo restante da rodada, recalcula a cada 500ms a partir do timestampInicio
  useEffect(() => {
    const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
    if (!ativo) {
      setTempoRestante(0);
      return;
    }
    const calc = () => {
      const elapsed = Date.now() - estado.timestampInicio;
      setTempoRestante(Math.max(0, estado.tempoLimite - Math.floor(elapsed / 1000)));
      // Contagem até a próxima palavra, derivada do fechamento que o SERVIDOR mandou:
      // é ele quem vira a rodada, esta tela só desenha quanto falta
      const proxima = estado.timestampFechamento + estado.tempoRanking * 1000;
      setRankingTimer(Math.max(0, Math.ceil((proxima - Date.now()) / 1000)));
    };
    calc();
    const id = setInterval(calc, 500);
    return () => clearInterval(id);
  }, [estado?.timestampInicio, estado?.tempoLimite, estado?.tipo, estado?.timestampFechamento, estado?.tempoRanking]);

  /**
   * Abre a tela de ranking quando a rodada FECHA.
   *
   * O marco é o timestampFechamento do servidor, que já cobre os dois jeitos de
   * fechar: o tempo esgotar ou todos os alunos responderem antes. Comparar com o
   * relógio do servidor (e não com tempoRestante, que começa a rodada valendo 0 antes
   * do primeiro tick) é o que evita cair direto no ranking ao abrir a tela no meio de
   * uma rodada - recarregar a página, voltar para a sala.
   *
   * Esta tela NÃO pede mais a próxima palavra. Ela era quem contava os 8 segundos e
   * pedia a virada, então a aba fechando, a rede caindo ou o celular suspendendo a aba
   * deixavam a turma inteira parada aqui. Agora o servidor vira a rodada sozinho
   * (JogoSalaRodadaScheduler) e o novo estado chega pelo WebSocket.
   */
  useEffect(() => {
    const emJogo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
    if (!emJogo || estado?.palavraAtual == null || rankingTriggeredRef.current) return;
    if (Date.now() < estado.timestampFechamento) return;
    rankingTriggeredRef.current = true;
    // Durante a rodada o servidor não transmite o placar a cada resposta; o ranking
    // que vai aparecer precisa da pontuação de agora, então pede antes de trocar de tela
    onPedirEstado?.();
    setShowRanking(true);
  }, [tempoRestante, estado, onPedirEstado]);

  const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';
  const pct = estado && estado.tempoLimite > 0 ? (tempoRestante / estado.tempoLimite) * 100 : 0;
  const timerDanger = tempoRestante <= 5;

  /* LOBBY */
  if (!estado || estado.tipo === 'AGUARDANDO') {
    // O servidor já deixa o professor fora da lista; o filtro aqui cobre a sala que
    // ficou com um registro antigo dele (sessão aberta antes desta correção)
    const alunos = (estado?.alunosConectados ?? []).filter(a => a.login !== meuLogin);
    // As quantidades desta tela ainda batem com as da configuração gravada? Se o
    // professor mexeu nos steppers, a lista pré-sorteada não corresponde mais ao que
    // ele pediu - aí ela é descartada e o servidor sorteia na hora de iniciar
    const qtdsIntactas = mesmasQuantidades(cfg, initialGameConfig);
    const qtdsAlteradas = !qtdsIntactas && (palavrasSala?.sorteadas.length ?? 0) > 0;
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

        {/* Partida anterior desta sala (snapshot no banco): a sala reaberta não
            volta em branco, o professor abre aqui mesmo o ranking e o relatório
            da última turma, sem sair da sala nem precisar reabrir nada */}
        <BlocoUltimaPartida ultima={ultimaPartida} codigoSala={codigoSala} nomes={nomesReais} turmas={turmas} />

        <div className="sj-lobby-cols">
          <div className="sj-cfg-card">
            <h3 className="sj-cfg-title">Configurar atividade</h3>
            {/* Um tempo de rodada para cada dificuldade */}
            {TEMPOS.map(({ key, label, cor }) => (
              <div className="sj-cfg-field" key={key}>
                <div className="sj-cfg-field-label">
                  <span className="sj-cfg-diff-dot" style={{ background: cor }} /> Tempo · {label} <strong>{cfg[key]}s</strong>
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

          <PainelAlunosConectados alunos={alunos} nomes={nomesReais} turmas={turmas} />
        </div>

        {/* A atividade que está guardada na sala: o professor pode ter montado a
            lista dias antes e chegado aqui só na hora da aula */}
        <PainelPalavrasDaSala palavras={palavrasSala} qtdsAlteradas={qtdsAlteradas} jaTevePartida={!!ultimaPartida} />

        <button
          className="sj-iniciar-btn"
          disabled={
            !conectado || (cfg.qtdFacil + cfg.qtdMedio + cfg.qtdDificil === 0 && (initialGameConfig?.palavrasExtrasIds?.length ?? 0) === 0)
          }
          // Preserva as palavras extras e as palavras pré-sorteadas na tela de criação.
          // Se o professor mudou as QUANTIDADES aqui no lobby, a lista pré-sorteada não
          // corresponde mais à configuração, descarta e deixa o servidor sortear na hora.
          onClick={() => {
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

  // Placar sem o próprio professor: ele comanda a partida, não compete, não deve
  // aparecer no pódio nem no ranking que os alunos disputam
  const placarAlunos = estado.placar.filter(p => p.login !== meuLogin);

  /* ENCERRADA */
  if (estado.tipo === 'ENCERRADA') {
    // Antes do placar final, roda a vinheta de suspense revelando o pódio
    if (!vinhetaFimConcluida) {
      return <VinhetaPodio placar={placarAlunos} onFim={() => setVinhetaFimConcluida(true)} nomes={nomesReais} turmas={turmas} />;
    }
    return (
      <div className="sj-ended">
        <h2 className="sj-ended-title">Atividade encerrada!</h2>

        {/* Ranking completo da partida */}
        <h3 className="sj-rel-secao">Ranking da partida</h3>
        <RankingPartida posicoes={placarAlunos} nomes={nomesReais} turmas={turmas} />

        {/* Métrica de cada aluno: o mesmo resumo que ele acabou de ver no próprio
            aparelho (o que acertou, o que errou, como foi ante a média da turma) */}
        <h3 className="sj-rel-secao">Resumo de cada aluno</h3>
        <MetricasPorAluno codigoSala={codigoSala} posicoes={placarAlunos} nomes={nomesReais} turmas={turmas} />

        {/* Relatório da partida: cada palavra com quem escreveu o quê
            Os dados vêm do endpoint restrito ao dono da sala (carregados no
            useEffect quando o estado vira ENCERRADA) */}
        <h3 className="sj-rel-secao">Relatório por palavra</h3>
        <RelatorioPorPalavra relatorio={relatorio} nomes={nomesReais} />

        {/* A sala NÃO FECHA MAIS. O botão que existia aqui marcava ativo=false no
            banco e a sala sumia das listagens - junto com o caminho para rever o
            desempenho da turma. Agora o professor só sai; a sala continua dele,
            com o resumo desta partida guardado, e ele volta nela quando quiser. */}
        <button type="button" className="sj-voltar-lobby-btn" onClick={() => navigate('/lobby')}>
          Voltar ao lobby
        </button>
        <p className="sj-sala-aberta-aviso">
          Esta sala continua aberta. Você reencontra o desempenho desta partida em <strong>Minhas Salas</strong>.
        </p>
      </div>
    );
  }

  /* RANKING entre palavras */
  if (showRanking) {
    return (
      <TelaRankingProfessor
        estado={estado}
        palavraTexto={palavraTexto}
        segundosParaProxima={rankingTimer}
        placarAlunos={placarAlunos}
        posRef={posRef}
        nomes={nomesReais}
        turmas={turmas}
      />
    );
  }

  /* EM JOGO, painel do professor
     O professor não digita respostas: ele acompanha a rodada. O painel mostra
     a palavra atual (só ele vê o texto, os alunos recebem apenas o áudio nos
     seus aparelhos), os números ao vivo da rodada e a lista de palavras já
     jogadas com a taxa de acerto de cada uma. */

  // Números AO VIVO da rodada atual, derivados do placar que o WebSocket
  // broadcast a cada resposta (sem nenhuma requisição extra):
  // status ACERTOU/ERROU = já respondeu; AGUARDANDO = ainda não.
  const respondidas = placarAlunos.filter(p => p.statusAtual === 'ACERTOU' || p.statusAtual === 'ERROU').length;
  const acertosAoVivo = placarAlunos.filter(p => p.statusAtual === 'ACERTOU').length;
  const pctAoVivo = respondidas > 0 ? Math.round((acertosAoVivo / respondidas) * 100) : 0;
  const alunosConectados = estado.alunosConectados.filter(a => a.login !== meuLogin);
  const totalAlunos = alunosConectados.length;
  // Todos os alunos conectados já responderam esta palavra? Só então liberamos o
  // professor a passar de palavra antes do tempo acabar (mesma leitura do placar
  // que o WebSocket já entrega; robusto a quem saiu no meio da rodada).
  const todosResponderam =
    totalAlunos > 0 &&
    alunosConectados.every(a => {
      const p = estado.placar.find(pl => pl.login === a.login);
      return p && (p.statusAtual === 'ACERTOU' || p.statusAtual === 'ERROU');
    });

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

        {/* Palavra atual em destaque, visível apenas nesta tela do professor */}
        {estado.palavraAtual && (
          <div className="sj-dash-atual">
            <span className="sj-dash-atual-label">Palavra atual</span>
            <div className="sj-dash-atual-row">
              <span className="sj-dash-atual-texto">{palavraTexto ?? '···'}</span>
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
            disabled={!ativo || !palavraTexto}
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

        {/* Passar de palavra sem esperar o tempo acabar: liberado só quando TODOS
            os alunos conectados já responderam. Antes disso mostra o progresso. */}
        <button type="button" className="sj-proxima-btn" onClick={() => onProxima(estado.indiceAtual)} disabled={!todosResponderam}>
          {todosResponderam ? 'Todos responderam · Próxima palavra →' : `Aguardando respostas · ${respondidas}/${totalAlunos}`}
        </button>

        {/* Palavras da partida: as já jogadas com % consolidado (do relatório) e a
            atual com os números ao vivo (do placar), nunca antecipa as próximas */}
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
