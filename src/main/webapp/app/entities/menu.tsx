import React from 'react';
import { Translate } from 'react-jhipster';

import { DropdownItem } from 'reactstrap';
import MenuItem from 'app/shared/layout/menus/menu-item';
import { TABELAS_DO_JOGO } from './tabelas-jogo/tabelas-jogo';

const EntitiesMenu = () => {
  return (
    <>
      {/* prettier-ignore */}
      <MenuItem icon="asterisk" to="/entidades/usuario">
        <Translate contentKey="global.menu.entities.usuario" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/sala">
        <Translate contentKey="global.menu.entities.sala" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/lista-palavras">
        <Translate contentKey="global.menu.entities.listaPalavras" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/palavra">
        <Translate contentKey="global.menu.entities.palavra" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/atividade">
        <Translate contentKey="global.menu.entities.atividade" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/resposta">
        <Translate contentKey="global.menu.entities.resposta" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/erro-ortografico">
        <Translate contentKey="global.menu.entities.erroOrtografico" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/ranking">
        <Translate contentKey="global.menu.entities.ranking" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/conquista">
        <Translate contentKey="global.menu.entities.conquista" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/entidades/usuario-conquista">
        <Translate contentKey="global.menu.entities.usuarioConquista" />
      </MenuItem>
      {/* jhipster-needle-add-entity-to-menu - JHipster will add entities to the menu here */}
      {/* Registros que o jogo grava sozinho - só leitura (ver AdminTabelasResource) */}
      <DropdownItem header>Registros do jogo (somente leitura)</DropdownItem>
      {TABELAS_DO_JOGO.map(t => (
        <MenuItem key={t.nome} icon="eye" to={`/entidades/tabelas/${t.nome}`}>
          {t.titulo}
        </MenuItem>
      ))}
    </>
  );
};

export default EntitiesMenu;
