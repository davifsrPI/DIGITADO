import './home.scss';

import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import PalavraDoDia from 'app/modules/home/palavra-do-dia';
import RankingTop5 from 'app/modules/home/ranking-top5';
import { useBodyClass } from 'app/shared/util/use-body-class';

export const Home = () => {
  const account = useAppSelector(state => state.authentication.account);

  useBodyClass('home-page');

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
            // Logado: os dois botões levam direto para a criação de sala
            <>
              <Link to="/sala/new">
                <button className="btn-primary">Começar agora</button>
              </Link>
              <Link to="/sala/new">
                <button className="btn-ghost">Entrar</button>
              </Link>
            </>
          ) : (
            <>
              <Link to="/account/register">
                <button className="btn-primary">Começar agora</button>
              </Link>
              <Link to="/login">
                <button className="btn-ghost">Entrar</button>
              </Link>
            </>
          )}
        </div>
      </section>

      {/* PALAVRA DO DIA - desafio público, uma chance por pessoa (controle no backend) */}
      <PalavraDoDia />

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

      {/* RANKING MUNDIAL - top 5 público, com link para o ranking completo */}
      <RankingTop5 />
    </div>
  );
};

export default Home;
