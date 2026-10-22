import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { DensityProvider, densityModes, type DensityMode } from '@/design-system';

export interface PreviewPaneProps {
  children: ReactNode;
  /** Applied to both frames, e.g. a fixed height for docks and grids. */
  className?: string;
}

function Frame({ theme, className, children }: { theme: 'light' | 'dark'; className?: string; children: ReactNode }) {
  return (
    <div
      className={cn(
        'overflow-hidden rounded-xl border border-border-subtle',
        theme === 'light' ? 'light' : 'dark'
      )}
    >
      <div className="border-b border-border-subtle bg-elevated px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted">
        {theme}
      </div>
      <div className={cn('bg-canvas p-4 text-primary', className)}>{children}</div>
    </div>
  );
}

/** Renders the same demo twice, scoped to the light and dark token sets. */
export function PreviewPane({ children, className }: PreviewPaneProps) {
  return (
    <div className="grid gap-3 lg:grid-cols-2">
      <Frame theme="light" className={className}>
        {children}
      </Frame>
      <Frame theme="dark" className={className}>
        {children}
      </Frame>
    </div>
  );
}

export function CatalogSection({
  id,
  title,
  description,
  children
}: {
  id: string;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-28 space-y-6">
      <div>
        <h2 className="text-xl font-bold tracking-tight text-primary">{title}</h2>
        {description ? <p className="mt-1 max-w-3xl text-sm text-secondary">{description}</p> : null}
      </div>
      {children}
    </section>
  );
}

export function CatalogRow({
  label,
  hint,
  children
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-secondary">{label}</h3>
        {hint ? <span className="text-xs text-muted">{hint}</span> : null}
      </div>
      <PreviewPane>{children}</PreviewPane>
    </div>
  );
}

export function DemoRow({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('flex flex-wrap items-center gap-3', className)}>{children}</div>;
}

export function DemoGrid({ children }: { children: ReactNode }) {
  return <div className="grid gap-3 sm:grid-cols-2">{children}</div>;
}

/** Renders the demo once per density, each inside its own provider. */
export function DensityPreview({ children }: { children: (density: DensityMode) => ReactNode }) {
  return (
    <div className="space-y-4">
      {densityModes.map((density) => (
        <div key={density}>
          <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted">{density}</p>
          <DensityProvider density={density}>{children(density)}</DensityProvider>
        </div>
      ))}
    </div>
  );
}
