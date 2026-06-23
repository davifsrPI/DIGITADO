import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Palavra from './palavra';
import PalavraDetail from './palavra-detail';
import PalavraUpdate from './palavra-update';
import PalavraDeleteDialog from './palavra-delete-dialog';

const PalavraRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Palavra />} />
    <Route path="new" element={<PalavraUpdate />} />
    <Route path=":id">
      <Route index element={<PalavraDetail />} />
      <Route path="edit" element={<PalavraUpdate />} />
      <Route path="delete" element={<PalavraDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default PalavraRoutes;
