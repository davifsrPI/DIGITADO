import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { login } from 'app/shared/reducers/authentication';
import LoginPage from './login-page';
import { ehAdmin } from 'app/shared/auth/private-route';

export const Login = () => {
  const dispatch = useAppDispatch();
  const isAuthenticated = useAppSelector(state => state.authentication.isAuthenticated);
  const loginError = useAppSelector(state => state.authentication.loginError);
  const loginBloqueado = useAppSelector(state => state.authentication.loginBloqueado);
  const pageLocation = useLocation();

  const handleLogin = (username, password, rememberMe = false) => dispatch(login(username, password, rememberMe));

  const account = useAppSelector(state => state.authentication.account);
  const estado = (pageLocation.state ?? {}) as { from?: any; contaCriada?: boolean; loginSugerido?: string };
  if (isAuthenticated) {
    // A conta de administração não joga: sem destino pedido, vai para o painel dela
    const padrao = { pathname: ehAdmin(account?.authorities) ? '/admin/desempenho' : '/lobby', search: pageLocation.search };
    return <Navigate to={estado.from ?? padrao} replace />;
  }

  return (
    <LoginPage
      handleLogin={handleLogin}
      loginError={loginError}
      loginBloqueado={loginBloqueado}
      contaCriada={!!estado.contaCriada}
      loginSugerido={typeof estado.loginSugerido === 'string' ? estado.loginSugerido : ''}
    />
  );
};

export default Login;
