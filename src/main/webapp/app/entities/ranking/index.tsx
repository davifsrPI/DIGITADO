import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Ranking from './ranking';
import RankingDetail from './ranking-detail';
import RankingUpdate from './ranking-update';
import RankingDeleteDialog from './ranking-delete-dialog';

const RankingRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Ranking />} />
    <Route path="new" element={<RankingUpdate />} />
    <Route path=":id">
      <Route index element={<RankingDetail />} />
      <Route path="edit" element={<RankingUpdate />} />
      <Route path="delete" element={<RankingDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default RankingRoutes;
