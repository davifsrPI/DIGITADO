import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from 'reactstrap';
import './login-page.scss';

interface ILoginPageProps {
  handleLogin: (username: string, password: string, rememberMe: boolean) => void;
  loginError: boolean;
}

const LoginPage = (props: ILoginPageProps) => {
  const { handleLogin, loginError } = props;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    handleLogin(username, password, rememberMe);
  };

  return (
    <div className="login-page">
      <div className="background-shapes">
        <div className="shape one" />
        <div className="shape two" />
        <div className="shape three" />
      </div>

      <header className="header">
        <div className="logo">
          <span className="logo-icon">✦</span>
          DIGITADO
        </div>
        <nav className="menu">
          <Link to="/">Início</Link>
          <a href="#salas">Salas</a>
          <a href="#ranking">Ranking</a>
          <Link to="/account/register">Criar login</Link>
        </nav>
      </header>

      <section className="hero">
        <div className="hero-left">
          <span className="hero-tag">Plataforma Gamificada de Ortografia</span>
          <h1>Aprender ortografia nunca foi tão divertido.</h1>
          <p>Participe de desafios interativos, suba no ranking e evolua sua escrita em tempo real.</p>
          <div className="hero-buttons">
            <button className="play-btn" type="submit" form="login-form">
              Entrar em uma sala
            </button>
            <Link to="/account/register" className="secondary-btn">
              Criar login
            </Link>
          </div>
        </div>

        <div className="hero-right">
          <div className="game-card" id="login-card">
            <div className="game-header">
              <span className="live">● AO VIVO</span>
              <span>Sala #A82X</span>
            </div>
            <h2>Entre com seu login para começar</h2>
            {loginError ? (
              <Alert color="danger" className="login-alert">
                Usuário ou senha incorretos. Verifique seus dados e tente novamente.
              </Alert>
            ) : null}
            <form id="login-form" onSubmit={handleSubmit} className="login-form">
              <label htmlFor="login-username">Usuário ou e-mail</label>
              <input
                id="login-username"
                type="text"
                placeholder="Digite seu login"
                value={username}
                onChange={event => setUsername(event.target.value)}
                required
              />
              <label htmlFor="login-password">Senha</label>
              <input
                id="login-password"
                type="password"
                placeholder="Digite sua senha"
                value={password}
                onChange={event => setPassword(event.target.value)}
                required
              />
              <div className="remember-row">
                <label>
                  <input type="checkbox" checked={rememberMe} onChange={event => setRememberMe(event.target.checked)} />
                  Lembrar-me
                </label>
              </div>
              <button className="answer-btn" type="submit">
                Entrar
              </button>
            </form>
            <div className="points">Conexão segura com o DB para autenticação</div>
            <div className="register-cta">
              <span>Não tem um login?</span>
              <Link to="/account/register" className="secondary-btn">
                Criar conta
              </Link>
            </div>
          </div>
        </div>
      </section>

      <section className="info-section" id="ranking">
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
    </div>
  );
};

export default LoginPage;
