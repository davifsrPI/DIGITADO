import './lobby.scss';

import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { TipoUsuario } from 'app/shared/model/enumerations/tipo-usuario.model';

const TIPO_KEY = 'digitado-tipo-usuario';

export const Lobby = () => {
  const account = useAppSelector(state => state.authentication.account);
  const [tipoUsuario, setTipoUsuario] = useState<string>(localStorage.getItem(TIPO_KEY) ?? TipoUsuario.ALUNO);

  useEffect(() => {
    document.body.classList.add('lobby-page');
    return () => {
      document.body.classList.remove('lobby-page');
    };
  }, []);

  const isProfessor = tipoUsuario === TipoUsuario.PROFESSOR;

  return (
    <div className="lobby-wrapper">
      <div className="lobby-bg">
        <div className="lb-shape one"></div>
        <div className="lb-shape two"></div>
        <div className="lb-shape three"></div>
      </div>

      <div className="lobby-content">
        <div className="lobby-greeting">
          <span className="lobby-badge">
            <span className="badge-dot"></span>
            {isProfessor ? 'Professor' : 'Aluno'}
          </span>
          <h1>
            Olá, <span className="lobby-name">{account?.firstName || account?.login}</span> 👋
          </h1>
          <p>O que você quer fazer hoje?</p>
        </div>

        <div className={`lobby-cards ${isProfessor ? 'two-cards' : 'one-card'}`}>
          <Link to="/sala" className="lobby-card enter-card">
            <div className="card-icon">🎮</div>
            <div className="card-info">
              <h2>Entrar em uma sala</h2>
              <p>Participe de uma sala existente e dispute com outros jogadores em tempo real.</p>
            </div>
            <div className="card-arrow">→</div>
          </Link>

          {isProfessor && (
            <Link to="/sala/new" className="lobby-card create-card">
              <div className="card-icon">✏️</div>
              <div className="card-info">
                <h2>Criar uma sala</h2>
                <p>Monte uma sala personalizada, escolha as palavras e convide seus alunos.</p>
              </div>
              <div className="card-arrow">→</div>
            </Link>
          )}
        </div>

        <div className="lobby-footer-links">
          <Link to="/ranking">🏆 Ver ranking</Link>
          <Link to="/account/settings">⚙️ Configurações</Link>
        </div>
      </div>
    </div>
  );
};

export default Lobby;
