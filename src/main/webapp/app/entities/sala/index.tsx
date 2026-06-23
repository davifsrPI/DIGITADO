import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Sala from './sala';
import SalaDetail from './sala-detail';
import SalaUpdate from './sala-update';
import SalaDeleteDialog from './sala-delete-dialog';

const SalaRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Sala />} />
    <Route path="new" element={<SalaUpdate />} />
    <Route path=":id">
      <Route index element={<SalaDetail />} />
      <Route path="edit" element={<SalaUpdate />} />
      <Route path="delete" element={<SalaDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default SalaRoutes;
