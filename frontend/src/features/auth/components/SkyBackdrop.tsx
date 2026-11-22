import { cn } from '@/utils/cn';

/**
 * Colour mixes of existing tokens only. The palette has no alpha or "sky" tokens, so the backdrop,
 * rings and card tint are blends of the brand, card and canvas tokens rather than new colours.
 */
export const mix = (token: string, percent: number, base = 'transparent') =>
  `color-mix(in srgb, var(${token}) ${percent}%, ${base})`;

/**
 * The soft brand-tinted sky behind the auth screens and the landing hero: a gradient that fades into
 * the canvas, two faint arcs and cloud glows along the bottom. Fills its nearest positioned parent;
 * put the content after it in a `relative` element so it sits on top.
 */
export function SkyBackdrop({ className }: { className?: string }) {
  const ring = mix('--color-surface-card', 60);

  return (
    <div
      aria-hidden="true"
      className={cn('pointer-events-none absolute inset-0 overflow-hidden', className)}
      style={{
        background: `linear-gradient(180deg, ${mix('--color-brand-primary', 30, 'var(--color-surface-canvas)')} 0%, ${mix('--color-brand-primary', 14, 'var(--color-surface-canvas)')} 38%, var(--color-surface-canvas) 78%)`
      }}
    >
      {/* Two faint arcs, centred low so they read as arcs at the sides. */}
      <div className="absolute inset-0 hidden justify-center sm:flex">
        {[1100, 1500].map((size) => (
          <span
            key={size}
            className="absolute top-[78%] -translate-y-1/2 rounded-full border"
            style={{ width: size, height: size, borderColor: ring }}
          />
        ))}
      </div>
      {/* Cloud glows along the bottom. */}
      <div className="absolute inset-x-0 bottom-0 h-[45%]">
        {[
          'left-[-8%] bottom-[-12%] h-[60%] w-[45%]',
          'left-[25%] bottom-[-20%] h-[70%] w-[50%]',
          'right-[-10%] bottom-[-10%] h-[65%] w-[45%]'
        ].map((position) => (
          <span
            key={position}
            className={cn('absolute rounded-full blur-3xl', position)}
            style={{ backgroundColor: mix('--color-surface-card', 90) }}
          />
        ))}
      </div>
    </div>
  );
}
