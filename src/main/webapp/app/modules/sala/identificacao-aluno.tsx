import './identificacao-aluno.scss';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';

import { useAppSelector } from 'app/config/store';
import { useBodyClass } from 'app/shared/util/use-body-class';

// O que o aluno grava em GET/PUT /api/salas/{codigo}/identificacao
interface Identificacao {
  nome: string;
  turma: string;
  usarApelido: boolean;
  apelido?: string | null;
}

const MAX_NOME = 60;
const MAX_TURMA = 30;
const MAX_APELIDO = 30;

/**
 * Tela de entrada na sala: quem digitou o código para aqui antes de jogar.
 *
 * O placar mostrava o nome da CONTA, que numa sala de aula não diz nada ao
 * professor - ele precisa saber o nome do aluno e de que turma ele é. Os três
 * campos são obrigatórios (o servidor recusa em branco, não só o formulário).
 *
 * O terceiro campo é a escolha de como aparecer para os COLEGAS: o nome ou um
 * apelido. Escolhendo o apelido, é ele que vai para o placar, o ranking e o
 * pódio - o professor continua vendo o nome verdadeiro, com o apelido entre
 * parênteses.
 *
 * Não vale para duelos 1v1 (não são turma) nem para o dono da sala: os dois
 * seguem direto para a partida.
 */
export const IdentificacaoAluno: React.FC = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();
  const account = useAppSelector(state => state.authentication.account);

  useBodyClass('identificacao-page');

  // Nome sugerido: o cadastro da conta, que o aluno pode corrigir
  const nomeDaConta = useMemo(
    () => [account?.firstName, account?.lastName].filter(Boolean).join(' ').trim(),
    [account?.firstName, account?.lastName],
  );

  const [nome, setNome] = useState('');
  const [turma, setTurma] = useState('');
  const [usarApelido, setUsarApelido] = useState(false);
  const [apelido, setApelido] = useState('');
  const [nomeSala, setNomeSala] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  // Só valida os campos depois da primeira tentativa de enviar: marcar tudo de
  // vermelho antes de a pessoa digitar qualquer coisa é hostil
  const [tentouEnviar, setTentouEnviar] = useState(false);
  const apelidoRef = useRef<HTMLInputElement>(null);

  // Quem NÃO passa por esta tela: o dono da sala (comanda, não joga) e o duelo
  // 1v1 (não é turma). Os dois vão direto para a partida.
  useEffect(() => {
    let cancelado = false;
    Promise.all([
      axios.get<{ souProfessor: boolean }>(`/api/salas/${codigo}/sou-professor`),
      axios.get<{ nome?: string; tipo?: string }>(`/api/salas/${codigo}`),
      // 404 aqui é o caso normal: primeira vez do aluno nesta sala
      axios.get<Identificacao>(`/api/salas/${codigo}/identificacao`).catch(() => null),
    ])
      .then(([papel, sala, identificacao]) => {
        if (cancelado) return;
        if (papel.data.souProfessor || sala.data?.tipo === 'UM_V_UM') {
          navigate(`/sala/${codigo}`, { replace: true });
          return;
        }
        setNomeSala(sala.data?.nome ?? null);
        // Já entrou nesta sala antes: reabre com o que ele preencheu, para poder
        // corrigir a turma ou trocar de apelido sem começar do zero
        const anterior = identificacao?.data;
        setNome(anterior?.nome ?? nomeDaConta);
        setTurma(anterior?.turma ?? '');
        setUsarApelido(anterior?.usarApelido ?? false);
        setApelido(anterior?.apelido ?? account?.apelido ?? '');
        setCarregando(false);
      })
      .catch(err => {
        if (cancelado) return;
        // 400 é o que o servidor responde para código que não existe. Qualquer
        // outra falha (rede, servidor fora) não pode acusar o código do aluno de
        // errado - ele ficaria conferindo uma letra por uma sem motivo.
        setErro(
          err?.response?.status === 400
            ? 'Não encontramos uma sala com esse código. Confira com o seu professor.'
            : 'Não foi possível abrir a sala agora. Verifique a conexão e tente de novo.',
        );
        setCarregando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [codigo]);

  // Ao trocar para apelido, leva o cursor para o campo: o aluno acabou de dizer
  // que quer um apelido, o passo seguinte é escrevê-lo
  useEffect(() => {
    if (usarApelido) apelidoRef.current?.focus();
  }, [usarApelido]);

  const nomeOk = nome.trim().length > 0;
  const turmaOk = turma.trim().length > 0;
  const apelidoOk = !usarApelido || apelido.trim().length > 0;
  const podeEnviar = nomeOk && turmaOk && apelidoOk;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setTentouEnviar(true);
    if (!podeEnviar || salvando) return;
    setSalvando(true);
    setErro(null);
    try {
      await axios.put<Identificacao>(`/api/salas/${codigo}/identificacao`, {
        nome: nome.trim(),
        turma: turma.trim(),
        usarApelido,
        apelido: usarApelido ? apelido.trim() : null,
      });
      // replace: o botão "voltar" do navegador não devolve o aluno ao formulário
      // que ele acabou de preencher
      navigate(`/sala/${codigo}`, { replace: true });
    } catch {
      setSalvando(false);
      setErro('Não foi possível entrar na sala agora. Tente de novo.');
    }
  };

  if (carregando) {
    return (
      <div className="ident-wrapper">
        <div className="ident-bg" />
        <div className="ident-carregando">Abrindo a sala...</div>
      </div>
    );
  }

  return (
    <div className="ident-wrapper">
      <div className="ident-bg" />

      <div className="ident-content">
        <button type="button" className="ident-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <form className="ident-card" onSubmit={handleSubmit} noValidate>
          <div className="ident-header">
            <span className="ident-codigo-pill">SALA {codigo}</span>
            <h1 className="ident-titulo">{nomeSala ?? 'Quase lá!'}</h1>
            <p className="ident-sub">Antes de começar, conte quem é você</p>
          </div>

          {erro && <div className="ident-erro">⚠ {erro}</div>}

          <label className="ident-campo">
            <span className="ident-label">
              Seu nome <span className="ident-obrig">*</span>
            </span>
            <input
              type="text"
              className={`ident-input${tentouEnviar && !nomeOk ? ' ident-input--erro' : ''}`}
              value={nome}
              onChange={e => setNome(e.target.value)}
              maxLength={MAX_NOME}
              placeholder="Como o seu professor te chama"
              autoComplete="name"
            />
            {tentouEnviar && !nomeOk && <span className="ident-msg-erro">Preencha o seu nome para entrar</span>}
          </label>

          <label className="ident-campo">
            <span className="ident-label">
              Sua turma <span className="ident-obrig">*</span>
            </span>
            <input
              type="text"
              className={`ident-input${tentouEnviar && !turmaOk ? ' ident-input--erro' : ''}`}
              value={turma}
              onChange={e => setTurma(e.target.value)}
              maxLength={MAX_TURMA}
              placeholder="Ex: 6º ano B"
              autoComplete="off"
            />
            {tentouEnviar && !turmaOk && <span className="ident-msg-erro">Preencha a sua turma para entrar</span>}
          </label>

          <div className="ident-campo">
            <span className="ident-label">Como você quer aparecer para a turma</span>
            {/* Chave tic-tac: o botão inteiro alterna e a pastilha corre de um lado
                para o outro. role="switch" para o leitor de tela anunciar o estado,
                em vez de um botão sem significado. */}
            <button
              type="button"
              role="switch"
              aria-checked={usarApelido}
              className={`ident-switch${usarApelido ? ' ident-switch--apelido' : ''}`}
              onClick={() => setUsarApelido(v => !v)}
            >
              <span className="ident-switch-pastilha" aria-hidden="true" />
              <span className="ident-switch-opcao">Meu nome</span>
              <span className="ident-switch-opcao">Um apelido</span>
            </button>

            {usarApelido ? (
              <>
                <input
                  ref={apelidoRef}
                  type="text"
                  className={`ident-input ident-input--apelido${tentouEnviar && !apelidoOk ? ' ident-input--erro' : ''}`}
                  value={apelido}
                  onChange={e => setApelido(e.target.value)}
                  maxLength={MAX_APELIDO}
                  placeholder="O apelido que a turma vai ver"
                  autoComplete="off"
                />
                {tentouEnviar && !apelidoOk && <span className="ident-msg-erro">Escreva o apelido ou volte para o seu nome</span>}
                <span className="ident-dica">Só a turma vê o apelido. O seu professor continua vendo o seu nome de verdade.</span>
              </>
            ) : (
              <span className="ident-dica">A turma vai te ver como {nome.trim() || 'o seu nome'} no placar e no pódio.</span>
            )}
          </div>

          <button type="submit" className="ident-entrar-btn" disabled={salvando}>
            {salvando ? 'Entrando...' : 'Entrar na sala →'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default IdentificacaoAluno;
