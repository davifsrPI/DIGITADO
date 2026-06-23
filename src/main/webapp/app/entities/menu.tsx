import React from 'react';
import { Translate } from 'react-jhipster';

import MenuItem from 'app/shared/layout/menus/menu-item';

const EntitiesMenu = () => {
  return (
    <>
      {/* prettier-ignore */}
      <MenuItem icon="asterisk" to="/usuario">
        <Translate contentKey="global.menu.entities.usuario" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/sala">
        <Translate contentKey="global.menu.entities.sala" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/lista-palavras">
        <Translate contentKey="global.menu.entities.listaPalavras" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/palavra">
        <Translate contentKey="global.menu.entities.palavra" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/atividade">
        <Translate contentKey="global.menu.entities.atividade" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/resposta">
        <Translate contentKey="global.menu.entities.resposta" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/erro-ortografico">
        <Translate contentKey="global.menu.entities.erroOrtografico" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/ranking">
        <Translate contentKey="global.menu.entities.ranking" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/conquista">
        <Translate contentKey="global.menu.entities.conquista" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/usuario-conquista">
        <Translate contentKey="global.menu.entities.usuarioConquista" />
      </MenuItem>
      {/* jhipster-needle-add-entity-to-menu - JHipster will add entities to the menu here */}
    </>
  );
};

export default EntitiesMenu;
