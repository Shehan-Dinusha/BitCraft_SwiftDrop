import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { catalogFamilies } from './families';
import { catalogPages, type CatalogPageDef } from './catalogNav';

const densityLegend = [
  {
    persona: 'Courier',
    density: 'Kinetic',
    target: '56px',
    note: 'Thumb zone, one primary action'
  },
  { persona: 'Customer', density: 'Comfortable', target: '44px', note: 'Progressive disclosure' },
  {
    persona: 'Merchant, dispatcher, admin',
    density: 'Compact',
    target: '34px',
    note: 'Scan-first operational grids'
  }
];

function sectionSummary(page: CatalogPageDef) {
  if (page.kind === 'shells') return 'Six roles, three widths, plus navigation parts';
  const titles = [
    ...(page.kind === 'foundations' ? ['Colour, spacing and density'] : []),
    ...page.familyIds.map((id) => catalogFamilies.find((family) => family.id === id)?.title)
  ].filter(Boolean);
  return `${titles.length} section${titles.length === 1 ? '' : 's'}: ${titles.join(', ')}`;
}

/** `/catalog`: what the design system is and where to find each part. */
export function CatalogHome() {
  return (
    <div className="mx-auto max-w-5xl space-y-10">
      <header className="space-y-3">
        <p className="text-overline uppercase text-brand-primary">Design system</p>
        <h1 className="text-h1 text-primary">Velocity components</h1>
        <p className="max-w-2xl text-body-l text-secondary">
          Every shared component, grouped like the Figma library. Everything here is light theme.
          Forced states exist for this catalog only; product code drives states from props and
          interaction. This page needs no login.
        </p>
      </header>

      <section aria-label="Pages" className="grid gap-4 sm:grid-cols-2">
        {catalogPages.map((page) => {
          return (
            <Link
              key={page.slug}
              to={`/catalog/${page.slug}`}
              className="group flex flex-col gap-2 rounded-xl border border-border-subtle bg-card p-5 transition-colors hover:border-border-selected focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
            >
              <span className="flex items-center justify-between">
                <span className="text-h3 text-primary">{page.title}</span>
                <ArrowRight
                  aria-hidden="true"
                  className="size-4 text-muted transition-transform group-hover:translate-x-0.5 group-hover:text-brand-primary"
                />
              </span>
              <span className="text-body text-secondary">{page.summary}</span>
              <span className="text-caption text-muted">{sectionSummary(page)}</span>
            </Link>
          );
        })}
      </section>

      <section aria-label="Density" className="space-y-3">
        <h2 className="text-h2 text-primary">Density by role</h2>
        <dl className="grid gap-3 rounded-xl border border-border-subtle bg-card p-5 sm:grid-cols-3">
          {densityLegend.map((entry) => (
            <div key={entry.persona}>
              <dt className="text-overline uppercase text-muted">{entry.persona}</dt>
              <dd className="text-body-strong text-primary">
                {entry.density} · {entry.target}
              </dd>
              <dd className="text-caption text-secondary">{entry.note}</dd>
            </div>
          ))}
        </dl>
      </section>
    </div>
  );
}

export default CatalogHome;
