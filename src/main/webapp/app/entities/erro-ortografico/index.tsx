import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import ErroOrtografico from './erro-ortografico';
import ErroOrtograficoDetail from './erro-ortografico-detail';
import ErroOrtograficoUpdate from './erro-ortografico-update';
import ErroOrtograficoDeleteDialog from './erro-ortografico-delete-dialog';

const ErroOrtograficoRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<ErroOrtografico />} />
    <Route path="new" element={<ErroOrtograficoUpdate />} />
    <Route path=":id">
      <Route index element={<ErroOrtograficoDetail />} />
      <Route path="edit" element={<ErroOrtograficoUpdate />} />
      <Route path="delete" element={<ErroOrtograficoDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default ErroOrtograficoRoutes;
