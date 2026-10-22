import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import axios from 'axios';
import { Alert, Button, Input, Table } from 'reactstrap';

/**
 * Consulta das tabelas que o jogo grava e que não tinham tela nenhuma - histórico
 * de respostas, estatísticas de partida, participantes de sala, tentativas da
 * palavra do dia e partidas do modo individual. SÓ PARA ADMIN: a rota fica dentro
 * do PrivateRoute de ADMIN e a API (/api/admin/tabelas) exige a role também.
 *
 * Somente leitura: são registros do jogo, e editar o histórico de alguém mudaria o
 * painel de desempenho dele sem ele ter jogado.
 */

// As tabelas oferecidas no menu de Entidades (o servidor tem a mesma lista)
export const TABELAS_DO_JOGO = [
  { nome: 'historico-respostas', titulo: 'Histórico de respostas' },
  { nome: 'estatisticas-partida', titulo: 'Estatísticas de partida' },
  { nome: 'participantes-sala', titulo: 'Participantes de sala' },
  { nome: 'palavra-do-dia-tentativas', titulo: 'Tentativas da palavra do dia' },
  { nome: 'partidas-individuais', titulo: 'Partidas individuais' },
];

interface TabelaInfo {
  nome: string;
  titulo: string;
  descricao: string;
  filtro: string;
}

interface Tabela {
  nome: string;
  titulo: string;
  colunas: string[];
  linhas: unknown[][];
  total: number;
  pagina: number;
  tamanho: number;
}

const TAMANHO = 20;

// Datas ISO viram data e hora locais; booleano vira sim/não; vazio vira "-"
const formatarCelula = (valor: unknown): string => {
  if (valor === null || valor === undefined || valor === '') return '-';
  if (typeof valor === 'boolean') return valor ? 'sim' : 'não';
  if (typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(valor)) {
    const data = new Date(valor);
    return isNaN(data.getTime()) ? valor : data.toLocaleString('pt-BR');
  }
  return typeof valor === 'object' ? JSON.stringify(valor) : String(valor as string | number);
};

export const TabelaDoJogo: React.FC = () => {
  const { nome } = useParams<{ nome: string }>();
  const [info, setInfo] = useState<TabelaInfo | null>(null);
  const [tabela, setTabela] = useState<Tabela | null>(null);
  const [pagina, setPagina] = useState(0);
  const [busca, setBusca] = useState('');
  const [buscaAplicada, setBuscaAplicada] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  // Trocou de tabela pelo menu: volta para a primeira página, sem filtro
  useEffect(() => {
    setPagina(0);
    setBusca('');
    setBuscaAplicada('');
    axios
      .get<TabelaInfo[]>('/api/admin/tabelas')
      .then(res => setInfo(res.data.find(t => t.nome === nome) ?? null))
      .catch(() => setInfo(null));
  }, [nome]);

  useEffect(() => {
    setCarregando(true);
    axios
      .get<Tabela>(`/api/admin/tabelas/${nome}`, { params: { page: pagina, size: TAMANHO, busca: buscaAplicada || undefined } })
      .then(res => {
        setTabela(res.data);
        setErro(null);
      })
      .catch(err => {
        setTabela(null);
        setErro(err?.response?.status === 403 ? 'Esta tela é só para administradores.' : 'Não foi possível carregar a tabela.');
      })
      .finally(() => setCarregando(false));
  }, [nome, pagina, buscaAplicada]);

  const totalPaginas = tabela ? Math.max(1, Math.ceil(tabela.total / TAMANHO)) : 1;
  const titulo = tabela?.titulo ?? info?.titulo ?? TABELAS_DO_JOGO.find(t => t.nome === nome)?.titulo ?? nome;

  return (
    <div>
      <h2 id="tabela-jogo-heading" data-cy="TabelaJogoHeading">
        {titulo} <small className="text-muted">(somente leitura)</small>
      </h2>
      {info?.descricao && <p className="text-muted">{info.descricao}</p>}

      <form
        className="d-flex gap-2 mb-3"
        onSubmit={e => {
          e.preventDefault();
          setPagina(0);
          setBuscaAplicada(busca.trim());
        }}
      >
        <Input
          value={busca}
          onChange={e => setBusca(e.target.value)}
          placeholder={info?.filtro === 'sala' ? 'Filtrar pelo código da sala' : 'Filtrar pelo login'}
          aria-label="Filtro"
          style={{ maxWidth: 320 }}
        />
        <Button color="primary" type="submit">
          Filtrar
        </Button>
        {buscaAplicada && (
          <Button
            color="secondary"
            outline
            type="button"
            onClick={() => {
              setBusca('');
              setBuscaAplicada('');
              setPagina(0);
            }}
          >
            Limpar
          </Button>
        )}
      </form>

      {erro && <Alert color="danger">{erro}</Alert>}

      {carregando && !tabela ? (
        <p>Carregando...</p>
      ) : tabela && tabela.linhas.length === 0 ? (
        <Alert color="warning">Nenhum registro {buscaAplicada ? `com "${buscaAplicada}"` : 'nesta tabela ainda'}.</Alert>
      ) : (
        tabela && (
          <>
            <div className="table-responsive">
              <Table responsive striped size="sm">
                <thead>
                  <tr>
                    {tabela.colunas.map(c => (
                      <th key={c}>{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {tabela.linhas.map((linha, i) => (
                    <tr key={i} data-cy="entityTable">
                      {linha.map((valor, j) => (
                        <td key={j}>{formatarCelula(valor)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
            <div className="d-flex align-items-center gap-2">
              <Button size="sm" disabled={pagina === 0 || carregando} onClick={() => setPagina(p => p - 1)}>
                ← Anterior
              </Button>
              <span>
                Página {pagina + 1} de {totalPaginas} · {tabela.total} registro{tabela.total === 1 ? '' : 's'}
              </span>
              <Button size="sm" disabled={pagina + 1 >= totalPaginas || carregando} onClick={() => setPagina(p => p + 1)}>
                Próxima →
              </Button>
            </div>
          </>
        )
      )}
    </div>
  );
};

export default TabelaDoJogo;
