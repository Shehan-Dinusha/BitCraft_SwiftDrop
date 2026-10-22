import type { Config } from 'tailwindcss';

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: 'var(--color-surface-canvas)',
        card: 'var(--color-surface-card)',
        elevated: 'var(--color-surface-elevated)',
        primary: 'var(--color-text-primary)',
        secondary: 'var(--color-text-secondary)',
        muted: 'var(--color-text-muted)',
        brand: {
          primary: 'var(--color-brand-primary)',
          hover: 'var(--color-brand-hover)',
          pressed: 'var(--color-brand-pressed)',
          on: 'var(--color-on-brand)'
        },
        border: {
          subtle: 'var(--color-border-subtle)',
          focus: 'var(--color-border-focus)'
        },
        status: {
          success: 'var(--color-status-success)',
          warning: 'var(--color-status-warning)',
          danger: 'var(--color-status-danger)',
          info: 'var(--color-status-info)'
        },
        fsm: {
          created: '#4f46e5',
          assigned: '#2563eb',
          pickedUp: '#0284c7',
          inTransit: '#d97706',
          delivered: '#059669',
          failed: '#dc2626',
          cancelled: '#64748b'
        },
        sla: { warning: '#d97706', critical: '#dc2626' }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'IBM Plex Mono', 'ui-monospace', 'monospace']
      },
      minHeight: { target: '56px' },
      minWidth: { target: '56px' }
    }
  },
  plugins: []
} satisfies Config;
