import React from 'react';
import MenuItem from 'app/shared/layout/menus/menu-item';
import { Translate } from 'react-jhipster';
import { NavItem, NavLink } from 'reactstrap';
import { NavLink as Link } from 'react-router-dom';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { NavDropdown } from './menu-components';

const accountMenuItemsAuthenticated = () => (
  <>
    <MenuItem icon="door-open" to="/minhas-salas" data-cy="minhasSalas">
      Minhas Salas
    </MenuItem>
    <MenuItem icon="trophy" to="/conquistas" data-cy="conquistas">
      Minhas Conquistas
    </MenuItem>
    <MenuItem icon="wrench" to="/account/settings" data-cy="settings">
      <Translate contentKey="global.menu.account.settings">Settings</Translate>
    </MenuItem>
    <MenuItem icon="lock" to="/account/password" data-cy="passwordItem">
      <Translate contentKey="global.menu.account.password">Password</Translate>
    </MenuItem>
    <MenuItem icon="sign-out-alt" to="/logout" data-cy="logout">
      <Translate contentKey="global.menu.account.logout">Sign out</Translate>
    </MenuItem>
  </>
);

// Rótulo do menu do usuário logado: nome público e, se ele acertou a
// palavra do dia de hoje (informação vinda do backend), uma chama animada 🔥
const rotuloDoMenu = (displayName?: string, acertouPalavraDoDia?: boolean) => (
  <span className="account-menu-nome">
    {displayName}
    {acertouPalavraDoDia && (
      <span className="pdd-fogo" title="Acertou a palavra do dia! 🔥">
        <span className="pdd-fogo-chama">🔥</span>
      </span>
    )}
  </span>
);

// Logado: menu suspenso de perfil. Visitante: botão "Entrar" direto para o login.
export const AccountMenu = ({ isAuthenticated = false, displayName = undefined as string | undefined, acertouPalavraDoDia = false }) =>
  isAuthenticated ? (
    <NavDropdown icon="user" name={rotuloDoMenu(displayName, acertouPalavraDoDia)} id="account-menu" data-cy="accountMenu">
      {accountMenuItemsAuthenticated()}
    </NavDropdown>
  ) : (
    <NavItem id="login-item">
      <NavLink tag={Link} to="/login" className="d-flex align-items-center" data-cy="login">
        <FontAwesomeIcon icon="sign-in-alt" />
        <span>Entrar</span>
      </NavLink>
    </NavItem>
  );

export default AccountMenu;
