import './criar-sala.scss';

import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const generateCode = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
};

type Dificuldade = 'FACIL' | 'MEDIO' | 'DIFICIL';

interface Palavra {
  id: number;
  texto: string;
  dificuldade: Dificuldade;
  categoria?: string;
}

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
  const [nome, setNome] = useState('');
  const [descricao, setDescricao] = useState('');
  const [codigo, setCodigo] = useState(generateCode());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tempo, setTempo] = useState(30);
  const [quantidades, setQuantidades] = useState<Record<Dificuldade, number>>({ FACIL: 5, MEDIO: 5, DIFICIL: 5 });
  const [wordSearch, setWordSearch] = useState('');
  const [busca, setBusca] = useState<BuscaResult>({ status: 'idle' });
  const [extraWords, setExtraWords] = useState<Palavra[]>([]);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    document.body.classList.add('criar-sala-page');
    return () => document.body.classList.remove('criar-sala-page');
  }, []);

  const adj = (key: Dificuldade, delta: number) => {
    setQuantidades(prev => ({ ...prev, [key]: Math.max(0, Math.min(30, prev[key] + delta)) }));
  };

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

  const addWord = (palavra: Palavra) => {
    if (!extraWords.find(w => w.id === palavra.id)) {
      setExtraWords(prev => [...prev, palavra]);
    }
    setWordSearch('');
    setBusca({ status: 'idle' });
  };

  const removeWord = (id: number) => setExtraWords(prev => prev.filter(w => w.id !== id));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    let tentativas = 0;
    let codigoTentativa = codigo;
    while (tentativas < 5) {
      try {
        const res = await axios.post('/api/salas', { nome, codigo: codigoTentativa, descricao: descricao || null, ativo: true });
        navigate(`/sala/${res.data.codigo}`, { state: { isProfessor: true } });
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
        <button className="cs-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <div className="cs-grid">
          {/* ---- COLUNA ESQUERDA ---- */}
          <div className="cs-col">
            {/* PASSO 1 */}
            <div className="cs-card">
              <span className="cs-step-label">Passo 1</span>
              <h2 className="cs-step-title">Informações da sala</h2>

              {error && <div className="cs-error">{error}</div>}

              <form id="sala-form" onSubmit={handleSubmit} className="cs-form">
                <div className="cs-field">
                  <label htmlFor="cs-nome">Nome da sala</label>
                  <input
                    id="cs-nome"
                    type="text"
                    placeholder="Ex: Turma 3A — Aula de Ortografia"
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

                <div className="cs-field">
                  <label>
                    Tempo por rodada: <span className="cs-range-val">{tempo}s</span>
                  </label>
                  <input
                    type="range"
                    min={10}
                    max={60}
                    step={5}
                    value={tempo}
                    onChange={e => setTempo(Number(e.target.value))}
                    className="cs-range"
                  />
                </div>
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
                        · {DIFF_LABELS[busca.palavra.dificuldade]}
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
                        <span className="cs-search-meta"> · {DIFF_LABELS[p.dificuldade]}</span>
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
              <span className="cs-code-hint">Compartilhe este código com seus alunos para que possam entrar na sala.</span>
            </div>

            {/* RESUMO */}
            <div className="cs-card">
              <h2 className="cs-step-title">Resumo</h2>
              <div className="cs-summary">
                <div className="cs-summary-row">
                  <span>Tempo por rodada</span>
                  <strong>{tempo}s</strong>
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
              {submitting ? 'Criando...' : '🚀 Criar sala'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CriarSala;
