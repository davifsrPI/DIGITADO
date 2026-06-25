import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Storage } from 'react-jhipster';

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

export interface FeedbackAluno {
  correta: boolean;
  pontos: number;
  ordem: number;
  tipoErro?: string;
  textoCorreto: string;
}

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

export function useSalaWebSocket({ codigoSala, login, nome, onEstado, onFeedback, onErro }: UseSalaWebSocketOptions) {
  const clientRef = useRef<Client | null>(null);
  const [conectado, setConectado] = useState(false);

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

  useEffect(() => {
    const token = Storage.local.get('jhi-authenticationToken') || Storage.session.get('jhi-authenticationToken');
    const client = new Client({
      webSocketFactory: () => new SockJS('/websocket/sala'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect() {
        setConectado(true);
        client.subscribe(`/topic/sala/${codigoSala}`, (msg: IMessage) => {
          const estado: EstadoJogo = JSON.parse(msg.body);
          onEstadoRef.current?.(estado);
        });
        client.subscribe(`/user/queue/sala/${codigoSala}/feedback`, (msg: IMessage) => {
          const feedback: FeedbackAluno = JSON.parse(msg.body);
          onFeedbackRef.current?.(feedback);
        });
        client.subscribe(`/user/queue/sala/${codigoSala}/erro`, (msg: IMessage) => {
          const erro: ErroWS = JSON.parse(msg.body);
          onErroRef.current?.(erro);
        });
        client.publish({
          destination: `/app/sala/${codigoSala}/entrar`,
          body: JSON.stringify({ login, nome }),
        });
      },
      onDisconnect: () => setConectado(false),
    });
    client.activate();
    clientRef.current = client;
    return () => {
      client.deactivate();
    };
  }, [codigoSala, login, nome]);

  const publicar = useCallback(
    (destino: string, payload?: unknown) => {
      clientRef.current?.publish({ destination: `/app/sala/${codigoSala}/${destino}`, body: payload ? JSON.stringify(payload) : '' });
    },
    [codigoSala],
  );

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
