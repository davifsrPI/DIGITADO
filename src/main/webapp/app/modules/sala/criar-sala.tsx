import './criar-sala.scss';

import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const generateCode = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  return Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
};

export const CriarSala = () => {
  const navigate = useNavigate();
  const [nome, setNome] = useState('');
  const [descricao, setDescricao] = useState('');
  const [codigo, setCodigo] = useState(generateCode());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    document.body.classList.add('criar-sala-page');
    return () => {
      document.body.classList.remove('criar-sala-page');
    };
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await axios.post('/api/salas', {
        nome,
        codigo,
        descricao: descricao || null,
        ativo: true,
      });
      navigate(`/sala/${res.data.codigo}`);
    } catch {
      setError('Não foi possível criar a sala. Tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="cs-wrapper">
      <div className="cs-bg">
        <div className="cs-shape one" />
        <div className="cs-shape two" />
        <div className="cs-shape three" />
      </div>

      <div className="cs-center">
        <button className="cs-back" onClick={() => navigate('/lobby')}>
          ← Voltar ao lobby
        </button>

        <div className="cs-card">
          <div className="cs-header">
            <div className="cs-header-icon">✏️</div>
            <div>
              <h2>Criar nova sala</h2>
              <p>Configure os detalhes da sua sala e compartilhe o código com os alunos</p>
            </div>
          </div>

          {error && <div className="cs-error">{error}</div>}

          <form onSubmit={handleSubmit} className="cs-form">
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
                placeholder="Descreva o objetivo da sala, tema ou instruções para os alunos..."
                value={descricao}
                onChange={e => setDescricao(e.target.value)}
                rows={3}
                maxLength={500}
              />
            </div>

            <div className="cs-field">
              <label>Código da sala</label>
              <div className="cs-code-row">
                <div className="cs-code-display">{codigo}</div>
                <button type="button" className="cs-regen-btn" onClick={() => setCodigo(generateCode())}>
                  🔄 Gerar novo
                </button>
              </div>
              <span className="cs-code-hint">Compartilhe este código com seus alunos para que possam entrar na sala.</span>
            </div>

            <button type="submit" className="cs-submit-btn" disabled={loading || !nome.trim()}>
              {loading ? 'Criando...' : '🚀 Criar sala'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default CriarSala;
