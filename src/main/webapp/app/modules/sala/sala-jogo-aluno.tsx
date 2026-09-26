import React, { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { EstadoJogo, FeedbackAluno } from './hooks/useSalaWebSocket';
import { compararLetras, MENSAGEM_ERRO, validarResposta } from './utils/validarResposta';
import { RODADA_RAPIDA_LIMITE, RelogioRodada } from './relogio-rodada';
import { ouvirPalavraDaRodada, pararAudioDaPalavra } from './utils/ouvir-palavra';
import { RankingNuvem } from './ranking-nuvem';
import { VinhetaPodio } from './vinheta-podio';
import { AmpulhetaAnimada } from './ampulheta-animada';
import { posicoesRanking } from './utils/posicoes-ranking';
import { EntradaPalavra } from 'app/shared/components/entrada-palavra/entrada-palavra';
import { IconeAudio } from 'app/shared/components/icone-audio/icone-audio';
import { ResumoAluno, ResumoAlunoPartida } from './resumo-aluno';

// Configuração enviada ao iniciar a partida, mesmo shape usado pela tela do professor
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

interface Props {
  estado: EstadoJogo | null;
  feedback: FeedbackAluno | null;
  meuLogin: string;
  // Devolve false quando a resposta não chegou a ser enviada (WebSocket caído)
  onResponder: (resposta: string, tentativasBurla?: number) => boolean;
  conectado: boolean;
  // Sala em modo duelo 1v1 (vale para os DOIS jogadores): quando todos os
  // conectados já responderam, a tela de correção da palavra aparece na hora,
  // sem esperar o tempo da rodada esgotar
  duelo1v1?: boolean;
  // Modo CRIADOR do duelo 1v1
  // APENAS no 1v1 quem criou a sala joga junto: além de digitar como qualquer
  // jogador, ele inicia o duelo e seu cliente avança as rodadas automaticamente
  // (papéis que na sala de turma pertencem ao professor).
  criadorDuelo?: boolean;
  // Código da sala: o criador do duelo usa para compartilhar, e TODO aluno usa
  // para buscar o próprio resumo quando a partida encerra
  codigoSala?: string;
  onIniciar?: (cfg: GameConfig) => void;
  initialGameConfig?: GameConfig;
  // Pede ao servidor o placar atualizado só para este aparelho (ver pedirEstado)
  onPedirEstado?: () => void;
}

// Tela de espera do CRIADOR do duelo 1v1 (componente próprio para não inflar a
// função principal): compartilha o código, mostra a configuração que valerá na
// partida, vê o oponente chegar e inicia o duelo, jogando junto
const EsperaCriadorDuelo: React.FC<{
  estado: EstadoJogo | null;
  conectado: boolean;
  codigoSala?: string;
  cfg: GameConfig;
  onIniciar?: (cfg: GameConfig) => void;
}> = ({ estado, conectado, codigoSala, cfg, onIniciar }) => {
  // "✓ Copiado!" temporário ao clicar no código do duelo
  const [copied, setCopied] = useState(false);
  const jogadores = estado?.alunosConectados?.length ?? 0;
  const temOponente = jogadores >= 2;
  const totalPalavras = cfg.qtdFacil + cfg.qtdMedio + cfg.qtdDificil + (cfg.palavrasExtrasIds?.length ?? 0);

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
      <p className="sj-waiting-sub">
        {totalPalavras} palavra{totalPalavras === 1 ? '' : 's'} · tempo {cfg.tempoFacil}s / {cfg.tempoMedio}s / {cfg.tempoDificil}s
      </p>
      <button type="button" className="sj-iniciar-btn" disabled={!conectado || !temOponente} onClick={() => onIniciar?.(cfg)}>
        {!conectado ? 'Conectando...' : temOponente ? '⚔ Iniciar duelo' : 'Aguardando oponente para iniciar...'}
      </button>
      <p className="sj-waiting-sub">Você também joga: ao iniciar, ouça as palavras e digite mais rápido que o oponente!</p>
    </div>
  );
};

/**
 * Avisos que aparecem sob o campo de resposta. Cada um explica por que algo NÃO
 * aconteceu - recusar em silêncio parece a tela travada.
 *
 * Extraído da tela principal, que já estava no limite de complexidade do projeto.
 */
const AvisosDoCampo: React.FC<{
  bloqueioCorretor: boolean;
  erroEnvio: boolean;
  audioBloqueado: boolean;
}> = ({ bloqueioCorretor, erroEnvio, audioBloqueado }) => (
  <>
    {bloqueioCorretor && (
      <div className="sj-feedback sj-feedback--warn">
        <span className="sj-feedback-icon">⌨</span>
        <div className="sj-feedback-body">
          <strong>Escreva você mesmo</strong> · corretor, sugestão do teclado, copiar e colar não valem aqui
        </div>
      </div>
    )}
    {erroEnvio && (
      <div className="sj-feedback sj-feedback--warn">
        <span className="sj-feedback-icon">⚠</span>
        <div className="sj-feedback-body">
          <strong>Sem conexão com a sala</strong> · sua resposta não foi enviada, toque em enviar de novo
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
  </>
);

/**
 * Confirmação logo após o envio da resposta.
 *
 * No acerto a tela celebra na hora; no erro fica NEUTRA (nada de ✗ vermelho) - o
 * resultado só é revelado no fim da rodada, junto do percentual e do que ele errou.
 */
const FeedbackDoEnvio: React.FC<{ feedback: FeedbackAluno | null }> = ({ feedback }) => {
  if (!feedback) return null;
  return (
    <div className={`sj-feedback${feedback.correta && feedback.registrada ? ' sj-feedback--ok' : ' sj-feedback--warn'}`}>
      <span className="sj-feedback-icon">{feedback.registrada ? '✓' : '⏱'}</span>
      <div className="sj-feedback-body">
        {!feedback.registrada ? (
          // O servidor recusou a resposta (chegou fora do tempo da rodada):
          // avisar na hora evita a impressão de acerto que não virou ponto
          <>
            <strong>Resposta fora do tempo</strong> · não foi contabilizada
          </>
        ) : feedback.correta ? (
          <>
            <strong>{feedback.ordem === 1 ? '1º a acertar!' : `${feedback.ordem}º a acertar`}</strong> · palavra correta
          </>
        ) : (
          <>
            {/* Ao enviar mostramos só que errou; o quanto acertou (%) e o que
                errou aparecem no fim da rodada, quando o tempo acaba */}
            <strong>Resposta enviada</strong> · veja o resultado quando o tempo acabar
          </>
        )}
      </div>
      {feedback.correta && feedback.registrada && <span className="sj-feedback-pts">+{feedback.pontos} pts</span>}
    </div>
  );
};

/**
 * Tela de fim de partida do aluno: o placar final e, abaixo dele, o resumo pessoal.
 *
 * Componente separado pelo mesmo motivo do EsperaCriadorDuelo e do
 * TelaRankingRodada: a função principal já estava no limite de complexidade que o
 * projeto aceita.
 */
const TelaFimDaPartida: React.FC<{
  placar: EstadoJogo['placar'];
  meuLogin: string;
  resumo: ResumoAluno | null;
}> = ({ placar, meuLogin, resumo }) => {
  // Empate divide o lugar (1º, 2º, 2º, 4º), igual ao ranking que o professor vê
  const lugaresFinais = posicoesRanking(placar.map(p => p.pontos));
  return (
    <div className="sj-ended">
      <h2>Atividade encerrada!</h2>
      <div className="sj-final-placar">
        {placar.map((p, i) => (
          <div key={p.login} className={`sj-final-row${p.login === meuLogin ? ' sj-final-me' : ''}`}>
            <span className="sj-final-rank">{lugaresFinais[i]}º</span>
            <span className="sj-final-nome">
              {p.nome || p.login}
              {p.login === meuLogin ? ' (você)' : ''}
            </span>
            <span className="sj-final-pts">{p.pontos} pts</span>
          </div>
        ))}
      </div>

      {/* Resumo pessoal: só este aluno vê o dele (e o professor, pela tela de
          métricas por aluno). Fica abaixo do placar, que é o que a turma
          comenta em voz alta assim que a partida acaba. */}
      {resumo && (
        <>
          <h3 className="sj-rel-secao">Seu resumo da partida</h3>
          <ResumoAlunoPartida resumo={resumo} titulo="Seu desempenho" />
        </>
      )}
    </div>
  );
};

/**
 * Tela entre uma palavra e a próxima: a palavra certa, o resultado do próprio aluno,
 * a contagem para a próxima e o ranking da turma.
 *
 * Componente separado pelo mesmo motivo do EsperaCriadorDuelo: a função principal já
 * estava no limite de complexidade que o projeto aceita.
 *
 * A contagem mostrada vem do SERVIDOR (ver segundosParaProxima): é ele quem vira a
 * rodada, esta tela só espera a palavra nova chegar pelo WebSocket.
 */
const TelaRankingRodada: React.FC<{
  estado: EstadoJogo;
  feedback: FeedbackAluno | null;
  validacaoLocal: ReturnType<typeof validarResposta> | null;
  jaRespondeu: boolean;
  resposta: string;
  meuLogin: string;
  segundosParaProxima: number;
  posRef: React.MutableRefObject<Map<string, number>>;
  // Palavra certa já resolvida pela tela principal (estado ou feedback)
  palavraCorreta: string;
}> = ({ estado, feedback, validacaoLocal, jaRespondeu, resposta, meuLogin, segundosParaProxima, posRef, palavraCorreta }) => (
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
        {/* A palavra certa vem do estado quando a rodada fechou; enquanto o estado
            atualizado não chega, vale a que o servidor mandou no feedback da
            resposta deste aluno (durante a rodada o texto não é transmitido) */}
        <span className="sj-palavra-correta-val">{palavraCorreta || '···'}</span>
        {/* Só o aluno vê o próprio resultado, revelado agora, no fim da rodada */}
        <ResultadoRodada
          feedback={feedback}
          validacaoLocal={validacaoLocal}
          jaRespondeu={jaRespondeu}
          resposta={resposta}
          palavraCorreta={palavraCorreta}
        />
      </div>
    )}

    {/* A contagem aparece para todos: quem vira a rodada é o servidor, no mesmo
        instante para a sala inteira - antes só o criador do duelo a via, porque
        era o cliente dele que pedia a próxima palavra */}
    <div className="sj-ranking-countdown">
      <span className="sj-ranking-next-label">Próxima palavra em</span>
      <span className="sj-ranking-next-val">{segundosParaProxima}s</span>
    </div>

    <RankingNuvem placar={estado.placar} meuLogin={meuLogin} posRef={posRef} />
  </div>
);

/**
 * Resultado da rodada para o próprio aluno, revelado quando o tempo acaba.
 *
 * Quem decide é o SERVIDOR: era a conferência local que dizia "você acertou" para uma
 * resposta que o servidor tinha recusado (por chegar fora do tempo, por exemplo), e o
 * aluno terminava a rodada sem entender por que não ganhou ponto. A conferência local
 * continua valendo enquanto o feedback do servidor não chega, e é ela que detalha o
 * erro (a % da palavra e as letras trocadas), que o servidor não manda.
 */
const ResultadoRodada: React.FC<{
  feedback: FeedbackAluno | null;
  validacaoLocal: ReturnType<typeof validarResposta> | null;
  jaRespondeu: boolean;
  resposta: string;
  palavraCorreta: string;
}> = ({ feedback, validacaoLocal, jaRespondeu, resposta, palavraCorreta }) => {
  // Resposta enviada mas não contabilizada: dizer isso é melhor do que mostrar um
  // acerto que não virou ponto
  if (feedback && !feedback.registrada) {
    return <span className="sj-similaridade sj-similaridade--warn">⏱ Sua resposta chegou fora do tempo e não foi contabilizada</span>;
  }
  if (!jaRespondeu || (!validacaoLocal && !feedback)) {
    return <span className="sj-similaridade sj-similaridade--warn">Você não respondeu a tempo</span>;
  }
  const acertou = feedback ? feedback.correta : validacaoLocal?.correta === true;
  if (acertou) {
    return <span className="sj-similaridade sj-similaridade--ok">✓ Você acertou!{feedback ? ` +${feedback.pontos} pts` : ''}</span>;
  }
  return (
    <>
      <span className="sj-similaridade sj-similaridade--err">
        ✗ Você errou
        {validacaoLocal ? ` · você acertou ${Math.round(validacaoLocal.similaridade * 100)}% da palavra` : ''}
        {validacaoLocal?.tipoErro ? ` · ${MENSAGEM_ERRO[validacaoLocal.tipoErro]}` : ''}
      </span>
      {/* Mostra O QUE o aluno errou: a resposta dele com as letras trocadas ou a mais
          destacadas em vermelho (a palavra certa aparece inteira logo acima, para ele comparar) */}
      <span className="sj-diff">
        <span className="sj-diff-label">Você escreveu:</span>{' '}
        {compararLetras(resposta, palavraCorreta).digitado.map((l, idx) => (
          <span key={idx} className={l.ok ? 'sj-diff-ok' : 'sj-diff-err'}>
            {l.ch}
          </span>
        ))}
      </span>
    </>
  );
};

// Tela do aluno durante a partida: aguarda o professor iniciar, recebe a palavra via áudio,
// digita a resposta e vê o feedback individual e o placar ao vivo dos colegas
export const SalaJogoAluno: React.FC<Props> = ({
  estado,
  feedback,
  meuLogin,
  onResponder,
  conectado,
  duelo1v1,
  criadorDuelo,
  codigoSala,
  onIniciar,
  initialGameConfig,
  onPedirEstado,
}) => {
  const [resposta, setResposta] = useState('');
  const [falando, setFalando] = useState(false);
  const [jaRespondeu, setJaRespondeu] = useState(false);
  const [tempoRestante, setTempoRestante] = useState(0);
  const [validacaoLocal, setValidacaoLocal] = useState<ReturnType<typeof validarResposta> | null>(null);
  // Envio que não saiu do aparelho (conexão caída): o aluno precisa saber para tentar
  // de novo, em vez de achar que respondeu e terminar a rodada sem ponto
  const [erroEnvio, setErroEnvio] = useState(false);
  // O campo recusou um texto que não foi digitado (sugestão do teclado, corretor,
  // colagem). Vira um aviso na tela: recusar em silêncio parece o campo travado.
  const [bloqueioCorretor, setBloqueioCorretor] = useState(false);
  // O navegador recusou tocar o áudio sem um toque (política de autoplay do iOS e
  // do Chrome). Sem avisar, o aluno espera um som que nunca vem.
  const [audioBloqueado, setAudioBloqueado] = useState(false);
  // Ranking exibido quando o tempo da rodada acaba (mesma tela que o professor vê)
  const [showRanking, setShowRanking] = useState(false);
  // Contagem regressiva do ranking, usada SÓ pelo criador do duelo, cujo cliente
  // avança para a próxima palavra (nas salas de turma quem avança é o professor)
  const [rankingTimer, setRankingTimer] = useState(0);
  // Pontuação/posição "congeladas" no início da rodada, só atualizam quando o tempo acaba,
  // para o aluno não descobrir o resultado dos colegas pelo placar enquanto digita
  const [scoreCongelado, setScoreCongelado] = useState<{ pontos: number; posicao: number }>({ pontos: 0, posicao: -1 });
  // Vinheta de suspense com o pódio, exibida uma única vez quando a partida encerra
  const [vinhetaFimConcluida, setVinhetaFimConcluida] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  // Tentativas de burla (colar, corretor) bloqueadas pelo EntradaPalavra na rodada atual
  const burlasRef = useRef(0);
  const palavraAtualId = useRef<number | null>(null);
  const rankingTriggeredRef = useRef(false);
  // Posições da rodada anterior no top 5, usado pela animação de ultrapassagem
  const posRef = useRef<Map<string, number>>(new Map());

  /**
   * Resumo pessoal da partida, buscado quando ela encerra.
   *
   * O placar final só diz quantos pontos cada um fez; o que o aluno quer saber é
   * QUAIS palavras ele acertou, quais errou e como foi em relação à turma. O
   * servidor devolve só os dados dele - as respostas dos colegas entram apenas
   * como o percentual de erro de cada palavra.
   */
  const [resumo, setResumo] = useState<ResumoAluno | null>(null);
  useEffect(() => {
    if (estado?.tipo !== 'ENCERRADA' || !codigoSala) return;
    axios
      .get<ResumoAluno>(`/api/salas/${codigoSala}/meu-resumo`)
      // 404: o aluno entrou mas não respondeu nenhuma palavra - nada a resumir
      .then(res => setResumo(res.data))
      .catch(() => setResumo(null));
  }, [estado?.tipo, codigoSala]);

  // Detecta mudança de palavra e reseta o estado de resposta, fala a palavra automaticamente
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
        setErroEnvio(false);
        setBloqueioCorretor(false);
        setFalando(false);
        setAudioBloqueado(false);
        setShowRanking(false);
        rankingTriggeredRef.current = false;
        // Congela a pontuação/posição atuais para exibir durante toda a rodada
        const idx = estado.placar.findIndex(p => p.login === meuLogin);
        setScoreCongelado({ pontos: idx >= 0 ? estado.placar[idx].pontos : 0, posicao: idx });
        if (estado.palavraAtual) {
          // O módulo já aplica a pausa de 1s antes de tocar. Sem texto no estado, o
          // áudio vem do SERVIDOR - é assim que a palavra deixa de chegar ao aparelho
          // do aluno como resposta legível.
          setFalando(true);
          setAudioBloqueado(false);
          ouvirPalavraDaRodada({
            codigoSala,
            texto: estado.palavraAtual.texto,
            onEnd: () => setFalando(false),
            onBloqueadoPeloNavegador: () => setAudioBloqueado(true),
          });
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
      // Contagem até a próxima palavra, derivada do fechamento que o SERVIDOR mandou
      const proxima = estado.timestampFechamento + estado.tempoRanking * 1000;
      setRankingTimer(Math.max(0, Math.ceil((proxima - Date.now()) / 1000)));
    };
    calcTempo();
    const id = setInterval(calcTempo, 500);
    return () => clearInterval(id);
  }, [estado?.timestampInicio, estado?.tempoLimite, estado?.tipo, estado?.timestampFechamento, estado?.tempoRanking]);

  // Reproduz a palavra ao clicar no botão de áudio e atualiza o ícone enquanto fala.
  // pausaMs: 0, reouvir toca na hora, sem a espera de 1s da palavra da rodada
  const handleFalar = useCallback(() => {
    if (!estado?.palavraAtual) return;
    setFalando(true);
    setAudioBloqueado(false);
    ouvirPalavraDaRodada({
      codigoSala,
      texto: estado.palavraAtual.texto,
      pausaMs: 0,
      onEnd: () => setFalando(false),
      onBloqueadoPeloNavegador: () => setAudioBloqueado(true),
    });
  }, [estado?.palavraAtual, codigoSala]);

  // Envia a resposta: faz validação local para feedback imediato antes de receber o do servidor
  const handleEnviar = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (!resposta.trim() || jaRespondeu) return;
      if (!estado?.palavraAtual) return;
      // Só dá a rodada por respondida se a mensagem REALMENTE saiu: com o socket
      // reconectando, o publish não chega ao servidor e travar o campo aqui deixaria
      // o aluno sem nova tentativa - e sem ponto pela palavra que ele acertou
      const enviada = onResponder(resposta.trim(), burlasRef.current);
      if (!enviada) {
        setErroEnvio(true);
        return;
      }
      setErroEnvio(false);
      // Só há o que conferir aqui se o texto veio no estado (servidor sem áudio).
      // No modo seguro a conferência acontece quando o feedback traz a palavra certa.
      if (estado.palavraAtual.texto) {
        setValidacaoLocal(validarResposta(resposta, estado.palavraAtual.texto));
      }
      setJaRespondeu(true);
    },
    [resposta, jaRespondeu, estado?.palavraAtual, onResponder],
  );

  /**
   * A palavra certa da rodada, para a tela de correção.
   *
   * Durante a rodada o servidor NÃO transmite o texto (ele ia para o tópico que
   * todo aluno assina, entregando a resposta antes da hora). Quando a rodada fecha,
   * o texto volta a vir no estado; e o aluno que respondeu já o recebeu no feedback
   * individual dele. Vale o que estiver disponível primeiro.
   */
  const palavraCorreta = estado?.palavraAtual?.texto || feedback?.textoCorreto || '';

  /**
   * Conferência local da resposta: quanto da palavra o aluno acertou e que letras
   * trocou. É o que detalha o erro na tela - o servidor manda se acertou e o tipo
   * do erro, não o percentual nem as letras.
   *
   * Roda quando a palavra certa aparece, e não mais no instante do envio: sem o
   * texto no estado, no envio não havia com o que comparar. O texto do FEEDBACK
   * chega junto com a resposta do servidor, então a conta acontece igual.
   */
  useEffect(() => {
    if (!jaRespondeu || !resposta.trim() || !palavraCorreta || validacaoLocal) return;
    setValidacaoLocal(validarResposta(resposta, palavraCorreta));
  }, [jaRespondeu, resposta, palavraCorreta, validacaoLocal]);

  // Sai da tela: para o áudio que estiver tocando, senão ele continua depois de o
  // aluno voltar ao lobby
  useEffect(() => pararAudioDaPalavra, []);

  const ativo = estado?.tipo === 'NOVA_PALAVRA' || estado?.tipo === 'INICIADA';

  // Exibe a tela de ranking quando a rodada FECHA, e espera a próxima palavra chegar
  // do servidor. O marco é o timestampFechamento dele, que já cobre os dois jeitos de
  // fechar (tempo esgotado ou todos responderam). Comparar com o relógio do servidor -
  // e não com tempoRestante, que vale 0 antes do primeiro tick - é o que evita o
  // ranking aparecer na hora ao abrir a tela no meio de uma rodada.
  useEffect(() => {
    if (!ativo || estado?.palavraAtual == null || rankingTriggeredRef.current) return;
    if (Date.now() < estado.timestampFechamento) return;
    rankingTriggeredRef.current = true;
    // O placar na tela é o do início da rodada: durante ela o servidor não
    // transmite mais nada para o aluno. Pede o atualizado antes de mostrar o ranking.
    onPedirEstado?.();
    setShowRanking(true);
  }, [tempoRestante, ativo, estado, onPedirEstado]);

  // Se TODOS os conectados já responderam a palavra, vai direto para a tela de
  // correção/ranking, não faz sentido ficar olhando o relógio depois de todo
  // mundo já ter digitado. Vale para o duelo 1v1 (2 jogadores) e para a sala de
  // turma (a partir de 1 aluno): assim, quando a turma termina, cada aluno já vê
  // o próprio resultado enquanto o professor decide quando passar a palavra.
  useEffect(() => {
    if (!ativo || !estado?.palavraAtual || rankingTriggeredRef.current) return;
    const jogadores = estado.alunosConectados;
    // Duelo exige os 2 jogadores; sala de turma vale a partir de 1 aluno conectado
    const minimo = duelo1v1 ? 2 : 1;
    if (jogadores.length < minimo) return;
    const todosResponderam = jogadores.every(j => {
      const p = estado.placar.find(pl => pl.login === j.login);
      return p && (p.statusAtual === 'ACERTOU' || p.statusAtual === 'ERROU');
    });
    if (todosResponderam) {
      rankingTriggeredRef.current = true;
      setShowRanking(true);
    }
  }, [duelo1v1, ativo, estado, criadorDuelo]);

  // Aqui existia o avanço automático do duelo: o cliente de quem CRIOU a sala contava
  // os segundos do ranking e pedia a próxima palavra. Se ele fechasse a aba ou perdesse
  // a rede, o oponente ficava preso no ranking para sempre - nada no servidor virava a
  // rodada. Quem vira agora é o relógio do servidor (JogoSalaRodadaScheduler), igual
  // para os dois jogadores; esta tela só desenha a contagem.

  if (!estado || estado.tipo === 'AGUARDANDO') {
    if (criadorDuelo) {
      return (
        <EsperaCriadorDuelo
          estado={estado}
          conectado={conectado}
          codigoSala={codigoSala}
          cfg={initialGameConfig ?? DUELO_CFG_PADRAO}
          onIniciar={onIniciar}
        />
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
    return <TelaFimDaPartida placar={estado.placar} meuLogin={meuLogin} resumo={resumo} />;
  }

  /* RANKING entre palavras (rodada fechada, esperando o servidor virar a palavra) */
  if (showRanking && ativo) {
    return (
      <TelaRankingRodada
        estado={estado}
        feedback={feedback}
        validacaoLocal={validacaoLocal}
        jaRespondeu={jaRespondeu}
        resposta={resposta}
        meuLogin={meuLogin}
        segundosParaProxima={rankingTimer}
        posRef={posRef}
        palavraCorreta={palavraCorreta}
      />
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
        {/* Pontuação congelada no início da rodada, só atualiza quando o tempo acaba */}
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
              // Avisa o aluno: sem isso, tocar numa sugestão do teclado simplesmente
              // não fazia nada e ele ficava tentando de novo, achando que travou
              setBloqueioCorretor(true);
            }}
            placeholder="escreva aqui..."
            disabled={jaRespondeu || !ativo}
          />
          <button type="submit" className="sj-send-btn" disabled={!resposta.trim() || jaRespondeu || !ativo}>
            {jaRespondeu ? 'Resposta enviada ✓' : 'Enviar resposta →'}
          </button>
          <AvisosDoCampo bloqueioCorretor={bloqueioCorretor} erroEnvio={erroEnvio} audioBloqueado={audioBloqueado} />
        </form>

        <FeedbackDoEnvio feedback={feedback} />

        {validacaoLocal && !validacaoLocal.correta && jaRespondeu && !feedback && (
          <div className="sj-feedback sj-feedback--warn">
            <span className="sj-feedback-icon">✓</span>
            <div className="sj-feedback-body">
              {/* Sem revelar acerto/erro nem % aqui, o resultado só sai no fim da rodada */}
              Resposta enviada · veja o resultado quando o tempo acabar
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default SalaJogoAluno;
