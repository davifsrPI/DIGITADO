import './sala-jogo-styles.scss';

import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';

import { useBodyClass } from 'app/shared/util/use-body-class';
import { PosicaoRanking, RankingPartida, RelatorioPalavra, RelatorioPorPalavra } from './relatorio-partida';

// Tela "Ver estatísticas": o desempenho da turma na ÚLTIMA partida da sala.
//
// Antes, o relatório só existia enquanto o jogo estava na memória do servidor,
// fechar a sala apagava tudo, e reabri-la devolvia uma sala zerada, do começo.
// Agora o servidor grava um snapshot ao encerrar a partida, e esta tela lê dele:
// funciona com a sala aberta ou fechada, sem precisar reabrir nada.
//
// Restrita ao professor dono (ou admin), é o mesmo controle do endpoint, que
// responde 400 "Acesso negado" para qualquer outro usuário.

// Resposta de GET /api/salas/{codigo}/estatisticas
interface Estatisticas {
  dataEncerramento: string;
  totalPalavras: number;
  ranking: PosicaoRanking[];
  relatorio: RelatorioPalavra[];
}

// Data/hora do encerramento no formato brasileiro (ex: 24/08/2026 às 14:32)
const formatarData = (iso: string): string => {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return `${d.toLocaleDateString('pt-BR')} às ${d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`;
};

export const SalaEstatisticas: React.FC = () => {
  const { codigo } = useParams<{ codigo: string }>();
  const navigate = useNavigate();

  const [estatisticas, setEstatisticas] = useState<Estatisticas | null>(null);
  const [carregando, setCarregando] = useState(true);
  // Mensagem de erro/estado vazio, separa "ainda não houve partida" de "não pode ver"
  const [erro, setErro] = useState<string | null>(null);

  // Remove o card branco padrão do layout, a página tem fundo escuro próprio
  useBodyClass('sala-jogo-page');

  useEffect(() => {
    setCarregando(true);
    axios
      .get<Estatisticas>(`/api/salas/${codigo}/estatisticas`)
      .then(res => {
        setEstatisticas(res.data);
        setErro(null);
      })
      .catch(err => {
        setEstatisticas(null);
        // 404: a sala existe mas nenhuma partida chegou ao fim ainda
        setErro(
          err?.response?.status === 404
            ? 'Esta sala ainda não tem estatísticas, elas são gravadas quando uma partida é encerrada.'
            : 'Não foi possível carregar as estatísticas desta sala.',
        );
      })
      .finally(() => setCarregando(false));
  }, [codigo]);

  return (
    <div className="sj-wrapper">
      <div className="sj-bg">
        <div className="sj-shape sj-shape-one" />
        <div className="sj-shape sj-shape-two" />
      </div>

      <div className="sj-container">
        <button className="sj-back-btn" onClick={() => navigate('/minhas-salas')}>
          ← Voltar às minhas salas
        </button>

        {carregando ? (
          <div style={{ color: 'rgba(255,255,255,0.6)', textAlign: 'center', padding: '60px 0', fontSize: 15 }}>
            Carregando estatísticas...
          </div>
        ) : (
          <div className="sj-ended">
            <h2 className="sj-ended-title">Desempenho da turma</h2>

            {erro ? (
              <p className="sj-no-alunos">{erro}</p>
            ) : (
              estatisticas && (
                <>
                  <p className="sj-estat-legenda">
                    Sala {codigo} · {estatisticas.totalPalavras} palavra{estatisticas.totalPalavras === 1 ? '' : 's'} · partida encerrada em{' '}
                    {formatarData(estatisticas.dataEncerramento)}
                  </p>

                  <h3 className="sj-rel-secao">Ranking da partida</h3>
                  <RankingPartida posicoes={estatisticas.ranking} />

                  <h3 className="sj-rel-secao">Relatório por palavra</h3>
                  <RelatorioPorPalavra relatorio={estatisticas.relatorio} />
                </>
              )
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default SalaEstatisticas;
