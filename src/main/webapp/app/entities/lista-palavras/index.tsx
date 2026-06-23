import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import ListaPalavras from './lista-palavras';
import ListaPalavrasDetail from './lista-palavras-detail';
import ListaPalavrasUpdate from './lista-palavras-update';
import ListaPalavrasDeleteDialog from './lista-palavras-delete-dialog';

const ListaPalavrasRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<ListaPalavras />} />
    <Route path="new" element={<ListaPalavrasUpdate />} />
    <Route path=":id">
      <Route index element={<ListaPalavrasDetail />} />
      <Route path="edit" element={<ListaPalavrasUpdate />} />
      <Route path="delete" element={<ListaPalavrasDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default ListaPalavrasRoutes;
