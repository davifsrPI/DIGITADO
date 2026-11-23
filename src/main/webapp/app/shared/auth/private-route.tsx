import React from 'react';
import { Link, Navigate, PathRouteProps, useLocation } from 'react-router-dom';
import { Translate } from 'react-jhipster';

import { useAppSelector } from 'app/config/store';
import { AUTHORITIES } from 'app/config/constants';
import ErrorBoundary from 'app/shared/error/error-boundary';
import { LoadingSpinner } from 'app/shared/layout/loading/loading-spinner';

interface IOwnProps extends PathRouteProps {
  hasAnyAuthorities?: string[];
  /**
   * Tela de JOGO: a conta de administração não entra (ela administra, não joga).
   * O servidor barra do mesmo jeito - isto só troca o erro por uma explicação.
   */
  somenteJogador?: boolean;
  children: React.ReactNode;
}

/** A conta tem papel de administração? */
export const ehAdmin = (authorities?: string[]) => !!authorities?.includes(AUTHORITIES.ADMIN);

/** O aviso que a conta de administração vê no lugar das telas de jogo. */
export const AvisoAdminNaoJoga = () => (
  <div className="insufficient-authority">
    <div className="alert alert-warning" role="alert" data-cy="adminNaoJoga">
      <strong>A conta de administração não participa dos jogos.</strong> Ela cuida do sistema: usuários, palavras, salas e métricas. Para
      jogar, entre com uma conta de jogador.
      <div className="mt-2">
        <Link to="/admin/desempenho">Ir para o desempenho de todos →</Link>
      </div>
    </div>
  </div>
);

export const PrivateRoute = ({ children, hasAnyAuthorities = [], somenteJogador = false, ...rest }: IOwnProps) => {
  const isAuthenticated = useAppSelector(state => state.authentication.isAuthenticated);
  const sessionHasBeenFetched = useAppSelector(state => state.authentication.sessionHasBeenFetched);
  const account = useAppSelector(state => state.authentication.account);
  const isAuthorized = hasAnyAuthority(account.authorities, hasAnyAuthorities);
  const pageLocation = useLocation();

  if (!children) {
    throw new Error(`A component needs to be specified for private route for path ${(rest as any).path}`);
  }

  if (!sessionHasBeenFetched) {
    return <LoadingSpinner />;
  }

  if (isAuthenticated) {
    if (somenteJogador && ehAdmin(account.authorities)) {
      return <AvisoAdminNaoJoga />;
    }
    if (isAuthorized) {
      return <ErrorBoundary>{children}</ErrorBoundary>;
    }

    return (
      <div className="insufficient-authority">
        <div className="alert alert-danger">
          <Translate contentKey="error.http.403">You are not authorized to access this page.</Translate>
        </div>
      </div>
    );
  }

  return (
    <Navigate
      to={{
        pathname: '/login',
        search: pageLocation.search,
      }}
      replace
      state={{ from: pageLocation }}
    />
  );
};

export const hasAnyAuthority = (authorities: string[], hasAnyAuthorities: string[]) => {
  if (authorities && authorities.length !== 0) {
    if (hasAnyAuthorities.length === 0) {
      return true;
    }
    return hasAnyAuthorities.some(auth => authorities.includes(auth));
  }
  return false;
};

/**
 * Checks authentication before showing the children and redirects to the
 * login page if the user is not authenticated.
 * If hasAnyAuthorities is provided the authorization status is also
 * checked and an error message is shown if the user is not authorized.
 */
export default PrivateRoute;
