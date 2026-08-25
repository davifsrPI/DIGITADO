import 'app/shared/components/desempenho/desempenho.scss';
import './meu-desempenho.scss';

import React, { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import axios from 'axios';

import { useAppSelector } from 'app/config/store';
import { hasAnyAuthority } from 'app/shared/auth/private-route';
import { AUTHORITIES } from 'app/config/constants';
import { useBodyClass } from 'app/shared/util/use-body-class';
import { CORES_DIFICULDADE, LABELS_DIFICULDADE } from 'app/shared/util/dificuldade-constants';
import { Acelerador, CORES_NIVEL, NivelDesempenho } from 'app/shared/components/desempenho/acelerador';
import { GraficoEvolucao, PontoEvolucao } from 'app/shared/components/desempenho/grafico-evolucao';

// Tela "Meu Desempenho": a linha da vida do jogador no DIGITADO, da primeira
// resposta até hoje.
//
// PRIVACIDADE: o front NÃO envia login, id nem filtro nenhum. Pede
// GET /api/meu-desempenho e o backend responde com o histórico de quem está no
// token, não existe endpoint que aceite pedir o desempenho de outra pessoa,
// então nem trocando a URL um aluno vê o de um colega.
//
// ADMIN não tem painel pessoal: quem tem ROLE_ADMIN é mandado para o resumo de
// todos os usuários. O admin joga para testar o sistema, então a evolução dele
// não mede aprendizado, e as respostas dele já ficam de fora do relatório da
// turma. O desvio está aqui e não só no menu para valer também quando a URL é
// digitada à mão.

interface PalavraDesempenho {
  palavraId: number;
  texto: string;
  dificuldade: string | null;
  total: number;
  acertos: number;
  erros: number;
  taxa: number;
}

interface FaixaDificuldade {
  dificuldade: string;
  total: number;
  acertos: number;
  taxa: number;
}

interface TipoErro {
  tipo: string;
  total: number;
  percentual: number;
}

interface MeuDesempenho {
  primeiraResposta: string | null;
  ultimaResposta: string | null;
  totalRespostas: number;
  totalAcertos: number;
  totalErros: number;
  taxaGeral: number;
  taxaRecente: number;
  tendencia: number;
  nivel: NivelDesempenho;
  mensagemNivel: string;
  sequenciaAtual: number;
  melhorSequencia: number;
  porDificuldade: FaixaDificuldade[];
  evolucao: PontoEvolucao[];
  maisErradas: PalavraDesempenho[];
  maisAcertadas: PalavraDesempenho[];
  errosPorTipo: TipoErro[];
}

// Rótulos amigáveis dos tipos de erro (espelho do enum TipoErro do backend)
const LABELS_ERRO: Record<string, string> = {
  ACENTUACAO: 'Acentuação',
  TROCA_LETRA: 'Troca de letra',
  LETRA_FALTANDO: 'Letra faltando',
  LETRA_EXTRA: 'Letra sobrando',
  ERRO_FONETICO: 'Erro fonético',
  OUTRO: 'Outro',
};

const formatarData = (iso: string | null): string => {
  if (!iso) return '-';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '-';
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'long', year: 'numeric' });
};

// Há quantos dias a pessoa joga (contado a partir da primeira resposta)
const diasDesde = (iso: string | null): number => {
  if (!iso) return 0;
  const d = new Date(iso).getTime();
  if (isNaN(d)) return 0;
  return Math.max(1, Math.round((Date.now() - d) / 86400000));
};

const corDificuldade = (dif: string | null): string => CORES_DIFICULDADE[dif ?? ''] ?? '#94a3b8';
const labelDificuldade = (dif: string | null): string => LABELS_DIFICULDADE[dif ?? ''] ?? dif ?? '-';

// Lista de palavras (mais erradas / mais acertadas), o número em destaque muda
// conforme o ranking: quantas vezes errou ou quantas vezes acertou
const ListaPalavras: React.FC<{ palavras: PalavraDesempenho[]; modo: 'erro' | 'acerto'; vazio: string }> = ({ palavras, modo, vazio }) => {
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
            {modo === 'erro' ? `${p.erros} erro${p.erros === 1 ? '' : 's'}` : `${p.acertos} acerto${p.acertos === 1 ? '' : 's'}`} em{' '}
            {p.total}
          </span>
          <span className="dp-palavra-taxa" style={{ color: modo === 'erro' ? CORES_NIVEL.RUIM : CORES_NIVEL.BOM }}>
            {p.taxa}%
          </span>
        </div>
      ))}
    </div>
  );
};

export const MeuDesempenho: React.FC = () => {
  const navigate = useNavigate();
  const ehAdmin = useAppSelector(state => hasAnyAuthority(state.authentication.account.authorities, [AUTHORITIES.ADMIN]));
  const [dados, setDados] = useState<MeuDesempenho | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);

  useBodyClass('meu-desempenho-page');

  useEffect(() => {
    // Admin não consome este endpoint, a tela dele é o resumo de todos
    if (ehAdmin) return;
    setCarregando(true);
    axios
      .get<MeuDesempenho>('/api/meu-desempenho')
      .then(res => {
        setDados(res.data);
        setErro(false);
      })
      .catch(() => setErro(true))
      .finally(() => setCarregando(false));
  }, [ehAdmin]);

  if (ehAdmin) {
    return <Navigate to="/admin/desempenho" replace />;
  }

  const semHistorico = dados != null && dados.totalRespostas === 0;

  return (
    <div className="md-wrapper">
      <div className="md-bg">
        <div className="md-shape one" />
        <div className="md-shape two" />
        <div className="md-shape three" />
      </div>

      <div className="md-center">
        <button className="md-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <div className="md-header">
          <h1 className="md-title">📈 Meu Desempenho</h1>
          <p className="md-subtitle">
            Sua evolução no DIGITADO, do primeiro dia até hoje. Estes números são <strong>só seus</strong>, ninguém mais tem acesso a esta
            tela.
          </p>
        </div>

        {carregando ? (
          <div className="md-loading">Carregando seu histórico...</div>
        ) : erro ? (
          <div className="md-empty">
            <div className="md-empty-icon">⚠️</div>
            <p>Não foi possível carregar seu desempenho. Tente novamente mais tarde.</p>
          </div>
        ) : semHistorico ? (
          <div className="md-empty">
            <div className="md-empty-icon">📊</div>
            <p>{dados?.mensagemNivel}</p>
            <button className="md-cta" onClick={() => navigate('/lobby')}>
              Jogar agora
            </button>
          </div>
        ) : (
          dados && (
            <>
              {/* O acelerador: como a pessoa está AGORA */}
              <div className="dp-card md-acelerador-card">
                <h2 className="dp-card-titulo">Como você está hoje</h2>
                <Acelerador valor={dados.taxaRecente} nivel={dados.nivel} mensagem={dados.mensagemNivel} tendencia={dados.tendencia} />
              </div>

              {/* Retrato desde o começo */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">Desde o começo</h2>
                <p className="dp-card-sub">
                  Primeira resposta em {formatarData(dados.primeiraResposta)} · {diasDesde(dados.primeiraResposta)} dia(s) de jogo · última
                  em {formatarData(dados.ultimaResposta)}
                </p>
                <div className="dp-kpis">
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor">{dados.totalRespostas}</span>
                    <span className="dp-kpi-label">Palavras respondidas</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor" style={{ color: CORES_NIVEL.BOM }}>
                      {dados.totalAcertos}
                    </span>
                    <span className="dp-kpi-label">Acertos</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor" style={{ color: CORES_NIVEL.RUIM }}>
                      {dados.totalErros}
                    </span>
                    <span className="dp-kpi-label">Erros</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor">{dados.taxaGeral}%</span>
                    <span className="dp-kpi-label">Assertividade geral</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor">{dados.sequenciaAtual}</span>
                    <span className="dp-kpi-label">Acertos seguidos agora</span>
                  </div>
                  <div className="dp-kpi">
                    <span className="dp-kpi-valor">{dados.melhorSequencia}</span>
                    <span className="dp-kpi-label">Melhor sequência recente</span>
                  </div>
                </div>
              </div>

              {/* Linha do tempo mês a mês */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">Assertividade mês a mês</h2>
                <p className="dp-card-sub">
                  A linha é a sua taxa de acerto; as barras ao fundo mostram quantas palavras você respondeu em cada mês.
                </p>
                <GraficoEvolucao
                  pontos={dados.evolucao}
                  cor="#818cf8"
                  vazio="Você ainda não tem meses suficientes para uma linha do tempo."
                />
              </div>

              {/* Onde a pessoa acerta e onde tropeça */}
              <div className="dp-duas-colunas">
                <div className="dp-card">
                  <h2 className="dp-card-titulo">Por dificuldade</h2>
                  {dados.porDificuldade.length === 0 ? (
                    <p className="dp-vazio">Sem dados por dificuldade ainda.</p>
                  ) : (
                    <div className="dp-barras">
                      {dados.porDificuldade.map(f => (
                        <div key={f.dificuldade} className="dp-barra-linha">
                          <div className="dp-barra-topo">
                            <span className="dp-barra-nome">
                              <span className="dp-barra-ponto" style={{ background: corDificuldade(f.dificuldade) }} />
                              {labelDificuldade(f.dificuldade)}
                            </span>
                            <span className="dp-barra-valor">
                              {f.acertos}/{f.total} · {f.taxa}%
                            </span>
                          </div>
                          <div className="dp-barra-trilho">
                            <div
                              className="dp-barra-preenchida"
                              style={{ width: `${f.taxa}%`, background: corDificuldade(f.dificuldade) }}
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="dp-card">
                  <h2 className="dp-card-titulo">Tipos de erro</h2>
                  {dados.errosPorTipo.length === 0 ? (
                    <p className="dp-vazio">Você ainda não errou nenhuma palavra. 🎉</p>
                  ) : (
                    <div className="dp-chips">
                      {dados.errosPorTipo.map(e => (
                        <span key={e.tipo} className="dp-chip">
                          {LABELS_ERRO[e.tipo] ?? e.tipo}: <strong>{e.total}</strong> ({e.percentual}%)
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Os dois rankings pessoais */}
              <div className="dp-card">
                <h2 className="dp-card-titulo">Palavras que você mais erra</h2>
                <p className="dp-card-sub">Comece o treino por aqui: são as que mais aparecem escritas errado no seu histórico.</p>
                <ListaPalavras palavras={dados.maisErradas} modo="erro" vazio="Nenhuma palavra errada até agora. 🎉" />
              </div>

              <div className="dp-card">
                <h2 className="dp-card-titulo">Palavras que você mais acerta</h2>
                <p className="dp-card-sub">Seu repertório já dominado.</p>
                <ListaPalavras palavras={dados.maisAcertadas} modo="acerto" vazio="Ainda não há palavras acertadas para listar." />
              </div>
            </>
          )
        )}
      </div>
    </div>
  );
};

export default MeuDesempenho;
