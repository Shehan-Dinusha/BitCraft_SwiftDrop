import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter, Navigate, useRoutes } from 'react-router-dom';
import { catalogRoutes } from '@/features/design-system-catalog/catalogRoutes';
import './index.css';

/**
 * Entry for the standalone, single-file design catalog. It has no providers, no login and no
 * backend. Hash routes let it work when opened as a plain file.
 */
function CatalogApp() {
  return useRoutes([...catalogRoutes, { path: '*', element: <Navigate to="/catalog" replace /> }]);
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <HashRouter>
      <CatalogApp />
    </HashRouter>
  </React.StrictMode>
);
