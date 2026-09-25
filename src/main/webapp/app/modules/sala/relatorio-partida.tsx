import React from 'react';
import { CORES_DIFICULDADE, LABELS_DIFICULDADE } from 'app/shared/util/dificuldade-constants';
import { posicoesRanking } from './utils/posicoes-ranking';

// Blocos do desempenho da turma numa partida: o ranking final e o relatório
// "quem escreveu o quê" por palavra.
//
// Renderizados em dois lugares com o MESMO visual: na tela de encerramento do
// professor (dados ao vivo, vindos do WebSocket + /relatorio) e na tela
// "Ver estatísticas" (snapshot gravado no banco, via /estatisticas). Ficam aqui
// para as duas telas não saírem do lugar uma da outra.

// Uma resposta individual: quem respondeu, o texto exato digitado e o resultado
export interface RespostaDetalhe {
  login: string;
  nome: string;
  texto: string;
  correta: boolean;
  ordem: number;
}

// Consolidado de uma palavra da partida (o % de acerto é calculado aqui no front)
export interface RelatorioPalavra {
  indice: number;
  texto: string;
  dificuldade: string | null;
  totalRespostas: number;
  totalAcertos: number;
  respostas: RespostaDetalhe[];
}

// Uma posição do ranking, serve tanto para o placar ao vivo (que traz também o
// status da rodada, ignorado aqui) quanto para o ranking gravado no snapshot
export interface PosicaoRanking {
  login: string;
  nome: string;
  pontos: number;
  alertas: number;
}

// Cores/rótulos por dificuldade, paleta compartilhada de todas as telas
const COR_DIFICULDADE: Record<string, string> = CORES_DIFICULDADE;
const LABEL_DIFICULDADE: Record<string, string> = LABELS_DIFICULDADE;

// Ranking completo da partida, do primeiro ao último colocado
export const RankingPartida: React.FC<{ posicoes: PosicaoRanking[] }> = ({ posicoes }) => {
  if (posicoes.length === 0) {
    return <p className="sj-no-alunos">Nenhum aluno participou desta partida.</p>;
  }
  // Empate divide a posição (1º, 2º, 2º, 4º) em vez de numerar pelo índice da lista
  const lugares = posicoesRanking(posicoes.map(p => p.pontos));
  return (
    <div className="sj-final-placar">
      {posicoes.map((p, i) => (
        <div key={p.login} className="sj-final-row">
          <span className="sj-final-rank">{lugares[i]}º</span>
          <span className="sj-final-nome">
            {p.nome || p.login}
            {p.alertas > 0 && (
              <span className="sj-alerta-burla" title={`${p.alertas} resposta(s) suspeita(s) de colar/corretor nesta partida`}>
                ⚠ {p.alertas}
              </span>
            )}
          </span>
          <span className="sj-final-pts">{p.pontos} pts</span>
        </div>
      ))}
    </div>
  );
};

// Uma palavra por card, com a resposta LITERAL de cada aluno
export const RelatorioPorPalavra: React.FC<{ relatorio: RelatorioPalavra[] }> = ({ relatorio }) => {
  if (relatorio.length === 0) {
    return <p className="sj-no-alunos">Sem respostas registradas nesta partida.</p>;
  }
  return (
    <div className="sj-rel-lista">
      {relatorio.map(r => {
        const pctAcerto = r.totalRespostas > 0 ? Math.round((r.totalAcertos / r.totalRespostas) * 100) : 0;
        return (
          <div key={r.indice} className="sj-rel-card">
            <div className="sj-rel-header">
              <span className="sj-rel-num">{r.indice + 1}</span>
              <span className="sj-rel-palavra">{r.texto}</span>
              {r.dificuldade && (
                <span className="sj-rel-dif" style={{ color: COR_DIFICULDADE[r.dificuldade] }}>
                  {LABEL_DIFICULDADE[r.dificuldade] ?? r.dificuldade}
                </span>
              )}
              <span className="sj-rel-stats">
                {r.totalRespostas} resposta{r.totalRespostas === 1 ? '' : 's'} · {pctAcerto}% de acerto
              </span>
            </div>
            {r.respostas.length === 0 ? (
              <p className="sj-rel-vazio">Ninguém respondeu esta palavra.</p>
            ) : (
              <ul className="sj-rel-respostas">
                {r.respostas.map(resp => (
                  <li key={resp.login} className={`sj-rel-resp${resp.correta ? ' sj-rel-resp--certa' : ' sj-rel-resp--errada'}`}>
                    <span className="sj-rel-resp-icone">{resp.correta ? '✓' : '✗'}</span>
                    <span className="sj-rel-resp-nome">{resp.nome || resp.login}</span>
                    <span className="sj-rel-resp-texto">&ldquo;{resp.texto}&rdquo;</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        );
      })}
    </div>
  );
};
