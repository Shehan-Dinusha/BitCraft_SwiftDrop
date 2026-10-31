export const velocityTypography = {
  trackingId: 'font-mono tabular-nums text-xs',
  coordinates: 'font-mono tabular-nums text-sm',
  eta: 'font-mono tabular-nums font-semibold',
  pricing: 'font-mono tabular-nums font-semibold'
} as const;

/**
 * The 12 Velocity text styles from Figma. Each value is the Tailwind class
 * (`fontSize` key in tailwind.config.ts); the two mono styles also need
 * `font-mono`, which is included here.
 */
export const velocityTextStyles = {
  display: { className: 'text-display', spec: 'Plus Jakarta Sans 700 · 36/44' },
  h1: { className: 'text-h1', spec: 'Plus Jakarta Sans 700 · 28/36' },
  h2: { className: 'text-h2', spec: 'Plus Jakarta Sans 600 · 22/30' },
  h3: { className: 'text-h3', spec: 'Plus Jakarta Sans 600 · 18/26' },
  bodyL: { className: 'text-body-l', spec: 'Plus Jakarta Sans 400 · 16/24' },
  body: { className: 'text-body', spec: 'Plus Jakarta Sans 400 · 14/20' },
  bodyStrong: { className: 'text-body-strong', spec: 'Plus Jakarta Sans 600 · 14/20' },
  label: { className: 'text-label', spec: 'Plus Jakarta Sans 500 · 13/18' },
  caption: { className: 'text-caption', spec: 'Plus Jakarta Sans 400 · 12/16' },
  overline: {
    className: 'text-overline uppercase',
    spec: 'Plus Jakarta Sans 600 · 11/16 · 6% tracking'
  },
  mono: { className: 'font-mono text-mono', spec: 'JetBrains Mono 400 · 14/20' },
  monoL: { className: 'font-mono text-mono-l', spec: 'JetBrains Mono 500 · 20/28' }
} as const;

export type VelocityTextStyle = keyof typeof velocityTextStyles;
