import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import UsuarioConquista from './usuario-conquista';
import UsuarioConquistaDetail from './usuario-conquista-detail';
import UsuarioConquistaUpdate from './usuario-conquista-update';
import UsuarioConquistaDeleteDialog from './usuario-conquista-delete-dialog';

const UsuarioConquistaRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<UsuarioConquista />} />
    <Route path="new" element={<UsuarioConquistaUpdate />} />
    <Route path=":id">
      <Route index element={<UsuarioConquistaDetail />} />
      <Route path="edit" element={<UsuarioConquistaUpdate />} />
      <Route path="delete" element={<UsuarioConquistaDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default UsuarioConquistaRoutes;
