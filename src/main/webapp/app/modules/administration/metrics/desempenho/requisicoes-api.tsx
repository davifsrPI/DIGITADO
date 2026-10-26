import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';

import { formatarMs, formatarNumero } from './monitor-util';

// "Requisições por API" e "Erros recentes", embaixo do item Requisições HTTP do
// painel Desempenho - o equivalente da aba Processos do Gerenciador de Tarefas:
// uma linha por API (método + rota), com quanto ela está sendo chamada agora, o
// tempo e os erros, e a lista dos últimos erros com a exceção e a pilha.
//
// Lê GET /management/requisicoes (só ADMIN) só enquanto está na tela, nunca mais
// de uma vez por segundo: é uma leitura maior que a dos gráficos.

export const URL_REQUISICOES = 'management/requisicoes';

/** Mais rápido que isso a tabela não pergunta, mesmo com o painel em "Alta". */
export const INTERVALO_MINIMO_MS = 1000;

export interface Rota {
  metodo: string;
  uri: string;
  requisicoes: number;
  tempoTotalMs: number;
  maximoMs: number;
  sucesso: number;
  erros4xx: number;
  erros5xx: number;
  porStatus: Record<string, number>;
  excecoes: Record<string, number>;
}

export interface ErroHttp {
  instante: number;
  metodo: string | null;
  rota: string | null;
  caminho: string | null;
  status: number;
  duracaoMs: number | null;
  excecao: string | null;
  mensagem: string | null;
  causaRaiz: string | null;
  pilha: string[];
}

export interface LeituraRequisicoes {
  instante: number;
  rotas: Rota[];
  errosRecentes: ErroHttp[];
  errosRegistrados: number;
}

export interface LinhaRota extends Rota {
  chave: string;
  /** Chamadas por segundo no último intervalo; nulo na primeira leitura. */
  porSegundo: number | null;
  tempoMedioMs: number | null;
  percentualErro: number;
}

export const chaveDaRota = (r: Pick<Rota, 'metodo' | 'uri'>): string => `${r.metodo} ${r.uri}`;

/**
 * As linhas da tabela, com a taxa do último intervalo. Rota que não estava na
 * leitura anterior foi toda chamada dentro dele; contador que andou para trás
 * (servidor reiniciado) não vira taxa negativa.
 */
export const montarLinhas = (atual: LeituraRequisicoes, anterior?: LeituraRequisicoes): LinhaRota[] => {
  const antes = new Map((anterior?.rotas ?? []).map(r => [chaveDaRota(r), r]));
  const segundos = anterior ? (atual.instante - anterior.instante) / 1000 : 0;
  return atual.rotas.map(r => {
    let porSegundo: number | null = null;
    if (anterior && segundos > 0) {
      const delta = r.requisicoes - (antes.get(chaveDaRota(r))?.requisicoes ?? 0);
      porSegundo = delta >= 0 ? delta / segundos : null;
    }
    return {
      ...r,
      chave: chaveDaRota(r),
      porSegundo,
      tempoMedioMs: r.requisicoes > 0 ? r.tempoTotalMs / r.requisicoes : null,
      percentualErro: r.requisicoes > 0 ? ((r.erros4xx + r.erros5xx) / r.requisicoes) * 100 : 0,
    };
  });
};

type Coluna = 'api' | 'porSegundo' | 'requisicoes' | 'tempoMedioMs' | 'maximoMs' | 'erros4xx' | 'erros5xx' | 'percentualErro';

const COLUNAS: { id: Coluna; rotulo: string; titulo: string }[] = [
  { id: 'api', rotulo: 'API', titulo: 'Método e rota' },
  { id: 'porSegundo', rotulo: 'Req/s', titulo: 'Chamadas por segundo no último intervalo' },
  { id: 'requisicoes', rotulo: 'Total', titulo: 'Chamadas desde que o servidor subiu' },
  { id: 'tempoMedioMs', rotulo: 'Tempo médio', titulo: 'Tempo médio de resposta desde que o servidor subiu' },
  { id: 'maximoMs', rotulo: 'Pico', titulo: 'A chamada mais lenta dos últimos minutos' },
  { id: 'erros4xx', rotulo: '4xx', titulo: 'Respostas de erro do cliente (400, 401, 403, 404...)' },
  { id: 'erros5xx', rotulo: '5xx', titulo: 'Respostas de erro do servidor (500...)' },
  { id: 'percentualErro', rotulo: '% erro', titulo: 'Quanto das chamadas terminou em 4xx ou 5xx' },
];

const valorDaColuna = (l: LinhaRota, c: Coluna): number | string => {
  if (c === 'api') return l.uri;
  return l[c] ?? -1;
};

export const ordenar = (linhas: LinhaRota[], coluna: Coluna, decrescente: boolean): LinhaRota[] =>
  [...linhas].sort((a, b) => {
    const va = valorDaColuna(a, coluna);
    const vb = valorDaColuna(b, coluna);
    const comparacao = typeof va === 'string' ? va.localeCompare(String(vb)) : va - (vb as number);
    return (decrescente ? -comparacao : comparacao) || a.chave.localeCompare(b.chave);
  });

const nomeSimples = (classe: string | null): string => (classe ? classe.substring(classe.lastIndexOf('.') + 1) : '');

const horario = (instante: number): string => new Date(instante).toLocaleTimeString('pt-BR');

const classeDoStatus = (status: number): string => (status >= 500 ? 'rq-status-5xx' : status >= 400 ? 'rq-status-4xx' : 'rq-status-ok');

const TabelaRotas = ({ linhas }: { linhas: LinhaRota[] }) => {
  const [coluna, setColuna] = useState<Coluna>('requisicoes');
  const [decrescente, setDecrescente] = useState(true);
  const [busca, setBusca] = useState('');
  const [soComErro, setSoComErro] = useState(false);
  const [aberta, setAberta] = useState<string | null>(null);

  const visiveis = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    const filtradas = linhas.filter(
      l => (!termo || chaveDaRota(l).toLowerCase().includes(termo)) && (!soComErro || l.erros4xx + l.erros5xx > 0),
    );
    return ordenar(filtradas, coluna, decrescente);
  }, [linhas, busca, soComErro, coluna, decrescente]);

  const ordenarPor = (c: Coluna) => {
    if (c === coluna) {
      setDecrescente(!decrescente);
    } else {
      setColuna(c);
      setDecrescente(c !== 'api');
    }
  };

  return (
    <>
      <div className="rq-filtros">
        <input
          type="search"
          className="rq-busca"
          placeholder="Filtrar por rota ou método"
          aria-label="Filtrar por rota ou método"
          value={busca}
          onChange={e => setBusca(e.target.value)}
        />
        <label className="rq-check">
          <input type="checkbox" checked={soComErro} onChange={e => setSoComErro(e.target.checked)} /> Só APIs com erro
        </label>
        <span className="rq-contagem">
          {visiveis.length} de {linhas.length} APIs
        </span>
      </div>
      <div className="rq-tabela-rolagem">
        <table className="rq-tabela">
          <thead>
            <tr>
              {COLUNAS.map(c => (
                <th
                  key={c.id}
                  scope="col"
                  title={c.titulo}
                  className={c.id === 'api' ? 'rq-col-api' : 'rq-col-num'}
                  aria-sort={coluna === c.id ? (decrescente ? 'descending' : 'ascending') : 'none'}
                >
                  <button type="button" onClick={() => ordenarPor(c.id)}>
                    {c.rotulo}
                    {coluna === c.id && <span aria-hidden="true">{decrescente ? ' ▾' : ' ▴'}</span>}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visiveis.length === 0 ? (
              <tr>
                <td colSpan={COLUNAS.length} className="rq-vazio">
                  {linhas.length === 0 ? 'Nenhuma requisição desde que o servidor subiu.' : 'Nenhuma API com esse filtro.'}
                </td>
              </tr>
            ) : (
              visiveis.map(l => (
                <React.Fragment key={l.chave}>
                  <tr className={`rq-linha${aberta === l.chave ? ' rq-linha-aberta' : ''}`}>
                    <td className="rq-col-api">
                      <button
                        type="button"
                        className="rq-rota"
                        aria-expanded={aberta === l.chave}
                        onClick={() => setAberta(aberta === l.chave ? null : l.chave)}
                      >
                        <span className={`rq-metodo rq-metodo-${l.metodo?.toLowerCase()}`}>{l.metodo}</span>
                        <span className="rq-uri">{l.uri}</span>
                      </button>
                    </td>
                    <td className="rq-col-num">{l.porSegundo === null ? '—' : formatarNumero(l.porSegundo, 1)}</td>
                    <td className="rq-col-num">{formatarNumero(l.requisicoes)}</td>
                    <td className="rq-col-num">{formatarMs(l.tempoMedioMs)}</td>
                    <td className="rq-col-num">{formatarMs(l.maximoMs)}</td>
                    <td className={`rq-col-num${l.erros4xx ? ' rq-tem-4xx' : ''}`}>{formatarNumero(l.erros4xx)}</td>
                    <td className={`rq-col-num${l.erros5xx ? ' rq-tem-5xx' : ''}`}>{formatarNumero(l.erros5xx)}</td>
                    <td className="rq-col-num">
                      {formatarNumero(l.percentualErro, l.percentualErro > 0 && l.percentualErro < 10 ? 1 : 0)}%
                    </td>
                  </tr>
                  {aberta === l.chave && (
                    <tr className="rq-detalhe-linha">
                      <td colSpan={COLUNAS.length}>
                        <div className="rq-detalhe">
                          <div>
                            <span className="rq-detalhe-rotulo">Respostas por status</span>
                            <ul className="rq-chips">
                              {Object.entries(l.porStatus).map(([status, n]) => (
                                <li key={status} className={classeDoStatus(Number(status))}>
                                  {status} <strong>{formatarNumero(n)}</strong>
                                </li>
                              ))}
                            </ul>
                          </div>
                          <div>
                            <span className="rq-detalhe-rotulo">Exceções</span>
                            {Object.keys(l.excecoes).length === 0 ? (
                              <span className="rq-nada"> nenhuma</span>
                            ) : (
                              <ul className="rq-chips">
                                {Object.entries(l.excecoes).map(([excecao, n]) => (
                                  <li key={excecao} className="rq-status-5xx">
                                    {excecao} <strong>{formatarNumero(n)}</strong>
                                  </li>
                                ))}
                              </ul>
                            )}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
};

type FiltroErro = 'todos' | '4xx' | '5xx';

const ListaErros = ({ erros, total }: { erros: ErroHttp[]; total: number }) => {
  const [filtro, setFiltro] = useState<FiltroErro>('todos');
  const visiveis = erros.filter(e => filtro === 'todos' || (filtro === '5xx' ? e.status >= 500 : e.status >= 400 && e.status < 500));
  const quantos = (f: FiltroErro) =>
    erros.filter(e => f === 'todos' || (f === '5xx' ? e.status >= 500 : e.status >= 400 && e.status < 500)).length;

  return (
    <>
      <div className="rq-filtros" role="group" aria-label="Filtrar erros por status">
        {(['todos', '4xx', '5xx'] as FiltroErro[]).map(f => (
          <button key={f} type="button" className="rq-filtro-erro" aria-pressed={filtro === f} onClick={() => setFiltro(f)}>
            {f === 'todos' ? 'Todos' : f} <strong>{quantos(f)}</strong>
          </button>
        ))}
        <span className="rq-contagem">
          {formatarNumero(total)} desde que o servidor subiu · guardados os últimos {erros.length}
        </span>
      </div>
      {visiveis.length === 0 ? (
        <p className="rq-vazio">{erros.length === 0 ? 'Nenhum erro desde que o servidor subiu.' : 'Nenhum erro com esse filtro.'}</p>
      ) : (
        <ol className="rq-erros">
          {visiveis.map(e => (
            <li key={`${e.instante}-${e.caminho}-${e.status}-${e.excecao}`} className="rq-erro">
              <details>
                <summary>
                  <span className={`rq-status ${classeDoStatus(e.status)}`}>{e.status || '—'}</span>
                  <span className="rq-erro-hora">{horario(e.instante)}</span>
                  <span className="rq-erro-caminho">
                    <span className={`rq-metodo rq-metodo-${e.metodo?.toLowerCase()}`}>{e.metodo}</span> {e.caminho}
                  </span>
                  <span className="rq-erro-excecao">
                    {e.excecao ? (
                      <>
                        <strong>{nomeSimples(e.excecao)}</strong>
                        {e.mensagem && `: ${e.mensagem}`}
                      </>
                    ) : (
                      'sem exceção (resposta de erro do próprio fluxo, como login expirado ou rota inexistente)'
                    )}
                  </span>
                </summary>
                <dl className="rq-erro-detalhe">
                  <dt>Quando</dt>
                  <dd>{new Date(e.instante).toLocaleString('pt-BR')}</dd>
                  <dt>Rota</dt>
                  <dd>{e.rota ?? 'nenhuma (barrado antes de chegar a uma rota)'}</dd>
                  <dt>Duração</dt>
                  <dd>{formatarMs(e.duracaoMs)}</dd>
                  {e.excecao && (
                    <>
                      <dt>Exceção</dt>
                      <dd>{e.excecao}</dd>
                    </>
                  )}
                  {e.mensagem && (
                    <>
                      <dt>Mensagem</dt>
                      <dd>{e.mensagem}</dd>
                    </>
                  )}
                  {e.causaRaiz && (
                    <>
                      <dt>Causa raiz</dt>
                      <dd>{e.causaRaiz}</dd>
                    </>
                  )}
                </dl>
                {e.pilha.length > 0 && (
                  <>
                    <span className="rq-detalhe-rotulo">Pilha de chamadas (» = código do DIGITADO)</span>
                    <pre className="rq-pilha">{e.pilha.join('\n')}</pre>
                  </>
                )}
              </details>
            </li>
          ))}
        </ol>
      )}
    </>
  );
};

export const RequisicoesPorApi = ({ intervalo }: { intervalo: number }) => {
  const [leitura, setLeitura] = useState<LeituraRequisicoes>();
  const [linhas, setLinhas] = useState<LinhaRota[]>([]);
  const [semResposta, setSemResposta] = useState(false);
  const anterior = useRef<LeituraRequisicoes>();

  useEffect(() => {
    let ativo = true;
    let espera: ReturnType<typeof setTimeout>;
    const ler = async () => {
      if (!document.hidden) {
        try {
          const { data } = await axios.get<LeituraRequisicoes>(URL_REQUISICOES);
          if (!ativo) {
            return;
          }
          setLinhas(montarLinhas(data, anterior.current));
          anterior.current = data;
          setLeitura(data);
          setSemResposta(false);
        } catch {
          if (ativo) {
            setSemResposta(true);
          }
        }
      }
      // Pausado: lê uma vez ao abrir e para
      if (ativo && intervalo) {
        espera = setTimeout(ler, Math.max(intervalo, INTERVALO_MINIMO_MS));
      }
    };
    ler();
    return () => {
      ativo = false;
      clearTimeout(espera);
    };
  }, [intervalo]);

  if (!leitura) {
    return (
      <p className="rq-vazio">{semResposta ? 'Não foi possível ler as requisições do servidor.' : 'Lendo as requisições do servidor...'}</p>
    );
  }

  return (
    <div className="rq-secao">
      <section aria-labelledby="rq-titulo-apis">
        <h4 id="rq-titulo-apis" className="rq-titulo">
          Requisições por API
        </h4>
        <p className="rq-sub">
          Clique numa API para ver as respostas por status e as exceções. Clique no título de uma coluna para ordenar.
        </p>
        <TabelaRotas linhas={linhas} />
      </section>
      <section aria-labelledby="rq-titulo-erros">
        <h4 id="rq-titulo-erros" className="rq-titulo">
          Erros recentes
        </h4>
        <p className="rq-sub">
          Cada resposta 4xx ou 5xx, do mais novo para o mais antigo. Clique num erro para ver a exceção, a causa raiz e a pilha de chamadas.
        </p>
        <ListaErros erros={leitura.errosRecentes} total={leitura.errosRegistrados} />
      </section>
      {semResposta && <p className="rq-aviso">Sem resposta do servidor: mostrando a última leitura.</p>}
    </div>
  );
};
