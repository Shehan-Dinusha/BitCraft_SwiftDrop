import type { DeliveryState } from '@/types/contracts';

export interface DeliveryStateTokens {
  background: string;
  foreground: string;
  border: string;
  glyph: string;
}

export const deliveryStateColors: Record<DeliveryState, DeliveryStateTokens> = {
  CREATED: {
    background: 'bg-fsm-created-bg',
    foreground: 'text-fsm-created-fg',
    border: 'border-fsm-created-border',
    glyph: '○'
  },
  ASSIGNED: {
    background: 'bg-fsm-assigned-bg',
    foreground: 'text-fsm-assigned-fg',
    border: 'border-fsm-assigned-border',
    glyph: '◈'
  },
  PICKED_UP: {
    background: 'bg-fsm-picked-up-bg',
    foreground: 'text-fsm-picked-up-fg',
    border: 'border-fsm-picked-up-border',
    glyph: '↑'
  },
  IN_TRANSIT: {
    background: 'bg-fsm-in-transit-bg',
    foreground: 'text-fsm-in-transit-fg',
    border: 'border-fsm-in-transit-border',
    glyph: '»'
  },
  OUT_FOR_DELIVERY: {
    background: 'bg-fsm-out-for-delivery-bg',
    foreground: 'text-fsm-out-for-delivery-fg',
    border: 'border-fsm-out-for-delivery-border',
    glyph: '»'
  },
  DELIVERED: {
    background: 'bg-fsm-delivered-bg',
    foreground: 'text-fsm-delivered-fg',
    border: 'border-fsm-delivered-border',
    glyph: '✓'
  },
  FAILED: {
    background: 'bg-fsm-failed-bg',
    foreground: 'text-fsm-failed-fg',
    border: 'border-fsm-failed-border',
    glyph: '!'
  },
  CANCELLED: {
    background: 'bg-fsm-cancelled-bg',
    foreground: 'text-fsm-cancelled-fg',
    border: 'border-fsm-cancelled-border',
    glyph: '—'
  }
};

export const slaColors = {
  warning: {
    background: 'bg-sla-warning-bg',
    foreground: 'text-sla-warning-fg',
    border: 'border-sla-warning-border'
  },
  critical: {
    background: 'bg-sla-critical-bg',
    foreground: 'text-sla-critical-fg',
    border: 'border-sla-critical-border'
  }
} as const;
