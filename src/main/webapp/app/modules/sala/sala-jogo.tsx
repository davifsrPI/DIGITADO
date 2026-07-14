import './sala-jogo-styles.scss';

import React, { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';

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
    gameConfig?: {
      tempoFacil: number;
      tempoMedio: number;
      tempoDificil: number;
      qtdFacil: number;
      qtdMedio: number;
      qtdDificil: number;
      palavrasExtrasIds: number[];
      // Palavras já sorteadas na tela de criação — a 1ª partida usa exatamente essas
      palavrasIds?: number[];
    };
  } | null;
  const gameConfig = locationState?.gameConfig;

  // O papel de professor chega pelo estado de navegação, mas ele se perde ao
  // RECARREGAR a página — sem esta recuperação, o professor caía para sempre na
  // visão de aluno da própria sala. Quando o estado não veio, pergunta ao servidor
  // se o usuário logado é o dono (null = ainda verificando).
  const [souProfessorServidor, setSouProfessorServidor] = useState<boolean | null>(null);
  const papelDesconhecido = locationState?.isProfessor === undefined;
  useEffect(() => {
    if (!papelDesconhecido) return;
    axios
      .get<{ souProfessor: boolean }>(`/api/salas/${codigo}/sou-professor`)
      .then(res => setSouProfessorServidor(res.data.souProfessor))
      .catch(() => setSouProfessorServidor(false));
  }, [codigo, papelDesconhecido]);

  const isProfessor = locationState?.isProfessor === true || souProfessorServidor === true;
  // Ainda não sabemos o papel (reload + resposta do servidor pendente): segura a
  // renderização para não mostrar a visão de aluno ao professor por um instante
  const verificandoPapel = papelDesconhecido && souProfessorServidor === null;

  // Remove o card branco padrão do layout (jh-card) — a página tem fundo escuro próprio
  useEffect(() => {
    document.body.classList.add('sala-jogo-page');
    return () => document.body.classList.remove('sala-jogo-page');
  }, []);

  // Nome de exibição: apelido público primeiro; sem apelido, primeiro nome; por fim o login.
  // O servidor revalida na entrada (o apelido do banco vence o que o cliente enviar).
  const login = account?.login ?? 'anonimo';
  const nome = account?.apelido || (account?.firstName ? `${account.firstName} ${account.lastName ?? ''}`.trim() : login);

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

        {verificandoPapel ? (
          <div style={{ color: 'rgba(255,255,255,0.6)', textAlign: 'center', padding: '60px 0', fontSize: 15 }}>Carregando sala...</div>
        ) : isProfessor ? (
          <SalaJogoProfessor
            estado={estado}
            codigoSala={codigo}
            conectado={conectado}
            onIniciar={iniciar}
            onProxima={proxima}
            onPausar={pausar}
            onEncerrar={encerrar}
            onResponder={responder}
            initialGameConfig={gameConfig}
            // Login do professor: a tela usa para excluí-lo das contagens e do ranking
            meuLogin={login}
          />
        ) : (
          <SalaJogoAluno estado={estado} feedback={feedback} meuLogin={login} onResponder={responder} conectado={conectado} />
        )}
      </div>
    </div>
  );
};

export default SalaJogo;
