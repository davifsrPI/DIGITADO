import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Resposta from './resposta';
import RespostaDetail from './resposta-detail';
import RespostaUpdate from './resposta-update';
import RespostaDeleteDialog from './resposta-delete-dialog';

const RespostaRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Resposta />} />
    <Route path="new" element={<RespostaUpdate />} />
    <Route path=":id">
      <Route index element={<RespostaDetail />} />
      <Route path="edit" element={<RespostaUpdate />} />
      <Route path="delete" element={<RespostaDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default RespostaRoutes;
