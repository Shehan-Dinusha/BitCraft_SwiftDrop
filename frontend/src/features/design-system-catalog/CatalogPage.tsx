import type { ReactNode } from 'react';
import { CatalogSection } from './components/catalog-kit';
import { ButtonsSection } from './sections/ButtonsSection';
import { FormControlsSection } from './sections/FormControlsSection';
import { MenusSection } from './sections/MenusSection';
import { SurfacesSection } from './sections/SurfacesSection';
import { DialogsSection } from './sections/DialogsSection';
import { StatusSection } from './sections/StatusSection';
import { ProgressSection } from './sections/ProgressSection';
import { FeedbackSection } from './sections/FeedbackSection';
import { DataSection } from './sections/DataSection';
import { TypographySection } from './sections/TypographySection';

interface CatalogEntry {
  id: string;
  title: string;
  description: string;
  content: ReactNode;
}

const families: CatalogEntry[] = [
  {
    id: 'typography',
    title: 'Typography',
    description:
      'Twelve Velocity text styles (Display to Mono L), exposed as Tailwind text-* utilities.',
    content: <TypographySection />
  },
  {
    id: 'buttons',
    title: 'Buttons',
    description:
      'Five variants across six forced states and three densities. Density decides height, padding, icon size, and label size.',
    content: <ButtonsSection />
  },
  {
    id: 'form-controls',
    title: 'Inputs and fields',
    description:
      'Five input archetypes and seven visual states, plus the labelled Field wrapper that wires hints and errors to `aria-describedby`.',
    content: <FormControlsSection />
  },
  {
    id: 'menus',
    title: 'Selects and menus',
    description:
      'Combobox and listbox families share rows, status dots, and density through the menu provider.',
    content: <MenusSection />
  },
  {
    id: 'surfaces',
    title: 'Cards and surfaces',
    description:
      'Four surface roles and four forced states. Interactive roles are keyboard operable and only become buttons when given an onClick.',
    content: <SurfacesSection />
  },
  {
    id: 'dialogs',
    title: 'Dialogs and drawers',
    description:
      'One shell for modals and right-side sheets. Rendered inline here so the catalog stays scrollable.',
    content: <DialogsSection />
  },
  {
    id: 'status',
    title: 'Status and feedback',
    description:
      'FSM badges are the only source of delivery-state color. Banners use the status token triads, so both themes stay readable.',
    content: <StatusSection />
  },
  {
    id: 'progress',
    title: 'Progress and transitions',
    description:
      'The customer stepper and the courier slide-to-confirm control, including every forced state.',
    content: <ProgressSection />
  },
  {
    id: 'feedback',
    title: 'Loading and assistance',
    description:
      'Skeletons are decorative placeholders; the AI dock keeps its own conversation state and cites the data it used.',
    content: <FeedbackSection />
  },
  {
    id: 'data',
    title: 'Dense data and actions',
    description:
      "Operational grids and the courier action bar, capped by Hick's Law for each density.",
    content: <DataSection />
  }
];

const densityLegend = [
  { persona: 'COURIER', density: 'kinetic', target: '56px', note: 'Thumb zone, one primary action' },
  { persona: 'CUSTOMER', density: 'comfortable', target: '44px', note: 'Progressive disclosure' },
  {
    persona: 'ADMIN_OPS / MERCHANT',
    density: 'compact',
    target: '34px',
    note: 'Scan-first operational grids'
  }
];

export function CatalogPage() {
  return (
    <div className="mx-auto max-w-[1400px] space-y-10 px-4 py-8 sm:px-6">
      <header className="space-y-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-primary">
            Design system
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight text-primary">
            Velocity variant catalog
          </h1>
          <p className="mt-2 max-w-3xl text-sm text-secondary">
            Every family renders on the light token set. Forced states exist for this page; product code drives
            states from props and interaction.
          </p>
        </div>

        <nav aria-label="Catalog sections" className="flex flex-wrap gap-2">
          {families.map((family) => (
            <a
              key={family.id}
              href={`#${family.id}`}
              className="rounded-full border border-border-subtle bg-card px-3 py-1 text-xs font-semibold text-secondary transition-colors hover:border-border-focus hover:text-primary"
            >
              {family.title}
            </a>
          ))}
        </nav>

        <dl className="grid gap-2 rounded-xl border border-border-subtle bg-card p-4 sm:grid-cols-3">
          {densityLegend.map((entry) => (
            <div key={entry.persona}>
              <dt className="text-[10px] font-semibold uppercase tracking-wider text-muted">
                {entry.persona}
              </dt>
              <dd className="text-sm font-semibold text-primary">
                {entry.density} · {entry.target}
              </dd>
              <dd className="text-xs text-secondary">{entry.note}</dd>
            </div>
          ))}
        </dl>
      </header>

      {families.map((family) => (
        <CatalogSection
          key={family.id}
          id={family.id}
          title={family.title}
          description={family.description}
        >
          {family.content}
        </CatalogSection>
      ))}
    </div>
  );
}

export default CatalogPage;
