import { SurfaceCard, type SurfaceCardState, type SurfaceCardVariant } from '@/design-system';
import { CatalogRow, DemoGrid, DensityPreview } from '../components/catalog-kit';

const variants: SurfaceCardVariant[] = ['base', 'elevated', 'interactive', 'selected'];
const states: SurfaceCardState[] = ['default', 'hover', 'focused', 'disabled'];

export function SurfacesSection() {
  return (
    <>
      <CatalogRow label="Roles">
        <DemoGrid>
          {variants.map((variant) => (
            <SurfaceCard
              key={variant}
              variant={variant}
              title={variant}
              footer={<span className="text-xs text-muted">Footer slot</span>}
            >
              <p className="text-sm text-secondary">
                {variant === 'interactive'
                  ? 'Tappable surface with hover and focus affordances.'
                  : variant === 'selected'
                    ? 'The chosen card in a set.'
                    : 'Content surface on the page canvas.'}
              </p>
            </SurfaceCard>
          ))}
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="States" hint="Forced with the `state` prop on an interactive card">
        <DemoGrid>
          {states.map((state) => (
            <SurfaceCard
              key={state}
              variant="interactive"
              state={state}
              title={state}
              onClick={state === 'disabled' ? undefined : () => undefined}
              aria-disabled={state === 'disabled' || undefined}
            >
              <p className="text-sm text-secondary">Parcel SD-4821-XZ</p>
            </SurfaceCard>
          ))}
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Content slots" hint="title, header, actions, body, footer">
        <SurfaceCard
          title="Delivery summary"
          header={<span className="text-xs text-muted">Updated 2 minutes ago</span>}
          actions={
            <button type="button" className="text-xs font-semibold text-brand-primary">
              View
            </button>
          }
          footer={<span className="text-xs text-muted">Zone 4 · 12 stops</span>}
        >
          <p className="text-sm text-secondary">
            Three parcels are waiting for a courier assignment.
          </p>
        </SurfaceCard>
      </CatalogRow>

      <CatalogRow label="Density">
        <DensityPreview>
          {() => (
            <SurfaceCard title="Courier load" variant="elevated">
              <p className="text-sm text-secondary">Amara Okafor · 4 active parcels</p>
            </SurfaceCard>
          )}
        </DensityPreview>
      </CatalogRow>
    </>
  );
}
