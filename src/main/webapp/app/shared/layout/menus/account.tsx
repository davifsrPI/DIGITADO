import React from 'react';
import MenuItem from 'app/shared/layout/menus/menu-item';
import { Translate, translate } from 'react-jhipster';
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

const accountMenuItems = () => (
  <>
    <MenuItem id="login-item" icon="sign-in-alt" to="/login" data-cy="login">
      <Translate contentKey="global.menu.account.login">Sign in</Translate>
    </MenuItem>
    <MenuItem icon="user-plus" to="/account/register" data-cy="register">
      <Translate contentKey="global.menu.account.register">Register</Translate>
    </MenuItem>
  </>
);

// Rótulo do menu: nome do usuário logado (em vez de "Conta") e, se ele acertou a
// palavra do dia de hoje (informação vinda do backend), uma chama animada 🔥
const rotuloDoMenu = (isAuthenticated: boolean, displayName?: string, acertouPalavraDoDia?: boolean) => {
  if (!isAuthenticated || !displayName) {
    return translate('global.menu.account.main');
  }
  return (
    <span className="account-menu-nome">
      {displayName}
      {acertouPalavraDoDia && (
        <span className="pdd-fogo" title="Acertou a palavra do dia! 🔥">
          <span className="pdd-fogo-chama pdd-fogo-chama--tras">🔥</span>
          <span className="pdd-fogo-chama">🔥</span>
        </span>
      )}
    </span>
  );
};

export const AccountMenu = ({ isAuthenticated = false, displayName = undefined as string | undefined, acertouPalavraDoDia = false }) => (
  <NavDropdown icon="user" name={rotuloDoMenu(isAuthenticated, displayName, acertouPalavraDoDia)} id="account-menu" data-cy="accountMenu">
    {isAuthenticated && accountMenuItemsAuthenticated()}
    {!isAuthenticated && accountMenuItems()}
  </NavDropdown>
);

export default AccountMenu;
