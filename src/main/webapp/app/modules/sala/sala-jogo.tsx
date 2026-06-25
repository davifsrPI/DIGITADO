import './sala-jogo-styles.scss';

import React, { useCallback, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { ErroWS, EstadoJogo, FeedbackAluno, useSalaWebSocket } from './hooks/useSalaWebSocket';
import { SalaJogoAluno } from './sala-jogo-aluno';
import { SalaJogoProfessor } from './sala-jogo-professor';

// Página principal do jogo — decide se renderiza a visão do professor ou do aluno
// com base no estado de navegação passado pela tela de criação/entrada na sala
export const SalaJogo: React.FC = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const account = useAppSelector(state => state.authentication.account);
  // Lê as configurações passadas pela tela anterior via React Router state
  const locationState = location.state as {
    isProfessor?: boolean;
    autoStart?: boolean;
    gameConfig?: { tempoLimite: number; qtdFacil: number; qtdMedio: number; qtdDificil: number; palavrasExtrasIds: number[] };
  } | null;
  const isProfessor = locationState?.isProfessor === true;
  const autoStart = locationState?.autoStart === true;
  const gameConfig = locationState?.gameConfig;

  // Nome de exibição: usa o primeiro nome do usuário autenticado, ou o login como fallback
  const login = account?.login ?? 'anonimo';
  const nome = account?.firstName ? `${account.firstName} ${account.lastName ?? ''}`.trim() : login;

  // ─── Estado local da página ───────────────────────────────────────────────
  const [estado, setEstado] = useState<EstadoJogo | null>(null);
  const [feedback, setFeedback] = useState<FeedbackAluno | null>(null);
  const [erroWS, setErroWS] = useState<ErroWS | null>(null);

  // ─── Callbacks para o hook de WebSocket ──────────────────────────────────
  // Quando chega um novo estado do jogo, atualiza e limpa o feedback da palavra anterior
  const handleEstado = useCallback((e: EstadoJogo) => {
    setEstado(e);
    if (e.tipo === 'NOVA_PALAVRA') setFeedback(null);
  }, []);

  const handleFeedback = useCallback((f: FeedbackAluno) => {
    setFeedback(f);
  }, []);

  // Mostra o erro por 6 segundos e depois esconde automaticamente
  const handleErro = useCallback((e: ErroWS) => {
    setErroWS(e);
    setTimeout(() => setErroWS(null), 6000);
  }, []);

  // ─── Conexão WebSocket ────────────────────────────────────────────────────
  const { conectado, iniciar, proxima, pausar, encerrar, responder } = useSalaWebSocket({
    codigoSala: codigo,
    login,
    nome,
    onEstado: handleEstado,
    onFeedback: handleFeedback,
    onErro: handleErro,
  });

  return (
    <div className="sj-wrapper">
      <div className="sj-bg">
        <div className="sj-shape sj-shape-one" />
        <div className="sj-shape sj-shape-two" />
      </div>

      <div className="sj-container">
        <button className="sj-back-btn" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        {erroWS && (
          <div style={{ background: '#7f1d1d', color: '#fecaca', padding: '10px 16px', borderRadius: 8, marginBottom: 12, fontSize: 14 }}>
            ⚠ {erroWS.mensagem}
          </div>
        )}

        {isProfessor ? (
          <SalaJogoProfessor
            estado={estado}
            codigoSala={codigo}
            conectado={conectado}
            onIniciar={iniciar}
            onProxima={proxima}
            onPausar={pausar}
            onEncerrar={encerrar}
            onResponder={responder}
            autoStart={autoStart}
            initialGameConfig={gameConfig}
          />
        ) : (
          <SalaJogoAluno estado={estado} feedback={feedback} meuLogin={login} onResponder={responder} conectado={conectado} />
        )}
      </div>
    </div>
  );
};

export default SalaJogo;
