import './home.scss';

import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';

export const Home = () => {
  const account = useAppSelector(state => state.authentication.account);

  useEffect(() => {
    document.body.classList.add('home-page');
    return () => {
      document.body.classList.remove('home-page');
    };
  }, []);

  return (
    <div className="home-wrapper">
      {/* BACKGROUND */}
      <div className="background-shapes">
        <div className="shape one"></div>
        <div className="shape two"></div>
        <div className="shape three"></div>
      </div>

      {/* HERO */}
      <section className="hero">
        <div className="hero-left">
          <span className="hero-tag">Plataforma Gamificada de Ortografia</span>

          <h1>Aprender ortografia nunca foi tão divertido.</h1>

          <p>Participe de desafios interativos, suba no ranking e evolua sua escrita em tempo real.</p>

          <div className="hero-buttons">
            {account?.login ? (
              <>
                <Link to="/sala">
                  <button className="play-btn">Entrar em uma sala</button>
                </Link>
                <Link to="/ranking">
                  <button className="secondary-btn">Ver Ranking</button>
                </Link>
              </>
            ) : (
              <>
                <Link to="/account/register">
                  <button className="play-btn">Criar conta grátis</button>
                </Link>
                <Link to="/login">
                  <button className="secondary-btn">Entrar</button>
                </Link>
              </>
            )}
          </div>
        </div>

        <div className="hero-right">
          <div className="game-card">
            <div className="game-header">
              <span className="live">● AO VIVO</span>
              <span>Sala #A82X</span>
            </div>

            <h2>Digite corretamente a palavra:</h2>

            <div className="word-area">🔊 MAÇÃ</div>

            <input type="text" placeholder="Digite aqui" />

            <button className="answer-btn">Enviar Resposta</button>

            <div className="points">+10 XP</div>
          </div>
        </div>
      </section>

      {/* INFO */}
      <section className="info-section">
        <div className="info-card">
          <div className="info-icon">🏆</div>
          <h3>Rankings em Tempo Real</h3>
          <p>Dispute posições e acompanhe sua evolução instantaneamente.</p>
        </div>

        <div className="info-card">
          <div className="info-icon">⚡</div>
          <h3>Feedback Imediato</h3>
          <p>Descubra seus erros na hora e aprenda continuamente.</p>
        </div>

        <div className="info-card">
          <div className="info-icon">🎯</div>
          <h3>Missões e Conquistas</h3>
          <p>Ganhe medalhas, níveis e recompensas conforme evolui.</p>
        </div>
      </section>

      {/* DASHBOARD */}
      {account?.login && (
        <section className="dashboard">
          <div className="dashboard-top">
            <h1>Seu Progresso</h1>
            <div className="level-box">Nível 8</div>
          </div>

          <div className="dashboard-grid">
            <div className="dashboard-card">
              <span>XP TOTAL</span>
              <h2>1250</h2>
            </div>
            <div className="dashboard-card">
              <span>POSIÇÃO</span>
              <h2>#3</h2>
            </div>
            <div className="dashboard-card">
              <span>MEDALHAS</span>
              <h2>12</h2>
            </div>
          </div>

          <div className="progress-box">
            <div className="progress-info">
              <span>Progresso para o próximo nível</span>
              <span>75%</span>
            </div>
            <div className="progress-bar-bg">
              <div className="progress-fill"></div>
            </div>
          </div>
        </section>
      )}

      {/* RANKING */}
      <section className="ranking">
        <div className="ranking-header">
          <h1>Ranking da Sala</h1>
          <span>Atualizado em tempo real</span>
        </div>

        <div className="ranking-list">
          <div className="player first">
            <div className="player-position">🥇</div>
            <div className="player-name">Ana</div>
            <div className="player-score">2500 XP</div>
          </div>
          <div className="player second">
            <div className="player-position">🥈</div>
            <div className="player-name">Lucas</div>
            <div className="player-score">2200 XP</div>
          </div>
          <div className="player third">
            <div className="player-position">🥉</div>
            <div className="player-name">Davi</div>
            <div className="player-score">2100 XP</div>
          </div>
        </div>
      </section>
    </div>
  );
};

export default Home;
