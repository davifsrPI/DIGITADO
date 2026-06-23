import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Usuario from './usuario';
import Sala from './sala';
import ListaPalavras from './lista-palavras';
import Palavra from './palavra';
import Atividade from './atividade';
import Resposta from './resposta';
import ErroOrtografico from './erro-ortografico';
import Ranking from './ranking';
import Conquista from './conquista';
import UsuarioConquista from './usuario-conquista';
/* jhipster-needle-add-route-import - JHipster will add routes here */

export default () => {
  return (
    <div>
      <ErrorBoundaryRoutes>
        {/* prettier-ignore */}
        <Route path="usuario/*" element={<Usuario />} />
        <Route path="sala/*" element={<Sala />} />
        <Route path="lista-palavras/*" element={<ListaPalavras />} />
        <Route path="palavra/*" element={<Palavra />} />
        <Route path="atividade/*" element={<Atividade />} />
        <Route path="resposta/*" element={<Resposta />} />
        <Route path="erro-ortografico/*" element={<ErroOrtografico />} />
        <Route path="ranking/*" element={<Ranking />} />
        <Route path="conquista/*" element={<Conquista />} />
        <Route path="usuario-conquista/*" element={<UsuarioConquista />} />
        {/* jhipster-needle-add-route-path - JHipster will add routes here */}
      </ErrorBoundaryRoutes>
    </div>
  );
};
