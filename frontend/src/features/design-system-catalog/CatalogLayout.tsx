import { useMemo, useState } from 'react';
import { ExternalLink, Package, Search } from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { cn } from '@/utils/cn';
import { catalogFamilies } from './families';
import { FIGMA_FILE, catalogPages } from './catalogNav';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center justify-between rounded-lg px-3 py-2 text-label transition-colors',
    isActive ? 'bg-brand-subtle font-semibold text-brand-pressed' : 'text-primary hover:bg-elevated'
  );

/**
 * Standalone layout for the design catalog. It has no login, no role and no app shell,
 * so it renders the same for everyone and works without the backend.
 */
export function CatalogLayout() {
  const [query, setQuery] = useState('');

  const pages = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return catalogPages;
    return catalogPages.filter((page) => {
      const familyText = page.familyIds
        .map((id) => catalogFamilies.find((family) => family.id === id))
        .map((family) => `${family?.title ?? ''} ${family?.description ?? ''}`)
        .join(' ');
      return `${page.title} ${page.summary} ${page.keywords ?? ''} ${familyText}`
        .toLowerCase()
        .includes(needle);
    });
  }, [query]);

  return (
    <div className="light min-h-screen bg-canvas text-primary">
      <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border-subtle bg-card px-4 sm:px-6">
        <Link to="/catalog" className="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            className="flex size-8 items-center justify-center rounded-lg bg-brand-primary text-brand-on"
          >
            <Package className="size-[18px]" />
          </span>
          <span className="text-body-strong text-primary">SwiftDrop Velocity</span>
          <span className="hidden text-label text-muted sm:inline">Design system</span>
        </Link>
        <div className="ml-auto flex items-center gap-4 text-label">
          <a
            href={FIGMA_FILE}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-secondary hover:text-primary"
          >
            Figma
            <ExternalLink aria-hidden="true" className="size-3.5" />
          </a>
          <Link to="/login" className="text-brand-primary hover:underline">
            Open the app
          </Link>
        </div>
      </header>

      <div className="mx-auto flex max-w-[1500px]">
        <aside
          aria-label="Catalog navigation"
          className="sticky top-14 hidden h-[calc(100vh-3.5rem)] w-64 shrink-0 flex-col gap-3 overflow-y-auto border-r border-border-subtle px-3 py-4 lg:flex"
        >
          <label className="flex items-center gap-2 rounded-lg border border-border-subtle bg-card px-3 py-2 focus-within:outline focus-within:outline-2 focus-within:-outline-offset-2 focus-within:outline-border-focus">
            <Search aria-hidden="true" className="size-4 shrink-0 text-muted" />
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Find a component"
              aria-label="Find a component"
              className="min-w-0 flex-1 bg-transparent text-body text-primary outline-none placeholder:text-muted"
            />
          </label>
          <nav aria-label="Catalog pages" className="flex flex-col gap-0.5">
            <NavLink to="/catalog" end className={linkClass}>
              Overview
            </NavLink>
            {pages.map((page) => (
              <NavLink key={page.slug} to={`/catalog/${page.slug}`} className={linkClass}>
                {page.title}
              </NavLink>
            ))}
            {pages.length === 0 ? (
              <p className="px-3 py-2 text-caption text-muted">Nothing matches “{query}”.</p>
            ) : null}
          </nav>
        </aside>

        <div className="min-w-0 flex-1">
          <nav
            aria-label="Catalog pages"
            className="flex gap-1 overflow-x-auto border-b border-border-subtle bg-card px-4 py-2 lg:hidden"
          >
            <NavLink to="/catalog" end className={linkClass}>
              Overview
            </NavLink>
            {catalogPages.map((page) => (
              <NavLink
                key={page.slug}
                to={`/catalog/${page.slug}`}
                className={(state) => cn(linkClass(state), 'shrink-0')}
              >
                {page.title}
              </NavLink>
            ))}
          </nav>
          <main className="px-4 py-8 sm:px-8">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}

export default CatalogLayout;
