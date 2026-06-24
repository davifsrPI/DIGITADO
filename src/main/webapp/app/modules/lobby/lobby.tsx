import './lobby.scss';

import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';

export const Lobby = () => {
  const account = useAppSelector(state => state.authentication.account);
  const [roomCode, setRoomCode] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    document.body.classList.add('lobby-page');
    return () => {
      document.body.classList.remove('lobby-page');
    };
  }, []);

  const handleEnterRoom = (e: React.FormEvent) => {
    e.preventDefault();
    const code = roomCode.trim().toUpperCase();
    if (code) navigate(`/sala/${code}`);
  };

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
            Online
          </span>
          <h1>
            Olá, <span className="lobby-name">{account?.firstName || account?.login}</span> 👋
          </h1>
          <p>Digite o código da sala para começar</p>
        </div>

        <div className="enter-room-box">
          <div className="enter-room-icon">🎮</div>
          <h2>Entrar em uma sala</h2>
          <p>Insira o código fornecido pelo seu professor</p>
          <form onSubmit={handleEnterRoom} className="room-code-form">
            <input
              type="text"
              className="room-code-input"
              placeholder="Ex: A4BX2"
              value={roomCode}
              onChange={e => setRoomCode(e.target.value.toUpperCase())}
              maxLength={8}
              autoComplete="off"
              spellCheck={false}
            />
            <button type="submit" className="room-enter-btn" disabled={!roomCode.trim()}>
              Entrar →
            </button>
          </form>
        </div>

        <Link to="/sala/new" className="create-room-link">
          <span className="create-room-link-icon">✏️</span>
          Criar uma sala nova
          <span className="create-room-link-arrow">→</span>
        </Link>

        <div className="lobby-footer-links">
          <Link to="/ranking">🏆 Ver ranking</Link>
          <Link to="/account/settings">⚙️ Configurações</Link>
        </div>
      </div>
    </div>
  );
};

export default Lobby;
