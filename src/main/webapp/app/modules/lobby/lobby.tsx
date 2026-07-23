import './lobby.scss';

import React, { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { useBodyClass } from 'app/shared/util/use-body-class';

// Os códigos de sala têm sempre 6 caracteres (ver generateCode em criar-sala)
const CODE_LEN = 6;

export const Lobby = () => {
  const account = useAppSelector(state => state.authentication.account);
  const [roomCode, setRoomCode] = useState('');
  const [codeFocused, setCodeFocused] = useState(false);
  const codeInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  useBodyClass('lobby-page');

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
            Olá, <span className="lobby-name">{account?.apelido || account?.firstName || account?.login}</span> 👋
          </h1>
          <p>Digite o código da sala para começar</p>
        </div>

        <div className="enter-room-box">
          {/* Ícone de "entrar" desenhado em SVG - emoji renderiza feio e diferente em cada sistema */}
          <div className="enter-room-icon">
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" aria-hidden="true">
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              <polyline points="10 17 15 12 10 7" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              <line x1="15" y1="12" x2="3" y2="12" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <h2>Entrar em uma sala</h2>
          <p>Insira o código fornecido pelo seu professor</p>
          <form onSubmit={handleEnterRoom} className="room-code-form">
            {/* 6 caixas de código com um input invisível por cima capturando a digitação */}
            <div className="code-boxes" onClick={() => codeInputRef.current?.focus()}>
              {Array.from({ length: CODE_LEN }).map((_, i) => (
                <div
                  key={i}
                  className={`code-box${roomCode[i] ? ' code-box--filled' : ''}${
                    codeFocused && i === Math.min(roomCode.length, CODE_LEN - 1) ? ' code-box--active' : ''
                  }`}
                >
                  {roomCode[i] ?? ''}
                </div>
              ))}
              <input
                ref={codeInputRef}
                type="text"
                className="code-hidden-input"
                value={roomCode}
                onChange={e =>
                  setRoomCode(
                    e.target.value
                      .toUpperCase()
                      .replace(/[^A-Z0-9]/g, '')
                      .slice(0, CODE_LEN),
                  )
                }
                onFocus={() => setCodeFocused(true)}
                onBlur={() => setCodeFocused(false)}
                maxLength={CODE_LEN}
                autoComplete="off"
                autoCorrect="off"
                autoCapitalize="characters"
                spellCheck={false}
                aria-label="Código da sala"
              />
            </div>
            <button type="submit" className="room-enter-btn" disabled={roomCode.length < CODE_LEN}>
              Entrar na sala →
            </button>
          </form>
        </div>

        <Link to="/sala/new" className="create-room-link">
          <span className="create-room-link-icon">✏️</span>
          Criar uma sala nova
          <span className="create-room-link-arrow">→</span>
        </Link>

        <Link to="/duelo" className="create-room-link">
          <span className="create-room-link-icon">⚔️</span>
          Duelo 1v1 - desafie alguém
          <span className="create-room-link-arrow">→</span>
        </Link>

        <div className="lobby-footer-links">
          <Link to="/ranking">🏆 Ver ranking</Link>
          <Link to="/conquistas">🏅 Minhas conquistas</Link>
          <Link to="/account/settings">⚙️ Configurações</Link>
        </div>
      </div>
    </div>
  );
};

export default Lobby;
