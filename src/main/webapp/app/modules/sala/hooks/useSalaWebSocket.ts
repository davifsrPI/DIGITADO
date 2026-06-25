import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Storage } from 'react-jhipster';

// ─── Tipos que trafegam pelo WebSocket ───────────────────────────────────────

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
  timestampInicio: number;
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
}

// Erro enviado pelo servidor via WebSocket (ex: tentativa não autorizada de iniciar)
export interface ErroWS {
  tipo: string;
  mensagem: string;
}

interface UseSalaWebSocketOptions {
  codigoSala: string;
  login: string;
  nome: string;
  onEstado?: (estado: EstadoJogo) => void;
  onFeedback?: (feedback: FeedbackAluno) => void;
  onErro?: (erro: ErroWS) => void;
}

// ─── Hook principal ───────────────────────────────────────────────────────────
// Gerencia toda a conexão WebSocket com STOMP/SockJS para uma sala de jogo.
// Retorna funções para enviar ações (iniciar, responder, próxima...) e o estado de conexão.
export function useSalaWebSocket({ codigoSala, login, nome, onEstado, onFeedback, onErro }: UseSalaWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);
  const [conectado, setConectado] = useState(false);

  // Refs para os callbacks — evitam que o useEffect de conexão precise ser recriado
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

  // ─── Cria e ativa o cliente STOMP ao montar o componente ────────────────────
  useEffect(() => {
    const token = Storage.local.get('jhi-authenticationToken') || Storage.session.get('jhi-authenticationToken');
    const client = new Client({
      webSocketFactory: () => new SockJS('/websocket/sala'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect() {
        setConectado(true);
        // Inscreve no tópico público da sala — recebe o estado do jogo para todos
        client.subscribe(`/topic/sala/${codigoSala}`, (msg: IMessage) => {
          const estado: EstadoJogo = JSON.parse(msg.body);
          onEstadoRef.current?.(estado);
        });
        // Inscreve no canal privado de feedback — só este usuário recebe
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
          body: JSON.stringify({ login, nome }),
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
  }, [codigoSala, login, nome]);

  // ─── Função auxiliar para publicar mensagens no servidor ─────────────────────
  const publicar = useCallback(
    (destino: string, payload?: unknown) => {
      clientRef.current?.publish({ destination: `/app/sala/${codigoSala}/${destino}`, body: payload ? JSON.stringify(payload) : '' });
    },
    [codigoSala],
  );

  // ─── Ações disponíveis para o componente que usa este hook ──────────────────
  return {
    conectado,
    iniciar: (payload: { tempoLimite: number; qtdFacil: number; qtdMedio: number; qtdDificil: number; palavrasExtrasIds: number[] }) =>
      publicar('iniciar', payload),
    proxima: () => publicar('proxima'),
    pausar: () => publicar('pausar'),
    encerrar: () => publicar('encerrar'),
    responder: (respostaDigitada: string) => publicar('responder', { respostaDigitada }),
  };
}
