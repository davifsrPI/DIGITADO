import './sala-jogo-styles.scss';

import React, { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';

import { useAppSelector } from 'app/config/store';
import { ErroWS, EstadoJogo, FeedbackAluno, RespostaRodada, useSalaWebSocket } from './hooks/useSalaWebSocket';
import { SalaJogoAluno } from './sala-jogo-aluno';
import { SalaJogoProfessor } from './sala-jogo-professor';
import { useBodyClass } from 'app/shared/util/use-body-class';

// Página principal do jogo, decide se renderiza a visão do professor ou do aluno
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
      // Palavras já sorteadas na tela de criação, a 1ª partida usa exatamente essas
      palavrasIds?: number[];
    };
  } | null;
  /**
   * Configuração da partida: quem manda é a que está GRAVADA NA SALA.
   *
   * Antes ela vinha só pelo estado de navegação, com uma cópia no sessionStorage
   * para os duelos: recarregar esta tela perdia a lista de palavras que o professor
   * conferiu uma por uma na criação, e a partida sorteava outras sem avisar. Agora
   * a tela busca no servidor (endpoint restrito ao dono da sala - palavrasIds são
   * as respostas da partida).
   *
   * O estado de navegação continua valendo como atalho para a primeira
   * renderização, até a resposta do servidor chegar e substituí-lo.
   */
  const [configuracaoSalva, setConfiguracaoSalva] = useState<NonNullable<typeof locationState>['gameConfig'] | null>(null);
  const gameConfig = configuracaoSalva ?? locationState?.gameConfig;

  // O papel de professor chega pelo estado de navegação, mas ele se perde ao
  // RECARREGAR a página, sem esta recuperação, o professor caía para sempre na
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

  // Modo da sala: em duelos 1v1 o CRIADOR também joga (recebe a visão de jogador
  // com o botão de iniciar), em vez do painel de professor. Buscado no servidor,
  // o tipo é a fonte da verdade e sobrevive ao reload da página.
  const [modo1v1, setModo1v1] = useState<boolean | null>(null);
  useEffect(() => {
    axios
      .get<{ tipo?: string }>(`/api/salas/${codigo}`)
      .then(res => setModo1v1(res.data?.tipo === 'UM_V_UM'))
      .catch(() => setModo1v1(false));
  }, [codigo]);

  /**
   * O aluno já se identificou NESTA sala? (nome e turma, na tela de entrada)
   *
   * Vale também para quem chegou direto pela URL ou recarregou a página: sem esta
   * checagem, /sala/{codigo} entrava na partida sem nome nem turma, e o professor
   * via no placar um login em vez de um aluno. Quem não se identificou é mandado
   * para o formulário. Professor e duelo 1v1 não passam por ele.
   * null = ainda verificando.
   */
  const papelResolvido = !papelDesconhecido || souProfessorServidor !== null;
  const [identificado, setIdentificado] = useState<boolean | null>(null);
  useEffect(() => {
    if (!papelResolvido || modo1v1 === null) return;
    if (isProfessor || modo1v1) {
      setIdentificado(true);
      return;
    }
    axios
      .get(`/api/salas/${codigo}/identificacao`)
      .then(() => setIdentificado(true))
      .catch(() => {
        setIdentificado(false);
        navigate(`/sala/${codigo}/entrar`, { replace: true });
      });
  }, [codigo, papelResolvido, isProfessor, modo1v1, navigate]);

  // Busca a configuração gravada na sala. Só quem comanda precisa (e só ele tem
  // acesso): é a tela de espera que usa, para iniciar a partida com o que foi
  // escolhido na criação, inclusive depois de recarregar a página.
  useEffect(() => {
    if (!isProfessor) return;
    axios
      .get<NonNullable<typeof locationState>['gameConfig']>(`/api/salas/${codigo}/configuracao`)
      .then(res => setConfiguracaoSalva(res.data))
      .catch(() => {
        // Sem configuração gravada (sala antiga) ou sem acesso: fica o que veio
        // pelo estado de navegação, e a tela cai no padrão dela se não vier nada
      });
  }, [codigo, isProfessor]);

  // Ainda não sabemos o papel (reload + resposta do servidor pendente), o modo da
  // sala ou se o aluno se identificou: segura a renderização para não mostrar a
  // visão errada por um instante
  const verificandoPapel = !papelResolvido || modo1v1 === null || identificado !== true;

  // Remove o card branco padrão do layout (jh-card), a página tem fundo escuro próprio
  useBodyClass('sala-jogo-page');

  // Nome de exibição: apelido público primeiro; sem apelido, primeiro nome; por fim o login.
  // O servidor revalida na entrada (o apelido do banco vence o que o cliente enviar).
  const login = account?.login ?? 'anonimo';
  const nome = account?.apelido || (account?.firstName ? `${account.firstName} ${account.lastName ?? ''}`.trim() : login);

  // Estado local da página
  const [estado, setEstado] = useState<EstadoJogo | null>(null);
  const [feedback, setFeedback] = useState<FeedbackAluno | null>(null);
  const [erroWS, setErroWS] = useState<ErroWS | null>(null);

  // Callbacks para o hook de WebSocket
  // Quando chega um novo estado do jogo, atualiza e limpa o feedback da palavra anterior
  const handleEstado = useCallback((e: EstadoJogo) => {
    setEstado(e);
    if (e.tipo === 'NOVA_PALAVRA') setFeedback(null);
  }, []);

  const handleFeedback = useCallback((f: FeedbackAluno) => {
    setFeedback(f);
  }, []);

  /**
   * Resposta de alguém durante a rodada, no formato leve.
   *
   * Aplica só o STATUS daquele jogador sobre o placar que já está na tela. A
   * pontuação não muda aqui de propósito: durante a rodada ela fica congelada, e o
   * placar novo chega inteiro quando a rodada fecha. Aviso de uma palavra que já
   * passou é descartado, para não sujar a contagem da rodada em andamento.
   */
  const handleRespostaRodada = useCallback((ev: RespostaRodada) => {
    setEstado(anterior => {
      if (!anterior || anterior.indiceAtual !== ev.indiceAtual) return anterior;
      return {
        ...anterior,
        placar: anterior.placar.map(p => (p.login === ev.login ? { ...p, statusAtual: ev.statusAtual } : p)),
      };
    });
  }, []);

  // Mostra o erro por 6 segundos e depois esconde automaticamente
  const handleErro = useCallback((e: ErroWS) => {
    setErroWS(e);
    setTimeout(() => setErroWS(null), 6000);
  }, []);

  // Conexão WebSocket. Só abre depois que a tela sabe quem é quem: o "entrar" que
  // o hook publica ao conectar é o que registra o participante na sala, e ele não
  // pode acontecer enquanto o aluno ainda está a caminho do formulário de entrada.
  const { conectado, iniciar, proxima, pausar, encerrar, responder, pedirEstado } = useSalaWebSocket({
    codigoSala: codigo,
    login,
    nome,
    habilitado: identificado === true,
    onEstado: handleEstado,
    onFeedback: handleFeedback,
    onErro: handleErro,
    onRespostaRodada: handleRespostaRodada,
  });

  return (
    <div className="sj-wrapper">
      {/* Brilhos de fundo: agora são gradientes pintados no próprio .sj-bg, sem as
          duas divs desfocadas que faziam a partida engasgar no celular */}
      <div className="sj-bg" />

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
        ) : isProfessor && modo1v1 ? (
          // APENAS no duelo 1v1 o criador joga junto: recebe a mesma tela do
          // jogador (áudio + digitação), acrescida do botão de iniciar o duelo.
          // O avanço das rodadas NÃO é mais dele: quem vira a palavra é o relógio
          // do servidor, igual para os dois jogadores
          <SalaJogoAluno
            estado={estado}
            feedback={feedback}
            meuLogin={login}
            onResponder={responder}
            conectado={conectado}
            duelo1v1
            criadorDuelo
            codigoSala={codigo}
            onIniciar={iniciar}
            onPedirEstado={pedirEstado}
            initialGameConfig={gameConfig}
          />
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
            onPedirEstado={pedirEstado}
            initialGameConfig={gameConfig}
            // Login do professor: a tela usa para excluí-lo das contagens e do ranking
            meuLogin={login}
          />
        ) : (
          // duelo1v1 também para o OPONENTE: no duelo, quando os dois respondem, a
          // tela de correção aparece na hora, sem esperar o tempo esgotar
          <SalaJogoAluno
            estado={estado}
            feedback={feedback}
            meuLogin={login}
            onResponder={responder}
            conectado={conectado}
            duelo1v1={modo1v1 === true}
            onPedirEstado={pedirEstado}
            // A tela busca com ele o resumo pessoal quando a partida encerra
            codigoSala={codigo}
          />
        )}
      </div>
    </div>
  );
};

export default SalaJogo;
