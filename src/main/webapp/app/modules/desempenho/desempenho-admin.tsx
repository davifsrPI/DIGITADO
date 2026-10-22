import 'app/shared/components/desempenho/desempenho.scss';
import './desempenho-admin.scss';

import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

import { useBodyClass } from 'app/shared/util/use-body-class';
import { CORES_DIFICULDADE, LABELS_DIFICULDADE } from 'app/shared/util/dificuldade-constants';
import { CORES_NIVEL } from 'app/shared/components/desempenho/acelerador';
import { GraficoEvolucao, PontoEvolucao } from 'app/shared/components/desempenho/grafico-evolucao';
import { ORDEM_TIPOS_ERRO, TIPOS_ERRO, rotuloTipoErro } from 'app/shared/util/tipos-erro';

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
  filtro: FiltroTipoErro | null;
}

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
              <div key={p.palavraId ?? p.texto} className="dp-palavra">
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
            ))}
          </div>
        )}
      </div>
    </>
  );
};

// Ranking de palavras da turma, o mesmo bloco serve para as mais acertadas e as
// mais erradas; muda o número em destaque e a cor
const RankingPalavras: React.FC<{ palavras: PalavraDesempenho[]; modo: 'erro' | 'acerto'; vazio: string }> = ({
  palavras,
  modo,
  vazio,
}) => {
  if (palavras.length === 0) {
    return <p className="dp-vazio">{vazio}</p>;
  }
  return (
    <div className="dp-palavras">
      {palavras.map((p, i) => (
        <div key={p.palavraId ?? p.texto} className="dp-palavra">
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
        {dados && dados.totalRespostas > 0 && (
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
            {ORDEM_TIPOS_ERRO.map(tipo => (
              <button
                key={tipo}
                type="button"
                className={`da-filtro-chip${tipoFiltro === tipo ? ' da-filtro-chip--ativo' : ''}`}
                style={{ borderColor: `${TIPOS_ERRO[tipo].cor}88`, color: tipoFiltro === tipo ? '#0f172a' : TIPOS_ERRO[tipo].cor }}
                onClick={() => setTipoFiltro(tipo)}
                aria-pressed={tipoFiltro === tipo}
                title={`${TIPOS_ERRO[tipo].explicacao} Ex.: ${TIPOS_ERRO[tipo].exemplo}`}
                data-tipo={tipo}
              >
                {TIPOS_ERRO[tipo].rotulo} <strong>{ocorrenciasPorTipo(tipo)}</strong>
              </button>
            ))}
          </div>
        )}

        {carregando && !dados ? (
          <div className="da-loading">Carregando o relatório...</div>
        ) : erro ? (
          <div className="da-empty">
            <div className="da-empty-icon">⚠️</div>
            <p>Não foi possível carregar o relatório. Verifique se você continua autenticado como administrador.</p>
          </div>
        ) : dados && dados.totalRespostas === 0 ? (
          <div className="da-empty">
            <div className="da-empty-icon">🗂️</div>
            <p>Ainda não há respostas de alunos registradas. O relatório começa a se formar assim que eles jogarem.</p>
          </div>
        ) : dados?.filtro ? (
          <PainelTipoErro filtro={dados.filtro} onLimpar={() => setTipoFiltro(null)} />
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

              {/* AS 5 E AS 5 */}
              <div className="dp-duas-colunas">
                <div className="dp-card">
                  <h2 className="dp-card-titulo">5 palavras mais acertadas</h2>
                  <p className="dp-card-sub">O que a turma já domina, entre as palavras com ao menos 5 respostas.</p>
                  <RankingPalavras palavras={dados.palavrasMaisAcertadas} modo="acerto" vazio="Nenhuma palavra com acertos registrados." />
                </div>

                <div className="dp-card">
                  <h2 className="dp-card-titulo">5 palavras mais erradas</h2>
                  <p className="dp-card-sub">Onde a turma tropeça, entre as palavras com ao menos 5 respostas.</p>
                  <RankingPalavras palavras={dados.palavrasMaisErradas} modo="erro" vazio="Nenhuma palavra com erros registrados." />
                </div>
              </div>

              {/* TIPOS DE ERRO: cada tipo explicado, com exemplo, e clicável para
                  filtrar o painel inteiro por ele */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">Tipos de erro da turma</h2>
                <p className="dp-card-sub">Clique num tipo para ver o painel só dele.</p>
                {dados.errosPorTipo.length === 0 ? (
                  <p className="dp-vazio">Nenhum erro registrado ainda.</p>
                ) : (
                  <div className="dp-barras">
                    {dados.errosPorTipo.map(e => (
                      <button key={e.tipo} type="button" className="dp-barra-linha da-barra-botao" onClick={() => setTipoFiltro(e.tipo)}>
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
                    ))}
                  </div>
                )}
              </div>
            </>
          )
        )}
      </div>
    </div>
  );
};

export default DesempenhoAdmin;
