import './sala-jogo-styles.scss';

import React, { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { TipoUsuario } from 'app/shared/model/enumerations/tipo-usuario.model';
import { EstadoJogo, FeedbackAluno, useSalaWebSocket } from './hooks/useSalaWebSocket';
import { SalaJogoAluno } from './sala-jogo-aluno';
import { SalaJogoProfessor } from './sala-jogo-professor';

const TIPO_KEY = 'digitado-tipo-usuario';

export const SalaJogo: React.FC = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();
  const account = useAppSelector(state => state.authentication.account);
  const tipoUsuario = (localStorage.getItem(TIPO_KEY) as TipoUsuario) ?? TipoUsuario.ALUNO;
  const isProfessor = tipoUsuario === TipoUsuario.PROFESSOR;

  const login = account?.login ?? 'anonimo';
  const nome = account?.firstName ? `${account.firstName} ${account.lastName ?? ''}`.trim() : login;

  const [estado, setEstado] = useState<EstadoJogo | null>(null);
  const [feedback, setFeedback] = useState<FeedbackAluno | null>(null);

  const handleEstado = useCallback((e: EstadoJogo) => {
    setEstado(e);
    if (e.tipo === 'NOVA_PALAVRA') setFeedback(null);
  }, []);

  const handleFeedback = useCallback((f: FeedbackAluno) => {
    setFeedback(f);
  }, []);

  const { conectado, iniciar, proxima, pausar, encerrar, responder } = useSalaWebSocket({
    codigoSala: codigo,
    login,
    nome,
    onEstado: handleEstado,
    onFeedback: handleFeedback,
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

        {isProfessor ? (
          <SalaJogoProfessor
            estado={estado}
            codigoSala={codigo}
            conectado={conectado}
            onIniciar={iniciar}
            onProxima={proxima}
            onPausar={pausar}
            onEncerrar={encerrar}
          />
        ) : (
          <SalaJogoAluno estado={estado} feedback={feedback} meuLogin={login} onResponder={responder} conectado={conectado} />
        )}
      </div>
    </div>
  );
};

export default SalaJogo;
