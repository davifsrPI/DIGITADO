import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Storage } from 'react-jhipster';

// Tipos que trafegam pelo WebSocket

export interface PalavraWS {
  id: number;
  texto: string;
  dificuldade: string;
  categoria?: string;
}

export interface PlacarEntry {
  login: string;
  nome: string;
  pontos: number;
  statusAtual: string;
  // Respostas suspeitas de burla na partida (colar/corretor bloqueado ou rápida demais)
  alertas: number;
}

export interface AlunoConectado {
  login: string;
  nome: string;
}

// Estado completo do jogo enviado pelo servidor a cada evento (nova palavra, resposta, encerramento)
export interface EstadoJogo {
  tipo: 'AGUARDANDO' | 'INICIADA' | 'NOVA_PALAVRA' | 'PAUSADA' | 'ENCERRADA';
  palavraAtual?: PalavraWS;
  indiceAtual: number;
  totalPalavras: number;
  tempoLimite: number;
  // Já convertido para o relógio do CLIENTE por corrigirRelogio, as telas podem
  // comparar com Date.now() diretamente
  timestampInicio: number;
  // Hora do servidor quando a mensagem foi montada (referência da conversão)
  timestampServidor: number;
  placar: PlacarEntry[];
  nomeSala: string;
  codigoSala: string;
  alunosConectados: AlunoConectado[];
}

// Feedback individual enviado pelo servidor após uma resposta (só o aluno que respondeu recebe)
export interface FeedbackAluno {
  correta: boolean;
  pontos: number;
  ordem: number;
  tipoErro?: string;
  textoCorreto: string;
  // A resposta foi contabilizada? false quando o servidor a recusou (chegou fora do
  // tempo da rodada) - sem este aviso a tela mostrava o resultado da conferência local
  // e o aluno via "acertou" sem ganhar ponto
  registrada: boolean;
}

// Erro enviado pelo servidor via WebSocket (ex: tentativa não autorizada de iniciar)
export interface ErroWS {
  tipo: string;
  mensagem: string;
}

// Traz o início da rodada para o relógio do CLIENTE.
// O servidor manda quando a rodada começou e que horas são para ele; a diferença
// entre "que horas são para ele" e "que horas são aqui" é o desencontro dos dois
// relógios (mais o tempo de rede, desprezível perto de uma rodada). Sem essa
// correção, um celular com a hora adiantada calculava a rodada como já vencida e
// caía direto no ranking, como se o aluno não tivesse respondido a tempo.
export function corrigirRelogio(estado: EstadoJogo): EstadoJogo {
  if (!estado.timestampServidor) return estado;
  const diferenca = Date.now() - estado.timestampServidor;
  return { ...estado, timestampInicio: estado.timestampInicio + diferenca };
}

interface UseSalaWebSocketOptions {
  codigoSala: string;
  login: string;
  nome: string;
  onEstado?: (estado: EstadoJogo) => void;
  onFeedback?: (feedback: FeedbackAluno) => void;
  onErro?: (erro: ErroWS) => void;
}

// Hook principal
// Gerencia toda a conexão WebSocket com STOMP/SockJS para uma sala de jogo.
// Retorna funções para enviar ações (iniciar, responder, próxima...) e o estado de conexão.
export function useSalaWebSocket({ codigoSala, login, nome, onEstado, onFeedback, onErro }: UseSalaWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);
  const [conectado, setConectado] = useState(false);

  // Refs para os callbacks, evitam que o useEffect de conexão precise ser recriado
  // quando os callbacks mudam (problema clássico de closure em hooks com dependências instáveis)
  const onEstadoRef = useRef(onEstado);
  const onFeedbackRef = useRef(onFeedback);
  const onErroRef = useRef(onErro);
  useEffect(() => {
    onEstadoRef.current = onEstado;
  }, [onEstado]);
  useEffect(() => {
    onFeedbackRef.current = onFeedback;
  }, [onFeedback]);
  useEffect(() => {
    onErroRef.current = onErro;
  }, [onErro]);

  // Nome de exibição em ref: ele muda sozinho quando a sessão do usuário é recarregada
  // (o apelido chega depois), e como dependência do efeito de conexão isso derrubava e
  // reabria o WebSocket no meio da partida. O valor só é lido no momento de entrar.
  const nomeRef = useRef(nome);
  useEffect(() => {
    nomeRef.current = nome;
  }, [nome]);

  // Cria e ativa o cliente STOMP ao montar o componente
  useEffect(() => {
    const token = Storage.local.get('jhi-authenticationToken') || Storage.session.get('jhi-authenticationToken');
    const client = new Client({
      webSocketFactory: () => new SockJS('/websocket/sala'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect() {
        setConectado(true);
        // Inscreve no tópico público da sala, recebe o estado do jogo para todos
        client.subscribe(`/topic/sala/${codigoSala}`, (msg: IMessage) => {
          const estado: EstadoJogo = JSON.parse(msg.body);
          onEstadoRef.current?.(corrigirRelogio(estado));
        });
        // Inscreve no canal privado de feedback, só este usuário recebe
        client.subscribe(`/user/queue/sala/${codigoSala}/feedback`, (msg: IMessage) => {
          const feedback: FeedbackAluno = JSON.parse(msg.body);
          onFeedbackRef.current?.(feedback);
        });
        // Inscreve no canal privado de erros (ex: permissão negada ao tentar iniciar)
        client.subscribe(`/user/queue/sala/${codigoSala}/erro`, (msg: IMessage) => {
          const erro: ErroWS = JSON.parse(msg.body);
          onErroRef.current?.(erro);
        });
        // Anuncia entrada na sala para o servidor registrar o participante no placar
        client.publish({
          destination: `/app/sala/${codigoSala}/entrar`,
          body: JSON.stringify({ login, nome: nomeRef.current }),
        });
      },
      onDisconnect: () => setConectado(false),
    });
    client.activate();
    clientRef.current = client;
    // Desconecta ao desmontar o componente (quando o usuário sai da página da sala)
    return () => {
      client.deactivate();
    };
  }, [codigoSala, login]);

  // Publica uma mensagem no servidor. Devolve false quando NÃO foi possível enviar
  // (socket caído ou reconectando): sem esse retorno a resposta do aluno se perdia em
  // silêncio - a tela dava a resposta como enviada, o servidor nunca a recebia e ele
  // terminava a rodada com zero ponto mesmo tendo digitado a palavra certa.
  const publicar = useCallback(
    (destino: string, payload?: unknown): boolean => {
      const client = clientRef.current;
      if (!client?.connected) return false;
      try {
        client.publish({ destination: `/app/sala/${codigoSala}/${destino}`, body: payload ? JSON.stringify(payload) : '' });
        return true;
      } catch {
        return false;
      }
    },
    [codigoSala],
  );

  // Ações disponíveis para o componente que usa este hook
  return {
    conectado,
    iniciar: (payload: {
      tempoFacil: number;
      tempoMedio: number;
      tempoDificil: number;
      qtdFacil: number;
      qtdMedio: number;
      qtdDificil: number;
      palavrasExtrasIds: number[];
      // Palavras pré-sorteadas na criação da sala (vazio = sortear ao iniciar)
      palavrasIds?: number[];
    }) => publicar('iniciar', payload),
    proxima: () => publicar('proxima'),
    pausar: () => publicar('pausar'),
    encerrar: () => publicar('encerrar'),
    // tentativasBurla: nº de inserções bloqueadas (colar, corretor) durante a rodada,
    // vai junto para o servidor marcar a resposta como suspeita
    responder: (respostaDigitada: string, tentativasBurla = 0) => publicar('responder', { respostaDigitada, tentativasBurla }),
  };
}
