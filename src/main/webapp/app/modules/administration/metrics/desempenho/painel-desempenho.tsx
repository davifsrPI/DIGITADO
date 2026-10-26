import './painel-desempenho.scss';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';

import { AmostraDeCor, GraficoDesempenho, comAlfa } from './grafico-desempenho';
import {
  CAPACIDADE_HISTORICO,
  Amostra,
  LeituraDesempenho,
  acrescentar,
  calcularAmostra,
  formatarBytes,
  formatarNumero,
} from './monitor-util';
import { IdPainel, Painel, montarPaineis } from './paineis';
import { RequisicoesPorApi } from './requisicoes-api';

// Aba "Desempenho" de Administração > Métricas, no desenho da aba Desempenho do
// Gerenciador de Tarefas do Windows: à esquerda a lista de recursos, cada um com
// a miniatura do gráfico e o número do momento; à direita o recurso escolhido em
// tamanho grande, com os últimos 60 pontos e os números embaixo.
//
// Lê GET /management/desempenho (só ADMIN) na velocidade escolhida. As leituras
// não se sobrepõem: a próxima só é agendada quando a anterior volta. Com a aba do
// navegador escondida, não pergunta nada ao servidor.

export const URL_DESEMPENHO = 'management/desempenho';

export const VELOCIDADES = [
  { rotulo: 'Alta (0,5 s)', ms: 500 },
  { rotulo: 'Normal (1 s)', ms: 1000 },
  { rotulo: 'Baixa (4 s)', ms: 4000 },
  { rotulo: 'Pausada', ms: 0 },
];

interface Estado {
  leitura?: LeituraDesempenho;
  historico: Amostra[];
  leituras: number;
}

const descricaoDoGrafico = (painel: Painel, janela: string): string => {
  const valores = painel.series[0].valores.filter((v): v is number => v !== null);
  if (!valores.length) {
    return `${painel.titulo}: ainda sem leituras`;
  }
  const f = painel.formatar;
  return `${painel.titulo}, ${painel.rotuloEixo}, ${janela}: agora ${f(valores[valores.length - 1])}, mínimo ${f(
    Math.min(...valores),
  )}, máximo ${f(Math.max(...valores))}`;
};

const Composicao = ({ painel }: { painel: Painel }) => {
  const { titulo, total, segmentos } = painel.composicao;
  return (
    <div className="pd-composicao">
      <div className="pd-rotulo">{titulo}</div>
      <div className="pd-composicao-barra" style={{ borderColor: painel.cor }}>
        {segmentos
          .filter(s => s.bytes > 0)
          .map(s => (
            <div
              key={s.rotulo}
              className="pd-composicao-segmento"
              style={{ flexGrow: s.bytes / total, background: comAlfa(painel.cor, s.opacidade) }}
              title={`${s.rotulo}: ${formatarBytes(s.bytes)}`}
            />
          ))}
      </div>
      <ul className="pd-composicao-legenda">
        {segmentos.map(s => (
          <li key={s.rotulo}>
            <span className="pd-amostra" style={{ background: comAlfa(painel.cor, s.opacidade), border: `1px solid ${painel.cor}` }} />
            {s.rotulo} <strong>{formatarBytes(s.bytes)}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
};

const Detalhe = ({ painel, estado, janela, intervalo }: { painel: Painel; estado: Estado; janela: string; intervalo: number }) => (
  <section className="pd-detalhe" aria-labelledby="pd-detalhe-titulo">
    <header className="pd-detalhe-topo">
      <h3 id="pd-detalhe-titulo">{painel.titulo}</h3>
      <span>{painel.subtitulo}</span>
    </header>

    <div className="pd-eixo">
      <span>{painel.rotuloEixo}</span>
      <span>{painel.rotuloMaximo}</span>
    </div>
    <GraficoDesempenho
      series={painel.series}
      maximo={painel.maximo}
      cor={painel.cor}
      instantes={estado.historico.map(a => a.instante)}
      deslocamento={estado.leituras}
      formatar={painel.formatar}
      descricao={descricaoDoGrafico(painel, janela)}
    />
    <div className="pd-eixo pd-eixo-baixo">
      <span>{janela}</span>
      <span>0</span>
    </div>

    {painel.series.length > 1 && (
      <ul className="pd-legenda" aria-label="Legenda">
        {painel.series.map(s => {
          const agora = s.valores[s.valores.length - 1];
          return (
            <li key={s.nome}>
              <AmostraDeCor cor={painel.cor} estilo={s.estilo} />
              {s.nome} <strong>{agora === null || agora === undefined ? '—' : painel.formatar(agora)}</strong>
            </li>
          );
        })}
      </ul>
    )}

    {painel.composicao && <Composicao painel={painel} />}

    <div className="pd-numeros">
      <div className="pd-destaques">
        {painel.destaques.map(d => (
          <div key={d.rotulo} className="pd-destaque">
            <span className="pd-rotulo">{d.rotulo}</span>
            <span className="pd-valor">{d.valor}</span>
          </div>
        ))}
      </div>
      {painel.especificacoes.length > 0 && (
        <dl className="pd-especificacoes">
          {painel.especificacoes.map(e => (
            <React.Fragment key={e.rotulo}>
              <dt>{e.rotulo}</dt>
              <dd>{e.valor}</dd>
            </React.Fragment>
          ))}
        </dl>
      )}
    </div>

    {painel.nota && <p className="pd-nota">{painel.nota}</p>}

    {/* Só no item HTTP: a tabela por API e os erros (leitura própria, maior) */}
    {painel.id === 'http' && <RequisicoesPorApi intervalo={intervalo} />}
  </section>
);

export const PainelDesempenho = () => {
  const [intervalo, setIntervalo] = useState(1000);
  const [estado, setEstado] = useState<Estado>({ historico: [], leituras: 0 });
  const [semResposta, setSemResposta] = useState(false);
  const [selecionado, setSelecionado] = useState<IdPainel>('cpu');
  const anterior = useRef<LeituraDesempenho>();
  // A janela do eixo continua a da última velocidade enquanto está pausado
  const ultimoIntervalo = useRef(intervalo);
  if (intervalo) {
    ultimoIntervalo.current = intervalo;
  }

  useEffect(() => {
    if (!intervalo) {
      return undefined;
    }
    let ativo = true;
    let espera: ReturnType<typeof setTimeout>;
    const ler = async () => {
      if (!document.hidden) {
        try {
          const { data } = await axios.get<LeituraDesempenho>(URL_DESEMPENHO);
          if (!ativo) {
            return;
          }
          const amostra = calcularAmostra(data, anterior.current);
          anterior.current = data;
          setEstado(e => ({ leitura: data, historico: acrescentar(e.historico, amostra), leituras: e.leituras + 1 }));
          setSemResposta(false);
        } catch {
          if (ativo) {
            setSemResposta(true);
          }
        }
      }
      if (ativo) {
        espera = setTimeout(ler, intervalo);
      }
    };
    ler();
    return () => {
      ativo = false;
      clearTimeout(espera);
    };
  }, [intervalo]);

  const paineis = useMemo(() => (estado.leitura ? montarPaineis(estado.leitura, estado.historico) : []), [estado]);
  const painel = paineis.find(p => p.id === selecionado) ?? paineis[0];
  const janela = `${formatarNumero((CAPACIDADE_HISTORICO * ultimoIntervalo.current) / 1000)} segundos`;

  let status = { classe: 'ao-vivo', texto: 'Ao vivo' };
  if (semResposta) {
    status = { classe: 'erro', texto: 'Sem resposta do servidor, tentando de novo' };
  } else if (!intervalo) {
    status = { classe: 'pausado', texto: 'Pausado' };
  }

  return (
    <div className="pd-painel" data-cy="painelDesempenho">
      <div className="pd-barra">
        <span className={`pd-status pd-status-${status.classe}`} role="status">
          <span className="pd-status-ponto" aria-hidden="true" />
          {status.texto}
        </span>
        <label className="pd-velocidade">
          Velocidade de atualização
          <select value={intervalo} onChange={e => setIntervalo(Number(e.target.value))}>
            {VELOCIDADES.map(v => (
              <option key={v.ms} value={v.ms}>
                {v.rotulo}
              </option>
            ))}
          </select>
        </label>
      </div>

      {painel ? (
        <div className="pd-corpo">
          <nav className="pd-lista" aria-label="Recursos">
            {paineis.map(p => (
              <button
                key={p.id}
                type="button"
                className={`pd-item${p.id === painel.id ? ' ativo' : ''}`}
                style={{ '--pd-cor-item': p.cor } as React.CSSProperties}
                aria-pressed={p.id === painel.id}
                onClick={() => setSelecionado(p.id)}
              >
                <GraficoDesempenho mini series={p.series} maximo={p.maximo} cor={p.cor} descricao={`${p.titulo}: ${p.resumo}`} />
                <span className="pd-item-texto">
                  <span className="pd-item-titulo">{p.titulo}</span>
                  <span className="pd-item-resumo">{p.resumo}</span>
                </span>
              </button>
            ))}
          </nav>
          <Detalhe painel={painel} estado={estado} janela={janela} intervalo={intervalo} />
        </div>
      ) : (
        <div className="pd-carregando">
          {semResposta ? 'Não foi possível ler o desempenho do servidor.' : 'Lendo o desempenho do servidor...'}
        </div>
      )}
    </div>
  );
};

export default PainelDesempenho;
