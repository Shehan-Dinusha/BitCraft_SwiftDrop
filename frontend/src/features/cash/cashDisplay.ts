import type { CashStateKind } from '@/design-system/components/CashState/CashState';
import type { CashEntryResponse, CashHandoverResponse } from '@/services/api/cashApi';

const amountFormat = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

/** LKR 1,250.00, for sentences; use MoneyAmount where the amount stands on its own. */
export const lkr = (amount: number) => `LKR ${amountFormat.format(amount)}`;

export const kindLabel = (entry: Pick<CashEntryResponse, 'kind'>) =>
  entry.kind === 'COD' ? 'Cash on delivery' : 'Delivery fee';

export const cashTime = (iso?: string | null) =>
  iso
    ? new Date(iso).toLocaleString([], {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit'
      })
    : '—';

/** The status pill of a handover; a shortage or excess shows the difference. */
export function handoverState(handover: CashHandoverResponse): {
  state: CashStateKind;
  amount: number;
} {
  if (handover.status === 'SUBMITTED') return { state: 'pending', amount: handover.expectedAmount };
  const difference = handover.difference ?? 0;
  if (difference < 0) return { state: 'shortage', amount: -difference };
  if (difference > 0) return { state: 'excess', amount: difference };
  return { state: 'handed-over', amount: handover.expectedAmount };
}

/** An amount typed by hand, or null when it is not a plain amount with at most 2 decimals. */
export function parseAmount(input: string): number | null {
  const value = input.replace(/[,\s]/g, '');
  return /^\d{1,10}(\.\d{1,2})?$/.test(value) ? Number(value) : null;
}
