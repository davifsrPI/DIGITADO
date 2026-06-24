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
        <div className="shape four"></div>
      </div>

      {/* HERO */}
      <section className="hero">
        <div className="hero-badge">
          <span className="badge-dot"></span>
          Jogue agora com outros alunos
        </div>

        <h1 className="hero-title">
          Domine a ortografia.
          <br />
          <span className="highlight">Suba no ranking.</span>
        </h1>

        <p className="hero-sub">Desafios em tempo real, conquistas e feedback instantâneo para transformar sua escrita.</p>

        <div className="hero-actions">
          {account?.login ? (
            <>
              <Link to="/sala">
                <button className="btn-primary">Começar agora</button>
              </Link>
              <Link to="/ranking">
                <button className="btn-ghost">Ver como funciona</button>
              </Link>
            </>
          ) : (
            <>
              <Link to="/account/register">
                <button className="btn-primary">Começar agora</button>
              </Link>
              <Link to="/login">
                <button className="btn-ghost">Ver como funciona</button>
              </Link>
            </>
          )}
        </div>

        {/* STATS ROW */}
        <div className="stats-row">
          <div className="stat">
            <span className="stat-number">12k+</span>
            <span className="stat-label">Usuários</span>
          </div>
          <div className="stat-divider"></div>
          <div className="stat">
            <span className="stat-number">850k</span>
            <span className="stat-label">Palavras digitadas</span>
          </div>
          <div className="stat-divider"></div>
          <div className="stat">
            <span className="stat-number">98%</span>
            <span className="stat-label">Satisfação</span>
          </div>
        </div>
      </section>

      {/* COMO FUNCIONA */}
      <section className="how-section">
        <div className="section-label">Como funciona</div>
        <h2 className="section-title">Três passos para evoluir</h2>

        <div className="steps">
          <div className="step">
            <div className="step-number">01</div>
            <div className="step-content">
              <h3>Entre em uma sala</h3>
              <p>Escolha uma sala pública ou crie a sua com amigos. Cada sala tem um tema e nível de dificuldade.</p>
            </div>
          </div>

          <div className="step-arrow">→</div>

          <div className="step">
            <div className="step-number">02</div>
            <div className="step-content">
              <h3>Ouça e digite</h3>
              <p>Uma palavra é anunciada. Você tem segundos para digitá-la corretamente e ganhar pontos.</p>
            </div>
          </div>

          <div className="step-arrow">→</div>

          <div className="step">
            <div className="step-number">03</div>
            <div className="step-content">
              <h3>Evolua e conquiste</h3>
              <p>Suba de nível, desbloqueie medalhas e apareça no ranking global.</p>
            </div>
          </div>
        </div>
      </section>

      {/* SALA AO VIVO */}
      <section className="live-section">
        <div className="live-left">
          <div className="section-label">Ao vivo agora</div>
          <h2 className="section-title">Veja uma partida em andamento</h2>

          <p className="section-desc">
            As salas são dinâmicas e competitivas. Cada rodada dura segundos — só os mais rápidos e precisos ficam no topo.
          </p>

          <div className="feature-list">
            <div className="feature-item">
              <span className="feature-check">✓</span>
              Palavras selecionadas por IA
            </div>
            <div className="feature-item">
              <span className="feature-check">✓</span>
              Placar atualizado em tempo real
            </div>
            <div className="feature-item">
              <span className="feature-check">✓</span>
              Análise de erros ao final da rodada
            </div>
          </div>
        </div>

        <div className="live-right">
          <div className="match-card">
            <div className="match-top">
              <div className="live-badge">● AO VIVO</div>
              <div className="room-code">Sala #B47Z</div>
              <div className="timer">0:23</div>
            </div>

            <div className="round-info">Rodada 3 de 5 &nbsp;·&nbsp; Dificuldade: Médio</div>

            <div className="word-display">🔊 &nbsp; NECESSÁRIO</div>

            <div className="players-typing">
              <div className="player-row">
                <div className="player-avatar blue">A</div>
                <div className="player-progress-wrap">
                  <div className="player-progress" style={{ width: '90%' }}></div>
                </div>
                <span className="player-pts">+30</span>
              </div>
              <div className="player-row">
                <div className="player-avatar green">L</div>
                <div className="player-progress-wrap">
                  <div className="player-progress green-bar" style={{ width: '60%' }}></div>
                </div>
                <span className="player-pts">+20</span>
              </div>
              <div className="player-row">
                <div className="player-avatar yellow">D</div>
                <div className="player-progress-wrap">
                  <div className="player-progress yellow-bar" style={{ width: '40%' }}></div>
                </div>
                <span className="player-pts">+10</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* CONQUISTAS */}
      <section className="achievements-section">
        <div className="section-label">Progresso</div>
        <h2 className="section-title">Conquistas que te motivam</h2>

        <div className="achievements-grid">
          <div className="achievement unlocked">
            <div className="ach-icon">🔥</div>
            <div className="ach-info">
              <div className="ach-name">Sequência de 7 dias</div>
              <div className="ach-desc">Jogou 7 dias seguidos</div>
            </div>
            <div className="ach-badge">Obtida</div>
          </div>

          <div className="achievement unlocked">
            <div className="ach-icon">⚡</div>
            <div className="ach-info">
              <div className="ach-name">Digitador Relâmpago</div>
              <div className="ach-desc">Acertou em menos de 2s</div>
            </div>
            <div className="ach-badge">Obtida</div>
          </div>

          <div className="achievement locked">
            <div className="ach-icon">👑</div>
            <div className="ach-info">
              <div className="ach-name">Rei do Ranking</div>
              <div className="ach-desc">Chegue ao 1º lugar global</div>
            </div>
            <div className="ach-badge locked-badge">Bloqueada</div>
          </div>

          <div className="achievement locked">
            <div className="ach-icon">💎</div>
            <div className="ach-info">
              <div className="ach-name">Diamante</div>
              <div className="ach-desc">Alcance o nível 50</div>
            </div>
            <div className="ach-badge locked-badge">Bloqueada</div>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="cta-section">
        <div className="cta-card">
          <h2>Pronto para competir?</h2>
          <p>Crie sua conta gratuitamente e entre na sua primeira sala em menos de 1 minuto.</p>
          <div className="cta-buttons">
            <Link to="/account/register">
              <button className="btn-primary large">Criar conta grátis</button>
            </Link>
            <Link to="/login">
              <button className="btn-ghost large">Já tenho conta</button>
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
};

export default Home;
