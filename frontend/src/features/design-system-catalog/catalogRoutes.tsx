import type { RouteObject } from 'react-router-dom';
import { CatalogGroupPage } from './CatalogGroupPage';
import { CatalogHome } from './CatalogHome';
import { CatalogLayout } from './CatalogLayout';
import { ShellPreviewPage } from './ShellPreviewPage';

/**
 * The public design catalog. Used by the app router (`/catalog`) and by the standalone
 * single-file build (`npm run build:catalog`), so both always show the same thing.
 */
export const catalogRoutes: RouteObject[] = [
  {
    path: '/catalog',
    element: <CatalogLayout />,
    children: [
      { index: true, element: <CatalogHome /> },
      { path: ':slug', element: <CatalogGroupPage /> }
    ]
  },
  { path: '/catalog/preview/:role', element: <ShellPreviewPage /> }
];
