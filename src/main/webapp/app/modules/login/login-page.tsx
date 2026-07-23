import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from 'reactstrap';
import './login-page.scss';
import { useBodyClass } from 'app/shared/util/use-body-class';

interface ILoginPageProps {
  handleLogin: (username: string, password: string, rememberMe: boolean) => void;
  loginError: boolean;
}

const LoginPage = ({ handleLogin, loginError }: ILoginPageProps) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);

  useBodyClass('login-page');

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    handleLogin(username, password, rememberMe);
  };

  return (
    <div className="login-wrapper">
      <div className="login-bg">
        <div className="lp-shape one" />
        <div className="lp-shape two" />
        <div className="lp-shape three" />
      </div>

      <div className="login-center">
        <div className="login-brand">
          <img src="/content/images/digitado-icon.svg" alt="" className="login-brand-icon" />
          DIGITADO
        </div>

        <div className="login-card">
          <h2>Bem-vindo de volta</h2>
          <p className="login-sub">Entre com seus dados para continuar</p>

          {loginError && (
            <Alert color="danger" className="login-alert">
              Usuário ou senha incorretos. Verifique seus dados.
            </Alert>
          )}

          <form onSubmit={handleSubmit} className="login-form">
            <div className="lp-field">
              <label htmlFor="lp-username">Usuário ou e-mail</label>
              <input
                id="lp-username"
                type="text"
                placeholder="Digite seu login"
                value={username}
                onChange={e => setUsername(e.target.value)}
                required
              />
            </div>
            <div className="lp-field">
              <label htmlFor="lp-password">Senha</label>
              <input
                id="lp-password"
                type="password"
                placeholder="Digite sua senha"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />
            </div>
            <div className="lp-remember-row">
              <label>
                <input type="checkbox" checked={rememberMe} onChange={e => setRememberMe(e.target.checked)} />
                Lembrar-me
              </label>
              <Link to="/account/reset/request" className="lp-forgot">
                Esqueceu a senha?
              </Link>
            </div>
            <button type="submit" className="lp-submit-btn">
              Entrar
            </button>
          </form>

          <div className="lp-register-link">
            <span>Não tem uma conta?</span>
            <Link to="/account/register">Criar conta</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
