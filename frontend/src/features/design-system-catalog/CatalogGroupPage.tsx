import { ExternalLink } from 'lucide-react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { CatalogSection } from './components/catalog-kit';
import { catalogFamilies } from './families';
import { figmaLink, findCatalogPage } from './catalogNav';
import { FoundationsSection } from './sections/FoundationsSection';
import { ShellsGallery } from './sections/ShellsGallery';

/** `/catalog/:slug`: one group of components with its Figma links and a section index. */
export function CatalogGroupPage() {
  const { slug } = useParams();
  const page = findCatalogPage(slug);
  if (!page) return <Navigate to="/catalog" replace />;

  const families = page.familyIds
    .map((id) => catalogFamilies.find((family) => family.id === id))
    .filter((family): family is NonNullable<typeof family> => Boolean(family));

  const index = [
    ...(page.kind === 'foundations'
      ? [{ id: 'tokens', title: 'Colour, spacing and density' }]
      : []),
    ...(page.kind === 'shells' ? [{ id: 'role-shells', title: 'Role shells' }] : []),
    ...families.map((family) => ({
      id: family.id,
      title: page.kind === 'shells' ? 'Navigation parts' : family.title
    }))
  ];

  return (
    <div className="mx-auto max-w-6xl space-y-10">
      <header className="space-y-4">
        <nav aria-label="Breadcrumb" className="flex items-center gap-2 text-label text-muted">
          <Link to="/catalog" className="hover:text-primary">
            Catalog
          </Link>
          <span aria-hidden="true">/</span>
          <span aria-current="page" className="text-primary">
            {page.title}
          </span>
        </nav>
        <h1 className="text-h1 text-primary">{page.title}</h1>
        <p className="max-w-3xl text-body-l text-secondary">{page.summary}</p>

        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-label">
          <span className="text-muted">Figma</span>
          {page.figma.map((link) => (
            <a
              key={link.node}
              href={figmaLink(link.node)}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-brand-primary hover:underline"
            >
              {link.label}
              <ExternalLink aria-hidden="true" className="size-3" />
            </a>
          ))}
        </div>

        {index.length > 1 ? (
          <nav aria-label="On this page" className="flex flex-wrap gap-2 pt-1">
            {index.map((entry) => (
              <a
                key={entry.id}
                href={`#${entry.id}`}
                className="rounded-full border border-border-subtle bg-card px-3 py-1 text-caption text-secondary transition-colors hover:border-border-focus hover:text-primary"
              >
                {entry.title}
              </a>
            ))}
          </nav>
        ) : null}
      </header>

      {page.kind === 'foundations' ? (
        <CatalogSection
          id="tokens"
          title="Colour, spacing and density"
          description="Semantic tokens from the Figma Theme collection. Build with these, never with the raw palette."
        >
          <FoundationsSection />
        </CatalogSection>
      ) : null}

      {page.kind === 'shells' ? (
        <CatalogSection
          id="role-shells"
          title="Role shells"
          description="The real side navigation, top bar and mobile tabs, shown as full pages inside frames. Pick a role and a width."
        >
          <ShellsGallery />
        </CatalogSection>
      ) : null}

      {families.map((family) => (
        <CatalogSection
          key={family.id}
          id={family.id}
          title={page.kind === 'shells' ? 'Navigation parts' : family.title}
          description={family.description}
        >
          {family.content}
        </CatalogSection>
      ))}
    </div>
  );
}

export default CatalogGroupPage;
