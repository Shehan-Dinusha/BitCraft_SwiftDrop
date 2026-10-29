import { useEffect, useRef, useState } from 'react';
import { ExternalLink } from 'lucide-react';
import { ChoiceChip, roleShellSpec } from '@/design-system';
import { cn } from '@/utils/cn';
import { previewRoles, type PreviewRole } from '../ShellPreviewPage';

type Viewport = 'desktop' | 'tablet' | 'mobile';

const viewports: Record<Viewport, { label: string; width: number; height: number; hint: string }> =
  {
    desktop: { label: 'Desktop', width: 1440, height: 800, hint: '1440px · 256px side nav' },
    tablet: { label: 'Tablet', width: 1024, height: 760, hint: '1024px · 72px icon rail' },
    mobile: { label: 'Mobile', width: 390, height: 780, hint: '390px · bottom tabs' }
  };

const roleLabel = (role: PreviewRole) => (role === 'public' ? 'Public' : roleShellSpec[role].label);

/** Mobile only exists in Figma for the public site, customer and courier. */
const supportsMobile = (role: PreviewRole) =>
  role === 'public' || role === 'customer' || role === 'courier';

/** An iframe at its real width, scaled down to fit the column so media queries see the true width. */
function ScaledFrame({
  src,
  title,
  width,
  height
}: {
  src: string;
  title: string;
  width: number;
  height: number;
}) {
  const box = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const node = box.current;
    if (!node) return undefined;
    const update = () => setScale(Math.min(1, node.clientWidth / width));
    update();
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, [width]);

  return (
    <div
      ref={box}
      className="mx-auto w-full overflow-hidden"
      style={{ maxWidth: width, height: height * scale }}
    >
      <iframe
        title={title}
        src={src}
        loading="lazy"
        style={{
          width,
          height,
          border: 0,
          transform: `scale(${scale})`,
          transformOrigin: 'top left'
        }}
        className="bg-card"
      />
    </div>
  );
}

/** Role switcher and width toggle around a framed, real preview of each shell. */
export function ShellsGallery() {
  const [role, setRole] = useState<PreviewRole>('customer');
  const [viewport, setViewport] = useState<Viewport>('desktop');

  const effective: Viewport = viewport === 'mobile' && !supportsMobile(role) ? 'desktop' : viewport;
  const frame = viewports[effective];
  const src = `/catalog/preview/${role}`;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
        <div role="group" aria-label="Role" className="flex flex-wrap gap-2">
          {previewRoles.map((item) => (
            <ChoiceChip key={item} selected={item === role} onClick={() => setRole(item)}>
              {roleLabel(item)}
            </ChoiceChip>
          ))}
        </div>
        <div role="group" aria-label="Width" className="flex gap-2">
          {(Object.keys(viewports) as Viewport[]).map((item) => (
            <ChoiceChip
              key={item}
              selected={item === effective}
              disabled={item === 'mobile' && !supportsMobile(role)}
              onClick={() => setViewport(item)}
            >
              {viewports[item].label}
            </ChoiceChip>
          ))}
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 text-caption text-secondary">
        <span>
          {roleLabel(role)} · {frame.hint}
          {viewport === 'mobile' && !supportsMobile(role)
            ? ' · Figma has no mobile design for this role'
            : ''}
        </span>
        <a
          href={src}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1 text-brand-primary hover:underline"
        >
          Open full page
          <ExternalLink aria-hidden="true" className="size-3" />
        </a>
      </div>

      <div
        className={cn(
          'overflow-hidden rounded-xl border border-border-subtle bg-elevated p-3',
          effective === 'mobile' && 'sm:px-8'
        )}
      >
        <div className="overflow-hidden rounded-lg border border-border-subtle bg-card shadow-ambient">
          <ScaledFrame
            key={`${role}-${effective}`}
            src={src}
            title={`${roleLabel(role)} shell, ${frame.label}`}
            width={frame.width}
            height={frame.height}
          />
        </div>
      </div>
    </div>
  );
}

export default ShellsGallery;
