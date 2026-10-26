import 'app/shared/components/desempenho/desempenho.scss';
import './desempenho-admin.scss';

import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

import { useBodyClass } from 'app/shared/util/use-body-class';
import { CORES_DIFICULDADE, LABELS_DIFICULDADE } from 'app/shared/util/dificuldade-constants';
import { CORES_NIVEL } from 'app/shared/components/desempenho/acelerador';
import { GraficoEvolucao, PontoEvolucao } from 'app/shared/components/desempenho/grafico-evolucao';
import {
  ORDEM_TIPOS_ERRO,
  SUBTIPOS_ERRO,
  SUBTIPOS_POR_TIPO,
  TIPOS_ERRO,
  TIPOS_ERRO_ANTIGOS,
  partesDoDetalhe,
  rotuloSubtipoErro,
  rotuloTipoErro,
  temSubtiposErro,
  textoDoTrecho,
} from 'app/shared/util/tipos-erro';

// Painel de desempenho da turma, EXCLUSIVO DE ADMIN.
//
// O acesso é barrado em três lugares independentes: a rota fica dentro do
// PrivateRoute de ADMIN, a URL /api/admin/** exige a role na SecurityConfiguration
// e o endpoint ainda tem @Secured(ADMIN). Um usuário comum não monta a tela e
// receberia 403 da API.
//
// Duas coisas NÃO aparecem aqui, por decisão:
// - as respostas do próprio admin (ele joga para testar; o backend filtra quem
//   tem ROLE_ADMIN de todos os números);
// - qualquer dado individual de aluno. Só médias, contagens e ranking de
//   palavras, o painel responde "a turma está evoluindo?", nunca "quem é quem".

interface PalavraDesempenho {
  palavraId: number;
  texto: string;
  dificuldade: string | null;
  total: number;
  acertos: number;
  erros: number;
  taxa: number;
}

interface TipoErro {
  tipo: string;
  total: number;
  percentual: number;
}

// Um jeito específico de errar dentro do tipo. subtipo nulo = resposta gravada antes
// da classificação detalhada, que só guardou o tipo
interface SubtipoErro {
  tipo: string;
  subtipo: string | null;
  total: number;
  percentualDoTipo: number;
}

// Um trecho que a turma troca, "certo→digitado" ("ç→ss")
interface Confusao {
  tipo: string;
  subtipo: string | null;
  detalhe: string;
  ocorrencias: number;
  alunos: number;
  palavras: number;
  percentualDosErros: number;
  exemplos: string[];
}

// O erro mais comum numa palavra
interface ErroComum {
  tipo: string;
  subtipo: string | null;
  detalhe: string | null;
  ocorrencias: number;
  percentualDosErros: number;
}

interface Desenvolvimento {
  alunosAvaliados: number;
  mediaTaxaInicial: number;
  mediaTaxaAtual: number;
  mediaEvolucao: number;
  alunosMelhoraram: number;
  alunosEstaveis: number;
  alunosPioraram: number;
  resumo: string;
}

interface RelatorioMensal {
  mesAtual: string;
  mesAnterior: string;
  respostasMesAtual: number;
  respostasMesAnterior: number;
  acertosMesAtual: number;
  errosMesAtual: number;
  acertosMesAnterior: number;
  errosMesAnterior: number;
  taxaMesAtual: number;
  taxaMesAnterior: number;
  variacaoTaxa: number;
  variacaoRespostas: number;
  maisAcertosQueErros: boolean;
  balancoDoMes: string;
  veredito: 'MELHOROU' | 'PIOROU' | 'ESTAVEL' | 'SEM_DADOS' | 'SEM_COMPARATIVO';
  resumo: string;
}

// O painel recortado por UM tipo de erro (filtro "Tipos de erro da turma")
interface FiltroTipoErro {
  tipo: string;
  ocorrencias: number;
  percentualDosErros: number;
  percentualDasRespostas: number;
  alunosAfetados: number;
  palavrasAfetadas: number;
  taxaMesAtual: number;
  taxaMesAnterior: number;
  veredito: RelatorioMensal['veredito'];
  resumo: string;
  evolucao: { mes: string; respostas: number; ocorrencias: number; taxa: number }[];
  palavras: { palavraId: number; texto: string; dificuldade: string | null; respostas: number; ocorrencias: number; percentual: number }[];
  subtipos: SubtipoErro[];
  confusoes: Confusao[];
  erroMaisComumPorPalavra: Record<string, ErroComum>;
}

interface DesempenhoGeral {
  totalRespostas: number;
  totalAcertos: number;
  totalErros: number;
  // Rodadas em que o tempo acabou sem resposta (já contadas em totalErros)
  totalNaoRespondeu: number;
  taxaGeral: number;
  totalAlunos: number;
  alunosBons: number;
  alunosMedios: number;
  alunosRuins: number;
  desenvolvimento: Desenvolvimento;
  relatorioMensal: RelatorioMensal;
  evolucao: PontoEvolucao[];
  palavrasMaisAcertadas: PalavraDesempenho[];
  palavrasMaisErradas: PalavraDesempenho[];
  errosPorTipo: TipoErro[];
  subtiposErro: SubtipoErro[];
  confusoes: Confusao[];
  erroMaisComumPorPalavra: Record<string, ErroComum>;
  filtro: FiltroTipoErro | null;
}

/**
 * Todos os tipos que o servidor grava, com zero nos que ainda não aconteceram, do
 * mais frequente para o menos (no empate, a ordem do catálogo). O "outro" antigo só
 * entra se tiver registro; um tipo que a tela não conheça entra como veio.
 */
export const catalogoTipos = (errosPorTipo: TipoErro[]): TipoErro[] => {
  const contados = new Map(errosPorTipo.map(e => [e.tipo, e]));
  return [
    ...ORDEM_TIPOS_ERRO.filter(tipo => contados.has(tipo) || !TIPOS_ERRO_ANTIGOS.includes(tipo)).map(
      tipo => contados.get(tipo) ?? { tipo, total: 0, percentual: 0 },
    ),
    ...errosPorTipo.filter(e => !TIPOS_ERRO[e.tipo]),
  ].sort((a, b) => b.total - a.total);
};

/** Os subtipos de um tipo, com zero nos que ainda não aconteceram; a mesma ordem do catalogoTipos. */
export const catalogoSubtipos = (tipo: string, subtipos: SubtipoErro[]): SubtipoErro[] => {
  const doTipo = subtipos.filter(s => s.tipo === tipo);
  const conhecidos = SUBTIPOS_POR_TIPO[tipo] ?? [];
  return [
    ...conhecidos.map(subtipo => doTipo.find(s => s.subtipo === subtipo) ?? { tipo, subtipo, total: 0, percentualDoTipo: 0 }),
    // o "sem detalhe" dos registros antigos e um subtipo que a tela não conheça
    ...doTipo.filter(s => s.subtipo === null || !conhecidos.includes(s.subtipo)),
  ].sort((a, b) => b.total - a.total);
};

/** Quantos erros foram gravados antes da classificação detalhada (só com o tipo). */
export const errosSemDetalhe = (subtipos: SubtipoErro[]): number =>
  subtipos
    .filter(s => s.subtipo === null && s.tipo !== 'OUTRA_PALAVRA' && s.tipo !== 'NAO_RESPONDEU')
    .reduce((soma, s) => soma + s.total, 0);

/**
 * O trecho trocado, "certo→digitado": o certo em verde, o digitado em vermelho. O
 * de acento pode trazer vários pares ("a→á, é→e"); lado vazio é letra que faltou
 * ou sobrou, e o espaço aparece como "␣".
 */
export const Trecho: React.FC<{ detalhe: string; grande?: boolean }> = ({ detalhe, grande }) => (
  <span className={`da-trecho${grande ? ' da-trecho--grande' : ''}`}>
    {detalhe.split(', ').map(par => {
      const { certo, digitado } = partesDoDetalhe(par);
      return (
        <span
          key={par}
          className="da-trecho-par"
          title={`Certo: ${certo === '' ? '(nada)' : `"${certo}"`} · digitado: ${digitado === '' ? '(nada)' : `"${digitado}"`}`}
        >
          <span className="da-trecho-certo">{textoDoTrecho(certo)}</span>
          <span className="da-trecho-seta" aria-hidden="true">
            →
          </span>
          <span className="da-trecho-digitado">{textoDoTrecho(digitado)}</span>
        </span>
      );
    })}
  </span>
);

// Os subtipos de um tipo, em barras dentro dele
const BarrasSubtipos: React.FC<{ subtipos: SubtipoErro[]; cor: string }> = ({ subtipos, cor }) => (
  <ul className="da-subtipos">
    {subtipos.map(s => (
      <li
        key={s.subtipo ?? 'antigo'}
        className={`da-subtipo${s.subtipo ? '' : ' da-subtipo--antigo'}${s.total === 0 ? ' da-subtipo--zero' : ''}`}
        title={s.subtipo ? SUBTIPOS_ERRO[s.subtipo]?.explicacao : 'Gravado antes da classificação detalhada: só o tipo ficou registrado.'}
      >
        <span className="da-subtipo-nome">{rotuloSubtipoErro(s.subtipo)}</span>
        <span className="da-subtipo-trilho">
          <span className="da-subtipo-barra" style={{ width: `${s.percentualDoTipo}%`, background: s.subtipo ? cor : '#64748b' }} />
        </span>
        <span className="da-subtipo-valor">
          {s.total} · {s.percentualDoTipo}%
        </span>
      </li>
    ))}
  </ul>
);

// "O que a turma mais troca": cada trecho com o tipo, onde aconteceu e quantas vezes.
// No painel geral a fatia é de todos os erros; filtrado por um tipo, só dos dele
const ListaConfusoes: React.FC<{ confusoes: Confusao[]; vazio: string; onTipo?: (tipo: string) => void; base?: string }> = ({
  confusoes,
  vazio,
  onTipo,
  base = 'dos erros',
}) => {
  if (confusoes.length === 0) {
    return <p className="dp-vazio">{vazio}</p>;
  }
  return (
    <ol className="da-confusoes">
      {confusoes.map((c, i) => {
        const conteudo = (
          <>
            <span className="dp-palavra-pos">{i + 1}º</span>
            <Trecho detalhe={c.detalhe} grande />
            <span className="da-confusao-texto">
              <span className="da-confusao-tipo" style={{ color: TIPOS_ERRO[c.tipo]?.cor }}>
                {rotuloTipoErro(c.tipo)}
                {c.subtipo && ` · ${rotuloSubtipoErro(c.subtipo)}`}
              </span>
              <span className="da-confusao-exemplos">
                em {c.exemplos.join(', ')}
                {c.palavras > c.exemplos.length && ` e mais ${c.palavras - c.exemplos.length}`}
              </span>
            </span>
            <span className="da-confusao-num">
              <strong>
                {c.ocorrencias} {c.ocorrencias === 1 ? 'vez' : 'vezes'}
              </strong>
              <small>
                {c.alunos} {c.alunos === 1 ? 'aluno' : 'alunos'} · {c.percentualDosErros}% {base}
              </small>
            </span>
          </>
        );
        return (
          <li key={`${c.tipo}|${c.subtipo}|${c.detalhe}`}>
            {onTipo ? (
              <button type="button" className="da-confusao da-confusao--botao" onClick={() => onTipo(c.tipo)}>
                {conteudo}
              </button>
            ) : (
              <div className="da-confusao">{conteudo}</div>
            )}
          </li>
        );
      })}
    </ol>
  );
};

// Embaixo de uma palavra do ranking: como a turma costuma errá-la
const LinhaErroComum: React.FC<{ erro?: ErroComum; mostrarTipo?: boolean }> = ({ erro, mostrarTipo = true }) =>
  erro ? (
    <div className="da-palavra-erro">
      <span className="da-palavra-erro-rotulo">Erro mais comum:</span>
      {mostrarTipo && <strong style={{ color: TIPOS_ERRO[erro.tipo]?.cor }}>{rotuloTipoErro(erro.tipo)}</strong>}
      {erro.subtipo && <span>{rotuloSubtipoErro(erro.subtipo)}</span>}
      {erro.detalhe && <Trecho detalhe={erro.detalhe} />}
      <span className="da-palavra-erro-pct">
        {erro.ocorrencias}× · {erro.percentualDosErros}% dos erros
      </span>
    </div>
  ) : null;

// Como pintar o cartão do comparativo entre meses
const ESTILO_VEREDITO: Record<RelatorioMensal['veredito'], { cor: string; icone: string; titulo: string }> = {
  MELHOROU: { cor: CORES_NIVEL.BOM, icone: '📈', titulo: 'A turma melhorou' },
  PIOROU: { cor: CORES_NIVEL.RUIM, icone: '📉', titulo: 'A turma piorou' },
  ESTAVEL: { cor: '#60a5fa', icone: '➖', titulo: 'Desempenho estável' },
  SEM_DADOS: { cor: '#94a3b8', icone: '🗓️', titulo: 'Sem dados no período' },
  SEM_COMPARATIVO: { cor: '#94a3b8', icone: '🆕', titulo: 'Primeiro mês com dados' },
};

const MESES_EXTENSO = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
];

// '2026-08' -> 'agosto de 2026'
const mesPorExtenso = (mes: string): string => {
  const [ano, m] = (mes ?? '').split('-');
  const i = Number(m) - 1;
  return i >= 0 && i < 12 ? `${MESES_EXTENSO[i]} de ${ano}` : mes;
};

const corDificuldade = (dif: string | null): string => CORES_DIFICULDADE[dif ?? ''] ?? '#94a3b8';
const labelDificuldade = (dif: string | null): string => LABELS_DIFICULDADE[dif ?? ''] ?? dif ?? '-';

// Número com uma casa decimal, no padrão brasileiro (2,5%)
const umaCasa = (n: number): string => n.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

// Num filtro por tipo de erro, MELHOROU quer dizer que a turma errou MENOS daquele jeito
const ESTILO_VEREDITO_FILTRO: Record<RelatorioMensal['veredito'], { cor: string; icone: string; titulo: string }> = {
  MELHOROU: { cor: CORES_NIVEL.BOM, icone: '📉', titulo: 'A turma está cometendo MENOS este erro' },
  PIOROU: { cor: CORES_NIVEL.RUIM, icone: '📈', titulo: 'A turma está cometendo MAIS este erro' },
  ESTAVEL: { cor: '#60a5fa', icone: '➖', titulo: 'Este erro está estável' },
  SEM_DADOS: { cor: '#94a3b8', icone: '🗓️', titulo: 'Sem dados no período' },
  SEM_COMPARATIVO: { cor: '#94a3b8', icone: '🆕', titulo: 'Primeiro mês com dados' },
};

/**
 * O painel recortado por um tipo de erro: o que ele é (com exemplo), quanto pesa,
 * como variou no mês, a linha do tempo e as palavras onde mais acontece.
 */
const PainelTipoErro: React.FC<{ filtro: FiltroTipoErro; onLimpar: () => void }> = ({ filtro, onLimpar }) => {
  const tipo = TIPOS_ERRO[filtro.tipo];
  const veredito = ESTILO_VEREDITO_FILTRO[filtro.veredito];
  const pontos: PontoEvolucao[] = filtro.evolucao.map(p => ({ mes: p.mes, total: p.respostas, acertos: p.ocorrencias, taxa: p.taxa }));
  return (
    <>
      <div className="da-relatorio" style={{ borderColor: `${veredito.cor}55`, background: `${veredito.cor}12` }}>
        <div className="da-relatorio-icone">{veredito.icone}</div>
        <div className="da-relatorio-texto">
          <span className="da-relatorio-etiqueta">Filtrando por: {rotuloTipoErro(filtro.tipo)}</span>
          <h2 className="da-relatorio-titulo" style={{ color: veredito.cor }}>
            {veredito.titulo}
          </h2>
          {tipo && (
            <p className="da-tipo-explicacao">
              <strong>O que é:</strong> {tipo.explicacao} <em>Ex.: {tipo.exemplo}.</em>
            </p>
          )}
          <p className="da-relatorio-resumo">{filtro.resumo}</p>
          <button type="button" className="da-filtro-limpar" onClick={onLimpar}>
            ✕ Ver o painel completo
          </button>
        </div>
      </div>

      <div className="dp-card">
        <h2 className="dp-card-titulo">{rotuloTipoErro(filtro.tipo)} em números</h2>
        <div className="dp-kpis">
          <div className="dp-kpi">
            <span className="dp-kpi-valor" style={{ color: tipo?.cor }}>
              {filtro.ocorrencias}
            </span>
            <span className="dp-kpi-label">Ocorrências</span>
          </div>
          <div className="dp-kpi">
            <span className="dp-kpi-valor">{filtro.percentualDosErros}%</span>
            <span className="dp-kpi-label">dos erros da turma</span>
          </div>
          <div className="dp-kpi">
            <span className="dp-kpi-valor">{umaCasa(filtro.percentualDasRespostas)}%</span>
            <span className="dp-kpi-label">das palavras ditadas</span>
          </div>
          <div className="dp-kpi">
            <span className="dp-kpi-valor">{filtro.alunosAfetados}</span>
            <span className="dp-kpi-label">Alunos que cometeram</span>
          </div>
          <div className="dp-kpi">
            <span className="dp-kpi-valor">{filtro.palavrasAfetadas}</span>
            <span className="dp-kpi-label">Palavras envolvidas</span>
          </div>
        </div>
      </div>

      {/* COMO o erro acontece: os subtipos (todos, mesmo os que ainda não
          aconteceram) e os trechos que a turma mais troca */}
      {temSubtiposErro(filtro.tipo) && (
        <div className="dp-card">
          <h2 className="dp-card-titulo">Como este erro acontece</h2>
          <p className="dp-card-sub">
            Todos os jeitos de cometer este erro que o jogo reconhece; o que ainda não aconteceu aparece com zero. Passe o mouse num item
            para ver o que ele quer dizer.
          </p>
          <BarrasSubtipos subtipos={catalogoSubtipos(filtro.tipo, filtro.subtipos)} cor={tipo?.cor ?? '#f472b6'} />
          {errosSemDetalhe(filtro.subtipos) > 0 && (
            <p className="da-nota">
              {errosSemDetalhe(filtro.subtipos)} ocorrência(s) foram gravadas antes da classificação detalhada e ficaram só com o tipo.
            </p>
          )}

          <h3 className="da-subtitulo">O que mais é trocado</h3>
          <p className="dp-card-sub">Verde é o que a palavra pede; vermelho, o que foi digitado. Traço é a letra que faltou ou sobrou.</p>
          <ListaConfusoes
            confusoes={filtro.confusoes}
            base="deste erro"
            vazio="Ainda não há ocorrências com o trecho trocado registrado."
          />
        </div>
      )}

      <div className="dp-card">
        <h2 className="dp-card-titulo">Histórico dos últimos 12 meses</h2>
        <p className="dp-card-sub">Linha: % das palavras ditadas no mês que tiveram este erro. Barras: quantas palavras foram ditadas.</p>
        <GraficoEvolucao pontos={pontos} cor={tipo?.cor ?? '#f472b6'} vazio="Ainda não há meses com respostas." />
      </div>

      <div className="dp-card">
        <h2 className="dp-card-titulo">Onde este erro mais acontece</h2>
        {filtro.palavras.length === 0 ? (
          <p className="dp-vazio">Nenhuma palavra com este erro ainda.</p>
        ) : (
          <div className="dp-palavras">
            {filtro.palavras.map((p, i) => (
              <div key={p.palavraId ?? p.texto} className="da-palavra-bloco">
                <div className="dp-palavra">
                  <span className="dp-palavra-pos">{i + 1}º</span>
                  <span className="dp-palavra-texto">{p.texto}</span>
                  <span className="dp-palavra-dif" style={{ color: corDificuldade(p.dificuldade) }}>
                    {labelDificuldade(p.dificuldade)}
                  </span>
                  <span className="dp-palavra-num">
                    {p.ocorrencias} de {p.respostas}
                  </span>
                  <span className="dp-palavra-taxa" style={{ color: tipo?.cor ?? CORES_NIVEL.RUIM }}>
                    {p.percentual}%
                  </span>
                </div>
                {temSubtiposErro(filtro.tipo) && (
                  <LinhaErroComum erro={filtro.erroMaisComumPorPalavra?.[p.palavraId]} mostrarTipo={false} />
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
};

// TIPOS DE ERRO: todos os erros que o jogo reconhece, cada um explicado e aberto nos
// subtipos, com zero no que ainda não aconteceu - o admin vê de antemão tudo o que o
// painel vai contar. Clicar num tipo filtra o painel inteiro por ele
const CatalogoTiposErro: React.FC<{ errosPorTipo: TipoErro[]; subtipos: SubtipoErro[]; onTipo: (tipo: string) => void }> = ({
  errosPorTipo,
  subtipos,
  onTipo,
}) => {
  const semDetalhe = errosSemDetalhe(subtipos);
  return (
    <div className="dp-card">
      <h2 className="dp-card-titulo">Tipos de erro da turma</h2>
      <p className="dp-card-sub">
        Todos os erros que o jogo sabe reconhecer, cada tipo aberto no jeito específico de errar; o que ainda não aconteceu aparece com
        zero. Clique num tipo para ver o painel só dele.
      </p>
      {errosPorTipo.length === 0 && <p className="dp-vazio">Nenhum erro registrado ainda: abaixo, tudo o que vai ser contado.</p>}
      <div className="dp-barras">
        {catalogoTipos(errosPorTipo).map(e => (
          <div key={e.tipo} className={`da-tipo-bloco${e.total === 0 ? ' da-tipo-bloco--zero' : ''}`} data-tipo={e.tipo}>
            <button type="button" className="dp-barra-linha da-barra-botao" onClick={() => onTipo(e.tipo)}>
              <div className="dp-barra-topo">
                <span className="dp-barra-nome">{rotuloTipoErro(e.tipo)}</span>
                <span className="dp-barra-valor">
                  {e.total} · {e.percentual}%
                </span>
              </div>
              <div className="dp-barra-trilho">
                <div
                  className="dp-barra-preenchida"
                  style={{ width: `${e.percentual}%`, background: TIPOS_ERRO[e.tipo]?.cor ?? '#f472b6' }}
                />
              </div>
              {TIPOS_ERRO[e.tipo] && (
                <span className="da-tipo-explicacao">
                  {TIPOS_ERRO[e.tipo].explicacao} <em>Ex.: {TIPOS_ERRO[e.tipo].exemplo}.</em>
                </span>
              )}
            </button>
            {temSubtiposErro(e.tipo) && (
              <BarrasSubtipos subtipos={catalogoSubtipos(e.tipo, subtipos)} cor={TIPOS_ERRO[e.tipo]?.cor ?? '#f472b6'} />
            )}
          </div>
        ))}
      </div>
      {semDetalhe > 0 && (
        <p className="da-nota">
          {semDetalhe} erro(s) foram gravados antes da classificação detalhada: aparecem só com o tipo, sem o jeito específico nem o trecho
          trocado.
        </p>
      )}
    </div>
  );
};

// Ranking de palavras da turma, o mesmo bloco serve para as mais acertadas e as
// mais erradas; muda o número em destaque e a cor
const RankingPalavras: React.FC<{
  palavras: PalavraDesempenho[];
  modo: 'erro' | 'acerto';
  vazio: string;
  erroMaisComum?: Record<string, ErroComum>;
}> = ({ palavras, modo, vazio, erroMaisComum }) => {
  if (palavras.length === 0) {
    return <p className="dp-vazio">{vazio}</p>;
  }
  return (
    <div className="dp-palavras">
      {palavras.map((p, i) => (
        <div key={p.palavraId ?? p.texto} className="da-palavra-bloco">
          <div className="dp-palavra">
            <span className="dp-palavra-pos">{i + 1}º</span>
            <span className="dp-palavra-texto">{p.texto}</span>
            <span className="dp-palavra-dif" style={{ color: corDificuldade(p.dificuldade) }}>
              {labelDificuldade(p.dificuldade)}
            </span>
            <span className="dp-palavra-num">
              {modo === 'erro' ? p.erros : p.acertos} de {p.total}
            </span>
            <span className="dp-palavra-taxa" style={{ color: modo === 'erro' ? CORES_NIVEL.RUIM : CORES_NIVEL.BOM }}>
              {p.taxa}%
            </span>
          </div>
          {modo === 'erro' && <LinhaErroComum erro={erroMaisComum?.[p.palavraId]} />}
        </div>
      ))}
    </div>
  );
};

export const DesempenhoAdmin: React.FC = () => {
  const navigate = useNavigate();
  const [dados, setDados] = useState<DesempenhoGeral | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  // Tipo de erro selecionado no filtro (null = painel completo)
  const [tipoFiltro, setTipoFiltro] = useState<string | null>(null);

  useBodyClass('desempenho-admin-page');

  useEffect(() => {
    setCarregando(true);
    axios
      .get<DesempenhoGeral>('/api/admin/desempenho', { params: { tipoErro: tipoFiltro ?? undefined } })
      .then(res => {
        setDados(res.data);
        setErro(false);
      })
      .catch(() => setErro(true))
      .finally(() => setCarregando(false));
  }, [tipoFiltro]);

  // Quantas ocorrências de cada tipo, para os chips do filtro (0 quando o tipo não apareceu)
  const ocorrenciasPorTipo = (tipo: string) => dados?.errosPorTipo.find(e => e.tipo === tipo)?.total ?? 0;

  const veredito = dados ? ESTILO_VEREDITO[dados.relatorioMensal.veredito] : null;
  const totalClassificados = dados ? dados.alunosBons + dados.alunosMedios + dados.alunosRuins : 0;
  const fatia = (quantidade: number) => (totalClassificados > 0 ? (quantidade / totalClassificados) * 100 : 0);

  // Cor do bloco de desenvolvimento: acompanha o sinal da evolução média
  const corEvolucao = (pontos: number) => (pontos >= 3 ? CORES_NIVEL.BOM : pontos <= -3 ? CORES_NIVEL.RUIM : '#60a5fa');

  return (
    <div className="da-wrapper">
      <div className="da-bg">
        <div className="da-shape one" />
        <div className="da-shape two" />
      </div>

      <div className="da-center">
        <button className="da-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <div className="da-header">
          <span className="da-badge">Somente administradores</span>
          <h1 className="da-title">📊 Desempenho de todos</h1>
          <p className="da-subtitle">
            Resumo de <strong>todos os usuários</strong> que já jogaram. Suas próprias respostas ficam de fora, e nenhum usuário é
            identificado individualmente aqui.
          </p>
        </div>

        {/* Filtro por tipo de erro: o painel inteiro passa a contar a história
            daquele erro. Os chips trazem a contagem, e o title explica o tipo. */}
        {dados && (
          <div className="da-filtro" role="group" aria-label="Filtrar por tipo de erro">
            <span className="da-filtro-rotulo">Filtrar por tipo de erro:</span>
            <button
              type="button"
              className={`da-filtro-chip${tipoFiltro === null ? ' da-filtro-chip--ativo' : ''}`}
              onClick={() => setTipoFiltro(null)}
              aria-pressed={tipoFiltro === null}
            >
              Todos
            </button>
            {/* Todos os tipos, mesmo os que ainda não aconteceram (esses ficam apagados);
                o "outro" antigo só se tiver registro, porque erro novo não entra nele */}
            {ORDEM_TIPOS_ERRO.filter(tipo => !TIPOS_ERRO_ANTIGOS.includes(tipo) || ocorrenciasPorTipo(tipo) > 0 || tipoFiltro === tipo).map(
              tipo => (
                <button
                  key={tipo}
                  type="button"
                  className={`da-filtro-chip${tipoFiltro === tipo ? ' da-filtro-chip--ativo' : ''}${ocorrenciasPorTipo(tipo) === 0 ? ' da-filtro-chip--zero' : ''}`}
                  style={{ borderColor: `${TIPOS_ERRO[tipo].cor}88`, color: tipoFiltro === tipo ? '#0f172a' : TIPOS_ERRO[tipo].cor }}
                  onClick={() => setTipoFiltro(tipo)}
                  aria-pressed={tipoFiltro === tipo}
                  title={`${TIPOS_ERRO[tipo].explicacao} Ex.: ${TIPOS_ERRO[tipo].exemplo}`}
                  data-tipo={tipo}
                >
                  {TIPOS_ERRO[tipo].rotulo} <strong>{ocorrenciasPorTipo(tipo)}</strong>
                </button>
              ),
            )}
          </div>
        )}

        {carregando && !dados ? (
          <div className="da-loading">Carregando o relatório...</div>
        ) : erro ? (
          <div className="da-empty">
            <div className="da-empty-icon">⚠️</div>
            <p>Não foi possível carregar o relatório. Verifique se você continua autenticado como administrador.</p>
          </div>
        ) : dados?.filtro ? (
          <PainelTipoErro filtro={dados.filtro} onLimpar={() => setTipoFiltro(null)} />
        ) : dados && dados.totalRespostas === 0 ? (
          <>
            <div className="da-empty">
              <div className="da-empty-icon">🗂️</div>
              <p>
                Ainda não há respostas de alunos registradas. O relatório começa a se formar assim que eles jogarem; abaixo, todos os erros
                que ele vai contar.
              </p>
            </div>
            <CatalogoTiposErro errosPorTipo={dados.errosPorTipo} subtipos={dados.subtiposErro ?? []} onTipo={setTipoFiltro} />
          </>
        ) : (
          dados &&
          veredito && (
            <>
              {/* RELATÓRIO DO MÊS, a pergunta central do painel, em duas partes:
                  o balanço do mês corrente e a comparação com o mês anterior */}
              <div className="da-relatorio" style={{ borderColor: `${veredito.cor}55`, background: `${veredito.cor}12` }}>
                <div className="da-relatorio-icone">{veredito.icone}</div>
                <div className="da-relatorio-texto">
                  <span className="da-relatorio-etiqueta">Relatório de {mesPorExtenso(dados.relatorioMensal.mesAtual)}</span>
                  <h2 className="da-relatorio-titulo" style={{ color: veredito.cor }}>
                    {veredito.titulo}
                  </h2>

                  {/* 1) Houve mais acertos ou mais erros no mês? */}
                  <div
                    className="da-balanco"
                    style={{
                      borderColor: dados.relatorioMensal.maisAcertosQueErros ? `${CORES_NIVEL.BOM}55` : `${CORES_NIVEL.RUIM}55`,
                    }}
                  >
                    <span className="da-balanco-icone">{dados.relatorioMensal.maisAcertosQueErros ? '✅' : '❌'}</span>
                    <div className="da-balanco-texto">
                      <p className="da-balanco-frase">{dados.relatorioMensal.balancoDoMes}</p>
                      <div className="da-balanco-barra">
                        <div
                          className="da-balanco-parte"
                          style={{
                            width: `${dados.relatorioMensal.respostasMesAtual > 0 ? (dados.relatorioMensal.acertosMesAtual / dados.relatorioMensal.respostasMesAtual) * 100 : 0}%`,
                            background: CORES_NIVEL.BOM,
                          }}
                        />
                        <div
                          className="da-balanco-parte"
                          style={{
                            width: `${dados.relatorioMensal.respostasMesAtual > 0 ? (dados.relatorioMensal.errosMesAtual / dados.relatorioMensal.respostasMesAtual) * 100 : 0}%`,
                            background: CORES_NIVEL.RUIM,
                          }}
                        />
                      </div>
                    </div>
                  </div>

                  {/* 2) Melhorou ou piorou em relação ao mês anterior? */}
                  <p className="da-relatorio-resumo">{dados.relatorioMensal.resumo}</p>
                  <div className="da-relatorio-comparativo">
                    <div className="da-comp-bloco">
                      <span className="da-comp-mes">{mesPorExtenso(dados.relatorioMensal.mesAnterior)}</span>
                      <span className="da-comp-taxa">{dados.relatorioMensal.taxaMesAnterior}%</span>
                      <span className="da-comp-vol">
                        {dados.relatorioMensal.acertosMesAnterior} acertos · {dados.relatorioMensal.errosMesAnterior} erros
                      </span>
                    </div>
                    <span className="da-comp-seta">→</span>
                    <div className="da-comp-bloco">
                      <span className="da-comp-mes">{mesPorExtenso(dados.relatorioMensal.mesAtual)}</span>
                      <span className="da-comp-taxa" style={{ color: veredito.cor }}>
                        {dados.relatorioMensal.taxaMesAtual}%
                      </span>
                      <span className="da-comp-vol">
                        {dados.relatorioMensal.acertosMesAtual} acertos · {dados.relatorioMensal.errosMesAtual} erros
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* DESENVOLVIMENTO MÉDIO, quanto os alunos evoluíram desde que começaram */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">Quanto os alunos se desenvolveram</h2>
                <p className="dp-card-sub">
                  O histórico de cada aluno é partido ao meio: compara-se o acerto da segunda metade com o da primeira. Entram os alunos com
                  pelo menos 10 respostas ({dados.desenvolvimento.alunosAvaliados} no momento).
                </p>

                {dados.desenvolvimento.alunosAvaliados === 0 ? (
                  <p className="dp-vazio">{dados.desenvolvimento.resumo}</p>
                ) : (
                  <>
                    <div className="da-evolucao-media">
                      <div className="da-comp-bloco">
                        <span className="da-comp-mes">No começo</span>
                        <span className="da-comp-taxa">{dados.desenvolvimento.mediaTaxaInicial}%</span>
                      </div>
                      <span className="da-comp-seta">→</span>
                      <div className="da-comp-bloco">
                        <span className="da-comp-mes">Hoje</span>
                        <span className="da-comp-taxa" style={{ color: corEvolucao(dados.desenvolvimento.mediaEvolucao) }}>
                          {dados.desenvolvimento.mediaTaxaAtual}%
                        </span>
                      </div>
                      <span
                        className="da-evolucao-chip"
                        style={{
                          color: corEvolucao(dados.desenvolvimento.mediaEvolucao),
                          borderColor: `${corEvolucao(dados.desenvolvimento.mediaEvolucao)}66`,
                          background: `${corEvolucao(dados.desenvolvimento.mediaEvolucao)}1a`,
                        }}
                      >
                        {dados.desenvolvimento.mediaEvolucao > 0 ? '▲' : dados.desenvolvimento.mediaEvolucao < 0 ? '▼' : '='}{' '}
                        {Math.abs(dados.desenvolvimento.mediaEvolucao)} pontos em média
                      </span>
                    </div>

                    <p className="da-relatorio-resumo">{dados.desenvolvimento.resumo}</p>

                    <div className="dp-chips">
                      <span className="dp-chip" style={{ color: CORES_NIVEL.BOM }}>
                        Melhoraram: <strong>{dados.desenvolvimento.alunosMelhoraram}</strong>
                      </span>
                      <span className="dp-chip" style={{ color: '#93c5fd' }}>
                        Estáveis: <strong>{dados.desenvolvimento.alunosEstaveis}</strong>
                      </span>
                      <span className="dp-chip" style={{ color: CORES_NIVEL.RUIM }}>
                        Pioraram: <strong>{dados.desenvolvimento.alunosPioraram}</strong>
                      </span>
                    </div>
                  </>
                )}
              </div>

              {/* DESEMPENHO GERAL, o retrato de toda a turma */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">Desempenho geral dos alunos</h2>
                <div className="dp-kpis">
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor">{dados.totalRespostas}</span>
                    <span className="dp-kpi-label">Palavras ditadas</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor" style={{ color: CORES_NIVEL.BOM }}>
                      {dados.totalAcertos}
                    </span>
                    <span className="dp-kpi-label">Acertos</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor" style={{ color: CORES_NIVEL.RUIM }}>
                      {dados.totalErros - dados.totalNaoRespondeu}
                    </span>
                    <span className="dp-kpi-label">Erros de grafia</span>
                  </div>
                  {/* O tempo acabou sem resposta: conta como erro na taxa de acerto,
                      mas é outra história - pode ser o áudio, a pressa, a distração */}
                  <div className="dp-kpi">
                    <button
                      type="button"
                      className="da-kpi-botao"
                      onClick={() => setTipoFiltro('NAO_RESPONDEU')}
                      title="Ver só as rodadas sem resposta"
                    >
                      <span className="dp-kpi-valor" style={{ color: TIPOS_ERRO.NAO_RESPONDEU.cor }}>
                        {dados.totalNaoRespondeu}
                      </span>
                      <span className="dp-kpi-label">Não respondeu</span>
                    </button>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor">{dados.taxaGeral}%</span>
                    <span className="dp-kpi-label">Assertividade média</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor">{dados.totalAlunos}</span>
                    <span className="dp-kpi-label">Alunos com histórico</span>
                  </div>
                </div>

                {totalClassificados > 0 && (
                  <>
                    <p className="dp-card-sub">Em que faixa os alunos estão hoje:</p>
                    <div className="da-distribuicao">
                      <div className="da-dist-fatia" style={{ width: `${fatia(dados.alunosBons)}%`, background: CORES_NIVEL.BOM }} />
                      <div className="da-dist-fatia" style={{ width: `${fatia(dados.alunosMedios)}%`, background: CORES_NIVEL.MEDIO }} />
                      <div className="da-dist-fatia" style={{ width: `${fatia(dados.alunosRuins)}%`, background: CORES_NIVEL.RUIM }} />
                    </div>
                    <div className="dp-chips">
                      <span className="dp-chip" style={{ color: CORES_NIVEL.BOM }}>
                        Bom (75%+): <strong>{dados.alunosBons}</strong>
                      </span>
                      <span className="dp-chip" style={{ color: CORES_NIVEL.MEDIO }}>
                        Médio (50-74%): <strong>{dados.alunosMedios}</strong>
                      </span>
                      <span className="dp-chip" style={{ color: CORES_NIVEL.RUIM }}>
                        Ruim (abaixo de 50%): <strong>{dados.alunosRuins}</strong>
                      </span>
                    </div>
                  </>
                )}
              </div>

              {/* HISTÓRICO */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">Histórico dos últimos 12 meses</h2>
                <p className="dp-card-sub">Linha: acerto da turma no mês. Barras: quantas palavras foram respondidas.</p>
                <GraficoEvolucao pontos={dados.evolucao} cor="#38bdf8" vazio="Ainda não há meses suficientes para o histórico." />
              </div>

              {/* AS 10 E AS 10 */}
              <div className="dp-duas-colunas">
                <div className="dp-card">
                  <h2 className="dp-card-titulo">Palavras mais acertadas</h2>
                  <p className="dp-card-sub">O que a turma já domina: as 10 primeiras entre as palavras com ao menos 5 respostas.</p>
                  <RankingPalavras palavras={dados.palavrasMaisAcertadas} modo="acerto" vazio="Nenhuma palavra com acertos registrados." />
                </div>

                <div className="dp-card">
                  <h2 className="dp-card-titulo">Palavras mais erradas</h2>
                  <p className="dp-card-sub">
                    Onde a turma tropeça: as 10 primeiras entre as palavras com ao menos 5 respostas, cada uma com o erro que mais aparece
                    nela.
                  </p>
                  <RankingPalavras
                    palavras={dados.palavrasMaisErradas}
                    modo="erro"
                    vazio="Nenhuma palavra com erros registrados."
                    erroMaisComum={dados.erroMaisComumPorPalavra}
                  />
                </div>
              </div>

              {/* O QUE A TURMA MAIS TROCA: o trecho exato, de todos os tipos */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">O que a turma mais troca</h2>
                <p className="dp-card-sub">
                  O trecho exato de cada erro: em verde o que a palavra pede, em vermelho o que foi digitado (traço = letra que faltou ou
                  sobrou). Clique num item para ver o painel do tipo dele.
                </p>
                <ListaConfusoes
                  confusoes={dados.confusoes}
                  onTipo={setTipoFiltro}
                  vazio="Os trechos trocados aparecem aqui a partir das próximas respostas: os erros gravados antes guardam só o tipo."
                />
              </div>

              <CatalogoTiposErro errosPorTipo={dados.errosPorTipo} subtipos={dados.subtiposErro ?? []} onTipo={setTipoFiltro} />
            </>
          )
        )}
      </div>
    </div>
  );
};

export default DesempenhoAdmin;
