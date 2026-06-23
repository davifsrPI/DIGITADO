import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Atividade from './atividade';
import AtividadeDetail from './atividade-detail';
import AtividadeUpdate from './atividade-update';
import AtividadeDeleteDialog from './atividade-delete-dialog';

const AtividadeRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Atividade />} />
    <Route path="new" element={<AtividadeUpdate />} />
    <Route path=":id">
      <Route index element={<AtividadeDetail />} />
      <Route path="edit" element={<AtividadeUpdate />} />
      <Route path="delete" element={<AtividadeDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default AtividadeRoutes;
