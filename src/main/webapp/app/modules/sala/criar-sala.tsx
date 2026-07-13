import './criar-sala.scss';

import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import axios from 'axios';

// Gera um código de 6 caracteres aleatórios para a sala, excluindo letras/números confusos (O, I, 1, 0)
const generateCode = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
};

type Dificuldade = 'FACIL' | 'MEDIO' | 'DIFICIL';

interface Palavra {
  id: number;
  texto: string;
  dificuldade: Dificuldade;
  // false enquanto ninguém jogou a palavra — a dificuldade ainda é provisória
  temRegistros?: boolean;
  categoria?: string;
}

// Rótulo da dificuldade exibido na busca: palavra sem estatística ainda não tem
// dificuldade real (a métrica depende de tentativas), então mostra "Sem registros"
const labelDificuldade = (p: Palavra) => (p.temRegistros ? DIFF_LABELS[p.dificuldade] : 'Sem registros');

interface BuscaResult {
  status: 'idle' | 'loading' | 'found' | 'similar' | 'notfound';
  palavra?: Palavra;
  similares?: Palavra[];
}

const DIFFS: Array<{ key: Dificuldade; color: string; label: string }> = [
  { key: 'FACIL', color: '#4ade80', label: 'Fáceis' },
  { key: 'MEDIO', color: '#fbbf24', label: 'Médias' },
  { key: 'DIFICIL', color: '#f87171', label: 'Difíceis' },
];

const DIFF_LABELS: Record<Dificuldade, string> = { FACIL: 'Fácil', MEDIO: 'Médio', DIFICIL: 'Difícil' };

export const CriarSala = () => {
  const navigate = useNavigate();
  // A mesma tela configura salas de turma e duelos 1v1 — o modo vem da URL (?modo=1v1)
  const [searchParams] = useSearchParams();
  const is1v1 = searchParams.get('modo') === '1v1';

  // ─── Estado do formulário ──────────────────────────────────────────────────
  const [nome, setNome] = useState('');
  const [descricao, setDescricao] = useState('');
  const [codigo, setCodigo] = useState(generateCode());
  // Visibilidade — escolha exclusiva do 1v1: pública entra na lista global de duelos;
  // privada só entra quem tiver o código
  const [privada, setPrivada] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Tempo de rodada POR DIFICULDADE (padrão: fáceis mais rápidas, difíceis mais lentas)
  const [tempos, setTempos] = useState<Record<Dificuldade, number>>({ FACIL: 20, MEDIO: 30, DIFICIL: 45 });
  const [quantidades, setQuantidades] = useState<Record<Dificuldade, number>>({ FACIL: 5, MEDIO: 5, DIFICIL: 5 });
  const [wordSearch, setWordSearch] = useState('');
  const [busca, setBusca] = useState<BuscaResult>({ status: 'idle' });
  const [extraWords, setExtraWords] = useState<Palavra[]>([]);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Palavras JÁ SORTEADAS para a partida (só sala de turma): o professor vê a lista
  // e pode trocar cada uma; a partida usa exatamente essas palavras
  const [sorteadas, setSorteadas] = useState<Palavra[]>([]);
  const [sorteioLoading, setSorteioLoading] = useState(false);
  const [sorteioAviso, setSorteioAviso] = useState<string | null>(null);
  // Id da palavra sendo trocada pelo "gerar outra" (desabilita o botão dela)
  const [regenId, setRegenId] = useState<number | null>(null);
  const sorteioDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Nº de sequência do sorteio: descarta respostas de requisições obsoletas
  // (o professor pode clicar +/− várias vezes antes de a primeira resposta chegar)
  const sorteioSeqRef = useRef(0);
  // Espelhos em ref para o sorteio ler o valor atual sem recriar o efeito
  const sorteadasRef = useRef<Palavra[]>([]);
  sorteadasRef.current = sorteadas;
  const extraWordsRef = useRef<Palavra[]>([]);
  extraWordsRef.current = extraWords;

  // Adiciona classe ao body para aplicar estilos de fundo específicos desta página
  useEffect(() => {
    document.body.classList.add('criar-sala-page');
    return () => document.body.classList.remove('criar-sala-page');
  }, []);

  // Incrementa/decrementa a quantidade de palavras de uma dificuldade, limitado entre 0 e 30
  const adj = (key: Dificuldade, delta: number) => {
    setQuantidades(prev => ({ ...prev, [key]: Math.max(0, Math.min(30, prev[key] + delta)) }));
  };

  // Sorteia palavras no servidor, excluindo as que já estão na tela (sorteadas + extras)
  const sortearNoServidor = async (dificuldade: Dificuldade, quantidade: number, excluirExtra: number[] = []) => {
    const excluir = [...sorteadasRef.current.map(w => w.id), ...extraWordsRef.current.map(w => w.id), ...excluirExtra];
    const { data } = await axios.get<Palavra[]>('/api/palavras/sortear', {
      params: { dificuldade, quantidade, excluirIds: excluir.length ? excluir.join(',') : undefined },
    });
    return data;
  };

  // Ajusta a lista de sorteadas às quantidades escolhidas: excedentes saem do fim
  // de cada faixa; o que falta é sorteado no servidor sem repetir as já exibidas
  const reconciliarSorteio = async (qtds: Record<Dificuldade, number>) => {
    const seq = ++sorteioSeqRef.current;
    setSorteioLoading(true);
    let lista = [...sorteadasRef.current];
    for (const { key } of DIFFS) {
      const atuais = lista.filter(w => w.dificuldade === key).length;
      if (atuais > qtds[key]) {
        let excesso = atuais - qtds[key];
        for (let i = lista.length - 1; i >= 0 && excesso > 0; i--) {
          if (lista[i].dificuldade === key) {
            lista.splice(i, 1);
            excesso--;
          }
        }
      } else if (atuais < qtds[key]) {
        try {
          const novas = await sortearNoServidor(
            key,
            qtds[key] - atuais,
            lista.map(w => w.id),
          );
          if (seq !== sorteioSeqRef.current) return; // outra reconciliação já começou
          lista = [...lista, ...novas];
        } catch {
          // banco indisponível: mantém o que já tem — a partida completa o resto ao iniciar
        }
      }
    }
    if (seq === sorteioSeqRef.current) {
      setSorteadas(lista);
      setSorteioLoading(false);
    }
  };

  // Re-sorteia quando as quantidades mudam (com debounce — os cliques em +/− são rápidos).
  // Só na sala de turma: no duelo 1v1 o criador também joga, então ver as palavras
  // antes da partida seria vantagem injusta — lá o sorteio continua às cegas no início.
  useEffect(() => {
    if (is1v1) return;
    if (sorteioDebounceRef.current) clearTimeout(sorteioDebounceRef.current);
    sorteioDebounceRef.current = setTimeout(() => {
      void reconciliarSorteio(quantidades);
    }, 300);
    return () => {
      if (sorteioDebounceRef.current) clearTimeout(sorteioDebounceRef.current);
    };
  }, [quantidades, is1v1]);

  // Troca uma palavra sorteada por outra da mesma dificuldade que ainda não esteja na tela
  const gerarOutra = async (palavra: Palavra) => {
    setRegenId(palavra.id);
    setSorteioAviso(null);
    try {
      const novas = await sortearNoServidor(palavra.dificuldade, 1);
      if (novas.length > 0) {
        setSorteadas(prev => prev.map(w => (w.id === palavra.id ? novas[0] : w)));
      } else {
        setSorteioAviso(`O banco não tem outra palavra ${DIFF_LABELS[palavra.dificuldade].toLowerCase()} disponível.`);
      }
    } catch {
      setSorteioAviso('Não foi possível sortear outra palavra. Tente novamente.');
    } finally {
      setRegenId(null);
    }
  };

  // Busca palavras no banco com debounce de 500ms — evita requisição a cada tecla digitada
  const handleWordSearch = (val: string) => {
    setWordSearch(val);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!val.trim()) {
      setBusca({ status: 'idle' });
      return;
    }
    setBusca({ status: 'loading' });
    debounceRef.current = setTimeout(async () => {
      try {
        const { data } = await axios.get<{
          encontrada: boolean;
          exata: boolean;
          palavra?: Palavra;
          similares?: Palavra[];
        }>('/api/palavras/buscar', { params: { texto: val.trim() } });

        if (data.encontrada && data.exata && data.palavra) {
          setBusca({ status: 'found', palavra: data.palavra });
        } else if (data.similares && data.similares.length > 0) {
          setBusca({ status: 'similar', similares: data.similares });
        } else {
          setBusca({ status: 'notfound' });
        }
      } catch {
        setBusca({ status: 'notfound' });
      }
    }, 500);
  };

  // Adiciona a palavra à lista de extras — evita duplicatas pelo ID
  const addWord = (palavra: Palavra) => {
    if (!extraWords.find(w => w.id === palavra.id)) {
      setExtraWords(prev => [...prev, palavra]);
    }
    setWordSearch('');
    setBusca({ status: 'idle' });
  };

  const removeWord = (id: number) => setExtraWords(prev => prev.filter(w => w.id !== id));

  // Cria a sala via API — tenta até 5 vezes se o código já existir, gerando um novo a cada tentativa.
  // Ao criar com sucesso, navega para a tela de espera da sala: o professor vê quem
  // está entrando e inicia quando quiser — inclusive com a sala vazia.
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    let tentativas = 0;
    let codigoTentativa = codigo;
    while (tentativas < 5) {
      try {
        const res = await axios.post('/api/salas', {
          nome,
          codigo: codigoTentativa,
          descricao: descricao || null,
          ativo: true,
          tipo: is1v1 ? 'UM_V_UM' : 'TURMA',
          // O backend força privada=true para salas de turma; só o 1v1 escolhe
          privada: is1v1 ? privada : true,
        });
        navigate(`/sala/${res.data.codigo}`, {
          state: {
            isProfessor: true,
            gameConfig: {
              tempoFacil: tempos.FACIL,
              tempoMedio: tempos.MEDIO,
              tempoDificil: tempos.DIFICIL,
              qtdFacil: quantidades.FACIL,
              qtdMedio: quantidades.MEDIO,
              qtdDificil: quantidades.DIFICIL,
              palavrasExtrasIds: extraWords.map(w => w.id),
              // Palavras já sorteadas e conferidas nesta tela — a partida usa exatamente
              // essas (vazio no 1v1, onde o sorteio continua acontecendo só ao iniciar)
              palavrasIds: sorteadas.map(w => w.id),
            },
          },
        });
        return;
      } catch (err: any) {
        if (err?.response?.data?.errorKey === 'codigoexists') {
          codigoTentativa = generateCode();
          setCodigo(codigoTentativa);
          tentativas++;
        } else {
          const detail = err?.response?.data?.detail || err?.response?.data?.title || err?.message;
          setError(detail ? `Erro: ${detail}` : 'Não foi possível criar a sala. Tente novamente.');
          break;
        }
      }
    }
    setSubmitting(false);
  };

  const total = quantidades.FACIL + quantidades.MEDIO + quantidades.DIFICIL;

  return (
    <div className="cs-wrapper">
      <div className="cs-bg">
        <div className="cs-shape one" />
        <div className="cs-shape two" />
        <div className="cs-shape three" />
      </div>

      <div className="cs-center cs-center--wide">
        <button className="cs-back" onClick={() => navigate(is1v1 ? '/duelo' : '/lobby')}>
          {is1v1 ? '← Voltar aos duelos' : '← Voltar ao lobby'}
        </button>

        <div className="cs-grid">
          {/* ---- COLUNA ESQUERDA ---- */}
          <div className="cs-col">
            {/* PASSO 1 */}
            <div className="cs-card">
              <span className="cs-step-label">Passo 1</span>
              <h2 className="cs-step-title">{is1v1 ? 'Informações do duelo 1v1' : 'Informações da sala'}</h2>

              {error && <div className="cs-error">{error}</div>}

              <form id="sala-form" onSubmit={handleSubmit} className="cs-form">
                {/* Visibilidade — APENAS no duelo 1v1: pública aparece na lista global;
                    privada exige o código de acesso */}
                {is1v1 && (
                  <div className="cs-field">
                    <label>Visibilidade do duelo</label>
                    <div className="cs-visibility">
                      <button
                        type="button"
                        className={`cs-vis-btn${!privada ? ' cs-vis-btn--active' : ''}`}
                        onClick={() => setPrivada(false)}
                      >
                        🌎 Pública
                        <span className="cs-vis-hint">Qualquer jogador pode entrar pela lista de duelos</span>
                      </button>
                      <button
                        type="button"
                        className={`cs-vis-btn${privada ? ' cs-vis-btn--active' : ''}`}
                        onClick={() => setPrivada(true)}
                      >
                        🔒 Privada
                        <span className="cs-vis-hint">Só entra quem tiver o código da sala</span>
                      </button>
                    </div>
                  </div>
                )}

                <div className="cs-field">
                  <label htmlFor="cs-nome">{is1v1 ? 'Nome do duelo' : 'Nome da sala'}</label>
                  <input
                    id="cs-nome"
                    type="text"
                    placeholder={is1v1 ? 'Ex: Duelo relâmpago ⚡' : 'Ex: Turma 3A — Aula de Ortografia'}
                    value={nome}
                    onChange={e => setNome(e.target.value)}
                    required
                    maxLength={80}
                  />
                </div>

                <div className="cs-field">
                  <label htmlFor="cs-descricao">
                    Descrição <span className="cs-optional">(opcional)</span>
                  </label>
                  <textarea
                    id="cs-descricao"
                    placeholder="Objetivo, tema ou instruções para os alunos..."
                    value={descricao}
                    onChange={e => setDescricao(e.target.value)}
                    rows={2}
                    maxLength={500}
                  />
                </div>

                {/* Tempo de rodada por dificuldade — um slider para cada nível */}
                {DIFFS.map(({ key, color, label }) => (
                  <div className="cs-field" key={key}>
                    <label>
                      <span className="cs-diff-dot" style={{ background: color, display: 'inline-block', marginRight: 6 }} />
                      Tempo — {label}: <span className="cs-range-val">{tempos[key]}s</span>
                    </label>
                    <input
                      type="range"
                      min={10}
                      max={60}
                      step={5}
                      value={tempos[key]}
                      onChange={e => setTempos(prev => ({ ...prev, [key]: Number(e.target.value) }))}
                      className="cs-range"
                    />
                  </div>
                ))}
              </form>
            </div>

            {/* PASSO 2 — QUANTIDADES */}
            <div className="cs-card">
              <span className="cs-step-label">Passo 2</span>
              <h2 className="cs-step-title">Palavras por dificuldade</h2>
              <p className="cs-step-sub">
                Total: <strong>{total}</strong> palavras sorteadas do banco
              </p>

              <div className="cs-diffs">
                {DIFFS.map(({ key, color, label }) => (
                  <div className="cs-diff-row" key={key}>
                    <div className="cs-diff-label">
                      <span className="cs-diff-dot" style={{ background: color }} />
                      <span>{label}</span>
                    </div>
                    <div className="cs-diff-controls">
                      <button type="button" className="cs-adj-btn" onClick={() => adj(key, -1)}>
                        −
                      </button>
                      <span className="cs-diff-val">{quantidades[key]}</span>
                      <button type="button" className="cs-adj-btn" onClick={() => adj(key, 1)}>
                        +
                      </button>
                    </div>
                    <div className="cs-diff-bar-wrap">
                      <div
                        className="cs-diff-bar"
                        style={{ width: total > 0 ? `${Math.round((quantidades[key] / total) * 100)}%` : '0%', background: color }}
                      />
                    </div>
                  </div>
                ))}
              </div>

              {/* Palavras sorteadas para a partida — visíveis só na sala de turma
                  (no 1v1 o criador joga, então ver as palavras seria vantagem) */}
              {!is1v1 && (
                <div className="cs-sorteadas">
                  <div className="cs-sorteadas-header">
                    <span>Palavras sorteadas</span>
                    {sorteioLoading && <span className="cs-sorteadas-loading">sorteando...</span>}
                  </div>
                  {sorteioAviso && <div className="cs-sorteio-aviso">{sorteioAviso}</div>}
                  {sorteadas.length === 0 && !sorteioLoading && (
                    <p className="cs-sorteadas-vazio">Nenhuma palavra sorteada — aumente as quantidades acima.</p>
                  )}
                  {DIFFS.map(({ key, color, label }) => {
                    const doNivel = sorteadas.filter(w => w.dificuldade === key);
                    if (doNivel.length === 0) return null;
                    return (
                      <div key={key} className="cs-sorteadas-group">
                        <span className="cs-sorteadas-group-label">
                          <span className="cs-diff-dot" style={{ background: color }} />
                          {label}
                          {doNivel.length < quantidades[key] && !sorteioLoading && (
                            <span className="cs-sorteadas-falta"> (banco sem mais palavras dessa dificuldade)</span>
                          )}
                        </span>
                        <div className="cs-sorteadas-list">
                          {doNivel.map(w => (
                            <span key={w.id} className="cs-word-chip">
                              {w.texto}
                              <button
                                type="button"
                                className="cs-regen-word-btn"
                                onClick={() => gerarOutra(w)}
                                disabled={regenId !== null}
                                title="Gerar outra palavra no lugar desta"
                                aria-label={`Gerar outra palavra no lugar de ${w.texto}`}
                              >
                                {regenId === w.id ? '…' : '🔄'}
                              </button>
                            </span>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* PASSO 3 — BUSCA DE PALAVRAS */}
            <div className="cs-card">
              <span className="cs-step-label">Passo 3</span>
              <h2 className="cs-step-title">Adicionar palavras</h2>
              <p className="cs-step-sub">Busca no banco de dados — adicione palavras extras à sala</p>

              <div className="cs-word-search">
                <input
                  type="text"
                  className="cs-word-input"
                  placeholder="Digite uma palavra para buscar..."
                  value={wordSearch}
                  onChange={e => handleWordSearch(e.target.value)}
                />
              </div>

              {busca.status === 'loading' && <div className="cs-search-status cs-search-loading">Buscando no banco de dados...</div>}

              {busca.status === 'found' && busca.palavra && (
                <div className="cs-search-status cs-search-found">
                  <div className="cs-search-info">
                    <span className="cs-search-check">✓</span>
                    <div>
                      <strong>{busca.palavra.texto}</strong>
                      <span className="cs-search-meta">
                        {' '}
                        · {labelDificuldade(busca.palavra)}
                        {busca.palavra.categoria ? ` · ${busca.palavra.categoria}` : ''}
                      </span>
                    </div>
                  </div>
                  <button type="button" className="cs-add-word-btn" onClick={() => addWord(busca.palavra)}>
                    + Adicionar
                  </button>
                </div>
              )}

              {busca.status === 'similar' && busca.similares && (
                <div className="cs-search-similar">
                  <p className="cs-similar-label">Palavras semelhantes encontradas:</p>
                  {busca.similares.map(p => (
                    <div key={p.id} className="cs-similar-row">
                      <div>
                        <strong>{p.texto}</strong>
                        <span className="cs-search-meta"> · {labelDificuldade(p)}</span>
                      </div>
                      <button type="button" className="cs-add-word-btn cs-add-word-btn--sm" onClick={() => addWord(p)}>
                        + Adicionar
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {busca.status === 'notfound' && (
                <div className="cs-search-status cs-search-notfound">
                  <span className="cs-search-x">✗</span>
                  Palavra não encontrada no banco de dados
                </div>
              )}

              {extraWords.length > 0 && (
                <div className="cs-extra-words">
                  {extraWords.map(w => (
                    <span key={w.id} className="cs-word-chip">
                      {w.texto}
                      <button type="button" onClick={() => removeWord(w.id)} aria-label={`Remover ${w.texto}`}>
                        ×
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* ---- COLUNA DIREITA ---- */}
          <div className="cs-col">
            {/* CÓDIGO */}
            <div className="cs-card">
              <span className="cs-step-label">Passo 4</span>
              <h2 className="cs-step-title">Código de acesso</h2>
              <div className="cs-code-display">{codigo}</div>
              <div className="cs-code-row">
                <button type="button" className="cs-regen-btn" onClick={() => setCodigo(generateCode())}>
                  🔄 Gerar novo
                </button>
              </div>
              <span className="cs-code-hint">
                {is1v1
                  ? privada
                    ? 'Duelo privado: envie este código para o seu oponente entrar.'
                    : 'Duelo público: ele aparece na lista global, mas o código também funciona.'
                  : 'Compartilhe este código com seus alunos para que possam entrar na sala.'}
              </span>
            </div>

            {/* RESUMO */}
            <div className="cs-card">
              <h2 className="cs-step-title">Resumo</h2>
              <div className="cs-summary">
                {is1v1 && (
                  <div className="cs-summary-row">
                    <span>Modo</span>
                    <strong>Duelo 1v1 — {privada ? 'Privado 🔒' : 'Público 🌎'}</strong>
                  </div>
                )}
                <div className="cs-summary-row">
                  <span>Tempo por rodada</span>
                  <strong>
                    {tempos.FACIL}s / {tempos.MEDIO}s / {tempos.DIFICIL}s
                  </strong>
                </div>
                <div className="cs-summary-divider" />
                {DIFFS.map(({ key, color, label }) => (
                  <div className="cs-summary-row" key={key}>
                    <span>
                      <span className="cs-dot" style={{ background: color }} />
                      {label}
                    </span>
                    <strong>{quantidades[key]}</strong>
                  </div>
                ))}
                <div className="cs-summary-divider" />
                <div className="cs-summary-row">
                  <span>Total do banco</span>
                  <strong>{total}</strong>
                </div>
                <div className="cs-summary-row">
                  <span>Palavras extras</span>
                  <strong>{extraWords.length}</strong>
                </div>
              </div>
            </div>

            <button type="submit" form="sala-form" className="cs-submit-btn" disabled={submitting || !nome.trim()}>
              {submitting
                ? is1v1
                  ? 'Criando duelo...'
                  : 'Criando sala...'
                : is1v1
                  ? '⚔ Criar duelo 1v1'
                  : '▶ Criar sala e iniciar partida'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CriarSala;
