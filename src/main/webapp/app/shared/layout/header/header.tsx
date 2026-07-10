import './header.scss';

import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Storage, Translate } from 'react-jhipster';
import { Collapse, Nav, Navbar, NavbarToggler } from 'reactstrap';
import LoadingBar from 'react-redux-loading-bar';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { setLocale } from 'app/shared/reducers/locale';
import { AccountMenu, AdminMenu, EntitiesMenu, LocaleMenu } from '../menus';
import { Brand, Home } from './header-components';

export interface IHeaderProps {
  isAuthenticated: boolean;
  isAdmin: boolean;
  ribbonEnv: string;
  isInProduction: boolean;
  isOpenAPIEnabled: boolean;
  currentLocale: string;
}

const Header = (props: IHeaderProps) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const account = useAppSelector(state => state.authentication.account);
  const [acertouPalavraDoDia, setAcertouPalavraDoDia] = useState(false);

  const dispatch = useAppDispatch();

  // Consulta no BACKEND se o usuário logado já acertou a palavra do dia — a
  // resposta vem do banco (nada é guardado/decidido no front). Quem acertou
  // ganha a chama animada ao lado do nome no menu.
  useEffect(() => {
    if (!props.isAuthenticated) {
      setAcertouPalavraDoDia(false);
      return;
    }
    axios
      .get('/api/public/palavra-do-dia')
      .then(res => setAcertouPalavraDoDia(res.data?.resultado?.acertou === true))
      .catch(() => setAcertouPalavraDoDia(false));
  }, [props.isAuthenticated, account?.login]);

  const handleLocaleChange = event => {
    const langKey = event.target.value;
    Storage.session.set('locale', langKey);
    dispatch(setLocale(langKey));
  };

  const renderDevRibbon = () =>
    props.isInProduction === false ? (
      <div className="ribbon dev">
        <a href="">
          <Translate contentKey={`global.ribbon.${props.ribbonEnv}`} />
        </a>
      </div>
    ) : null;

  const toggleMenu = () => setMenuOpen(!menuOpen);

  /* jhipster-needle-add-element-to-menu - JHipster will add new menu items here */

  return (
    <div id="app-header">
      {renderDevRibbon()}
      <LoadingBar className="loading-bar" />
      <Navbar data-cy="navbar" dark expand="md" fixed="top" className="jh-navbar">
        <NavbarToggler aria-label="Menu" onClick={toggleMenu} />
        <Brand />
        <Collapse isOpen={menuOpen} navbar>
          {/* mx-auto centraliza o grupo de navegação no meio da barra (estilo pílula) */}
          <Nav id="header-tabs" className="mx-auto" navbar>
            <Home />
            {props.isAuthenticated && props.isAdmin && <EntitiesMenu />}
            {props.isAuthenticated && props.isAdmin && <AdminMenu showOpenAPI={props.isOpenAPIEnabled} />}
            <LocaleMenu currentLocale={props.currentLocale} onClick={handleLocaleChange} />
            <AccountMenu
              isAuthenticated={props.isAuthenticated}
              // Apelido é o nome público do jogador — vence o primeiro nome
              displayName={account?.apelido || account?.firstName || account?.login}
              acertouPalavraDoDia={acertouPalavraDoDia}
            />
          </Nav>
        </Collapse>
      </Navbar>
    </div>
  );
};

export default Header;
