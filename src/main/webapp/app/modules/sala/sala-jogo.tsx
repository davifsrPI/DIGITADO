import './sala-jogo-styles.scss';

import React, { useCallback, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { ErroWS, EstadoJogo, FeedbackAluno, useSalaWebSocket } from './hooks/useSalaWebSocket';
import { SalaJogoAluno } from './sala-jogo-aluno';
import { SalaJogoProfessor } from './sala-jogo-professor';

export const SalaJogo: React.FC = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const account = useAppSelector(state => state.authentication.account);
  const locationState = location.state as {
    isProfessor?: boolean;
    autoStart?: boolean;
    gameConfig?: { tempoLimite: number; qtdFacil: number; qtdMedio: number; qtdDificil: number; palavrasExtrasIds: number[] };
  } | null;
  const isProfessor = locationState?.isProfessor === true;
  const autoStart = locationState?.autoStart === true;
  const gameConfig = locationState?.gameConfig;

  const login = account?.login ?? 'anonimo';
  const nome = account?.firstName ? `${account.firstName} ${account.lastName ?? ''}`.trim() : login;

  const [estado, setEstado] = useState<EstadoJogo | null>(null);
  const [feedback, setFeedback] = useState<FeedbackAluno | null>(null);
  const [erroWS, setErroWS] = useState<ErroWS | null>(null);

  const handleEstado = useCallback((e: EstadoJogo) => {
    setEstado(e);
    if (e.tipo === 'NOVA_PALAVRA') setFeedback(null);
  }, []);

  const handleFeedback = useCallback((f: FeedbackAluno) => {
    setFeedback(f);
  }, []);

  const handleErro = useCallback((e: ErroWS) => {
    setErroWS(e);
    setTimeout(() => setErroWS(null), 6000);
  }, []);

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
