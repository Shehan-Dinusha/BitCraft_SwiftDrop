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
          subtle: 'var(--color-brand-subtle)',
          on: 'var(--color-on-brand)'
        },
        border: {
          subtle: 'var(--color-border-subtle)',
          focus: 'var(--color-border-focus)',
          selected: 'var(--color-border-selected)'
        },
        disabled: {
          bg: 'var(--color-disabled-bg)',
          fg: 'var(--color-disabled-fg)'
        },
        scrim: {
          DEFAULT: 'var(--color-scrim)',
          soft: 'var(--color-scrim-soft)'
        },
        status: {
          success: {
            DEFAULT: 'var(--color-status-success)',
            bg: 'var(--color-status-success-bg)',
            fg: 'var(--color-status-success-fg)',
            border: 'var(--color-status-success-border)'
          },
          warning: {
            DEFAULT: 'var(--color-status-warning)',
            bg: 'var(--color-status-warning-bg)',
            fg: 'var(--color-status-warning-fg)',
            border: 'var(--color-status-warning-border)'
          },
          danger: {
            DEFAULT: 'var(--color-status-danger)',
            bg: 'var(--color-status-danger-bg)',
            fg: 'var(--color-status-danger-fg)',
            border: 'var(--color-status-danger-border)'
          },
          info: {
            DEFAULT: 'var(--color-status-info)',
            bg: 'var(--color-status-info-bg)',
            fg: 'var(--color-status-info-fg)',
            border: 'var(--color-status-info-border)'
          }
        },
        danger: {
          hover: 'var(--color-danger-hover)',
          pressed: 'var(--color-danger-pressed)',
          subtle: 'var(--color-danger-subtle)',
          glow: 'var(--color-danger-glow)'
        },
        fsm: {
          created: {
            DEFAULT: 'var(--color-fsm-created-fg)',
            bg: 'var(--color-fsm-created-bg)',
            fg: 'var(--color-fsm-created-fg)',
            border: 'var(--color-fsm-created-border)'
          },
          assigned: {
            DEFAULT: 'var(--color-fsm-assigned-fg)',
            bg: 'var(--color-fsm-assigned-bg)',
            fg: 'var(--color-fsm-assigned-fg)',
            border: 'var(--color-fsm-assigned-border)'
          },
          'pickup-assigned': {
            DEFAULT: 'var(--color-fsm-pickup-assigned-fg)',
            bg: 'var(--color-fsm-pickup-assigned-bg)',
            fg: 'var(--color-fsm-pickup-assigned-fg)',
            border: 'var(--color-fsm-pickup-assigned-border)'
          },
          'out-for-delivery': {
            DEFAULT: 'var(--color-fsm-out-for-delivery-fg)',
            bg: 'var(--color-fsm-out-for-delivery-bg)',
            fg: 'var(--color-fsm-out-for-delivery-fg)',
            border: 'var(--color-fsm-out-for-delivery-border)'
          },
          'returned-to-sender': {
            DEFAULT: 'var(--color-fsm-returned-to-sender-fg)',
            bg: 'var(--color-fsm-returned-to-sender-bg)',
            fg: 'var(--color-fsm-returned-to-sender-fg)',
            border: 'var(--color-fsm-returned-to-sender-border)'
          },
          'picked-up': {
            DEFAULT: 'var(--color-fsm-picked-up-fg)',
            bg: 'var(--color-fsm-picked-up-bg)',
            fg: 'var(--color-fsm-picked-up-fg)',
            border: 'var(--color-fsm-picked-up-border)'
          },
          'in-transit': {
            DEFAULT: 'var(--color-fsm-in-transit-fg)',
            bg: 'var(--color-fsm-in-transit-bg)',
            fg: 'var(--color-fsm-in-transit-fg)',
            border: 'var(--color-fsm-in-transit-border)'
          },
          delivered: {
            DEFAULT: 'var(--color-fsm-delivered-fg)',
            bg: 'var(--color-fsm-delivered-bg)',
            fg: 'var(--color-fsm-delivered-fg)',
            border: 'var(--color-fsm-delivered-border)'
          },
          failed: {
            DEFAULT: 'var(--color-fsm-failed-fg)',
            bg: 'var(--color-fsm-failed-bg)',
            fg: 'var(--color-fsm-failed-fg)',
            border: 'var(--color-fsm-failed-border)'
          },
          cancelled: {
            DEFAULT: 'var(--color-fsm-cancelled-fg)',
            bg: 'var(--color-fsm-cancelled-bg)',
            fg: 'var(--color-fsm-cancelled-fg)',
            border: 'var(--color-fsm-cancelled-border)'
          }
        },
        role: {
          customer: 'var(--color-role-customer)',
          merchant: 'var(--color-role-merchant)',
          courier: 'var(--color-role-courier)',
          dispatcher: 'var(--color-role-dispatcher)',
          'system-admin': 'var(--color-role-system-admin)'
        },
        sla: {
          warning: {
            DEFAULT: 'var(--color-sla-warning-fg)',
            bg: 'var(--color-sla-warning-bg)',
            fg: 'var(--color-sla-warning-fg)',
            border: 'var(--color-sla-warning-border)'
          },
          critical: {
            DEFAULT: 'var(--color-sla-critical-fg)',
            bg: 'var(--color-sla-critical-bg)',
            fg: 'var(--color-sla-critical-fg)',
            border: 'var(--color-sla-critical-border)'
          }
        }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'IBM Plex Mono', 'ui-monospace', 'monospace']
      },
      fontSize: {
        display: ['36px', { lineHeight: '44px', fontWeight: '700' }],
        h1: ['28px', { lineHeight: '36px', fontWeight: '700' }],
        h2: ['22px', { lineHeight: '30px', fontWeight: '600' }],
        h3: ['18px', { lineHeight: '26px', fontWeight: '600' }],
        'body-l': ['16px', { lineHeight: '24px', fontWeight: '400' }],
        body: ['14px', { lineHeight: '20px', fontWeight: '400' }],
        'body-strong': ['14px', { lineHeight: '20px', fontWeight: '600' }],
        label: ['13px', { lineHeight: '18px', fontWeight: '500' }],
        caption: ['12px', { lineHeight: '16px', fontWeight: '400' }],
        overline: ['11px', { lineHeight: '16px', fontWeight: '600', letterSpacing: '0.06em' }],
        mono: ['14px', { lineHeight: '20px', fontWeight: '400' }],
        'mono-l': ['20px', { lineHeight: '28px', fontWeight: '500' }]
      },
      minHeight: { target: '56px' },
      minWidth: { target: '56px' },
      boxShadow: {
        ambient: '0 8px 24px var(--color-shadow-ambient)',
        overlay: '0 10px 28px var(--color-shadow-overlay)',
        danger: '0 0 16px var(--color-danger-glow)'
      }
    }
  },
  plugins: []
} satisfies Config;
