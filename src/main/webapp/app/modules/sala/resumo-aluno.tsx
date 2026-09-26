import './resumo-aluno.scss';

import React from 'react';
import { CORES_DIFICULDADE, LABELS_DIFICULDADE } from 'app/shared/util/dificuldade-constants';

// Espelho dos records ResumoPartidaService.PalavraResumo / ResumoAluno,
// servidos por GET /api/salas/{codigo}/meu-resumo (o próprio aluno) e
// GET /api/salas/{codigo}/resumo/{login} (o professor, aluno por aluno).

export interface PalavraResumo {
  indice: number;
  texto: string;
  dificuldade: string | null;
  // O que o aluno escreveu; null quando o tempo acabou sem resposta
  respostaDigitada: string | null;
  respondeu: boolean;
  acertou: boolean;
  totalRespostas: number;
  totalErros: number;
  // % de quem respondeu a palavra e errou
  pctErro: number;
}

export interface ResumoAluno {
  login: string;
  nome: string;
  totalPalavras: number;
  acertos: number;
  erros: number;
  semResposta: number;
  mediaAcertosTurma: number;
  totalParticipantes: number;
  palavras: PalavraResumo[];
}

const COR_DIFICULDADE: Record<string, string> = CORES_DIFICULDADE;
const LABEL_DIFICULDADE: Record<string, string> = LABELS_DIFICULDADE;

// Número no padrão brasileiro com uma casa (a média sai "7,3")
const umaCasa = (n: number): string => n.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

// Uma palavra na lista: o texto, a dificuldade, o que o aluno escreveu quando
// errou e quanta gente da turma tropeçou nela
const LinhaPalavra: React.FC<{ palavra: PalavraResumo }> = ({ palavra }) => (
  <li className={`ra-palavra ra-palavra--${palavra.acertou ? 'ok' : 'erro'}`}>
    <span className="ra-palavra-icone">{palavra.acertou ? '✓' : '✗'}</span>
    <div className="ra-palavra-corpo">
      <div className="ra-palavra-topo">
        <span className="ra-palavra-texto">{palavra.texto}</span>
        {palavra.dificuldade && (
          <span className="ra-palavra-dif" style={{ color: COR_DIFICULDADE[palavra.dificuldade] }}>
            {LABEL_DIFICULDADE[palavra.dificuldade] ?? palavra.dificuldade}
          </span>
        )}
      </div>
      {!palavra.acertou &&
        (palavra.respondeu ? (
          <span className="ra-palavra-escrita">
            você escreveu <strong>&ldquo;{palavra.respostaDigitada}&rdquo;</strong>
          </span>
        ) : (
          <span className="ra-palavra-escrita">você não respondeu esta palavra</span>
        ))}
      {/* Quanta gente errou: tira o peso do erro individual quando a palavra
          derrubou a turma inteira, e mostra o contrário quando só ele errou */}
      <span className="ra-palavra-turma">
        {palavra.totalRespostas === 0
          ? 'ninguém respondeu esta palavra'
          : `${palavra.pctErro}% da turma errou (${palavra.totalErros} de ${palavra.totalRespostas})`}
      </span>
    </div>
  </li>
);

/**
 * Resumo pessoal da partida: o que o aluno acertou, o que errou, quantos
 * acertos fez e como isso se compara com a média da turma.
 *
 * Renderizado em dois lugares, sempre igual: na tela de encerramento do ALUNO
 * (ele vê só o dele) e na tela do PROFESSOR, quando ele abre a métrica de um
 * aluno pelo ranking da partida. Nenhum aluno vê o resumo de outro - o servidor
 * só devolve o próprio, e o de terceiros é restrito ao dono da sala.
 */
export const ResumoAlunoPartida: React.FC<{ resumo: ResumoAluno; titulo?: string }> = ({ resumo, titulo }) => {
  const acertadas = resumo.palavras.filter(p => p.acertou);
  const erradas = resumo.palavras.filter(p => !p.acertou);
  // Acima, abaixo ou em cima da média da turma - é a leitura que o aluno faz primeiro
  const diferenca = resumo.acertos - resumo.mediaAcertosTurma;
  const comparacao =
    Math.abs(diferenca) < 0.05
      ? 'exatamente na média da turma'
      : diferenca > 0
        ? `${umaCasa(diferenca)} acerto(s) acima da média da turma`
        : `${umaCasa(Math.abs(diferenca))} acerto(s) abaixo da média da turma`;
  const pctAcerto = resumo.totalPalavras > 0 ? Math.round((resumo.acertos / resumo.totalPalavras) * 100) : 0;

  return (
    <div className="ra-wrapper">
      <div className="ra-cabecalho">
        <span className="ra-cabecalho-nome">{titulo ?? resumo.nome}</span>
        <span className="ra-cabecalho-sub">
          {resumo.acertos} de {resumo.totalPalavras} palavra{resumo.totalPalavras === 1 ? '' : 's'} · {pctAcerto}% de acerto
        </span>
      </div>

      <div className="ra-numeros">
        <div className="ra-numero ra-numero--ok">
          <span className="ra-numero-val">{resumo.acertos}</span>
          <span className="ra-numero-label">acertos</span>
        </div>
        <div className="ra-numero ra-numero--erro">
          <span className="ra-numero-val">{resumo.erros}</span>
          <span className="ra-numero-label">erros</span>
        </div>
        {resumo.semResposta > 0 && (
          <div className="ra-numero ra-numero--vazio">
            <span className="ra-numero-val">{resumo.semResposta}</span>
            <span className="ra-numero-label">sem resposta</span>
          </div>
        )}
        <div className="ra-numero ra-numero--turma">
          <span className="ra-numero-val">{umaCasa(resumo.mediaAcertosTurma)}</span>
          <span className="ra-numero-label">média da turma</span>
        </div>
      </div>

      <p className="ra-comparacao">
        Você ficou {comparacao} ({resumo.totalParticipantes} participante{resumo.totalParticipantes === 1 ? '' : 's'}).
      </p>

      <div className="ra-listas">
        <div className="ra-lista">
          <h4 className="ra-lista-titulo ra-lista-titulo--ok">Você acertou ({acertadas.length})</h4>
          {acertadas.length === 0 ? (
            <p className="ra-lista-vazia">Nenhuma palavra acertada nesta partida.</p>
          ) : (
            <ul className="ra-palavras">
              {acertadas.map(p => (
                <LinhaPalavra key={p.indice} palavra={p} />
              ))}
            </ul>
          )}
        </div>

        <div className="ra-lista">
          <h4 className="ra-lista-titulo ra-lista-titulo--erro">Você errou ({erradas.length})</h4>
          {erradas.length === 0 ? (
            <p className="ra-lista-vazia">Nenhum erro nesta partida. 🎉</p>
          ) : (
            <ul className="ra-palavras">
              {erradas.map(p => (
                <LinhaPalavra key={p.indice} palavra={p} />
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
};

export default ResumoAlunoPartida;
