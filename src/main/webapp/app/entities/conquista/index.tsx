import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Conquista from './conquista';
import ConquistaDetail from './conquista-detail';
import ConquistaUpdate from './conquista-update';
import ConquistaDeleteDialog from './conquista-delete-dialog';

const ConquistaRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Conquista />} />
    <Route path="new" element={<ConquistaUpdate />} />
    <Route path=":id">
      <Route index element={<ConquistaDetail />} />
      <Route path="edit" element={<ConquistaUpdate />} />
      <Route path="delete" element={<ConquistaDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default ConquistaRoutes;
