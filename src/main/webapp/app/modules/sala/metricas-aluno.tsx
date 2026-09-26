import './metricas-aluno.scss';

import React, { useEffect, useState } from 'react';
import axios from 'axios';

import { PosicaoRanking } from './relatorio-partida';
import { ResumoAluno, ResumoAlunoPartida } from './resumo-aluno';

interface Props {
  // Opcional só porque quem vem da URL (useParams) entrega string | undefined
  codigoSala?: string;
  // Ranking da partida - é dele que sai a lista de alunos para escolher
  posicoes: PosicaoRanking[];
  // login → nome verdadeiro (+ apelido entre parênteses), de useNomesParticipantes
  nomes?: Record<string, string>;
  // login → turma do aluno
  turmas?: Record<string, string>;
}

/**
 * Métrica de CADA aluno, na tela do professor: ele escolhe um nome e vê o
 * mesmo resumo que o aluno viu no fim da partida - palavras certas, erradas, a
 * média da turma e quanto da turma errou cada palavra.
 *
 * O resumo vem de GET /api/salas/{codigo}/resumo/{login}, restrito ao dono da
 * sala. Funciona também nas partidas antigas: quando o jogo já saiu da memória,
 * o servidor monta o resumo a partir do snapshot gravado no banco.
 */
export const MetricasPorAluno: React.FC<Props> = ({ codigoSala, posicoes, nomes, turmas }) => {
  const [selecionado, setSelecionado] = useState<string | null>(null);
  const [resumo, setResumo] = useState<ResumoAluno | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!selecionado || !codigoSala) {
      setResumo(null);
      return;
    }
    let cancelado = false;
    setCarregando(true);
    setErro(null);
    axios
      .get<ResumoAluno>(`/api/salas/${codigoSala}/resumo/${encodeURIComponent(selecionado)}`)
      .then(res => {
        if (cancelado) return;
        setResumo(res.data);
      })
      .catch(err => {
        if (cancelado) return;
        setResumo(null);
        // 404: o aluno está no ranking mas não respondeu nenhuma palavra
        setErro(
          err?.response?.status === 404
            ? 'Este aluno não respondeu nenhuma palavra nesta partida.'
            : 'Não foi possível carregar o resumo deste aluno.',
        );
      })
      .finally(() => {
        if (!cancelado) setCarregando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [codigoSala, selecionado]);

  if (!codigoSala || posicoes.length === 0) {
    return <p className="sj-no-alunos">Nenhum aluno participou desta partida.</p>;
  }

  return (
    <div className="ma-wrapper">
      <p className="ma-instrucao">Toque no nome de um aluno para ver o resumo dele.</p>

      <div className="ma-lista">
        {posicoes.map(p => {
          const aberto = selecionado === p.login;
          return (
            <button
              key={p.login}
              type="button"
              className={`ma-chip${aberto ? ' ma-chip--ativo' : ''}`}
              aria-pressed={aberto}
              // Segundo toque no mesmo nome fecha o resumo
              onClick={() => setSelecionado(aberto ? null : p.login)}
            >
              <span className="ma-chip-nome">{nomes?.[p.login] ?? p.nome ?? p.login}</span>
              {turmas?.[p.login] && <span className="ma-chip-turma">{turmas[p.login]}</span>}
              <span className="ma-chip-pts">{p.pontos} pts</span>
            </button>
          );
        })}
      </div>

      {selecionado &&
        (carregando ? (
          <p className="ma-status">Carregando o resumo...</p>
        ) : erro ? (
          <p className="ma-status">{erro}</p>
        ) : (
          resumo && (
            <div className="ma-resumo">
              <ResumoAlunoPartida resumo={resumo} titulo={nomes?.[selecionado] ?? resumo.nome} />
            </div>
          )
        ))}
    </div>
  );
};

export default MetricasPorAluno;
