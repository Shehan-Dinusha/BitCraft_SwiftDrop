import { Camera, Info } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

export type PriceBreakdownType = 'customer' | 'merchant' | 'recalculated';

export interface PriceLine {
  label: string;
  /** Secondary line, e.g. `Chargeable 2.4 kg` or `Gampaha → Colombo · Nearby band`. */
  note?: string;
  amount: number;
}

export interface PriceBreakdownProps {
  /** Customer and Merchant share a layout (the merchant one adds a discount). Recalculated shows a courier re-measure. */
  type?: PriceBreakdownType;
  title?: string;
  currency?: string;
  /** Base, weight and distance fees. */
  lines: PriceLine[];
  subtotal: number;
  /** Speed multiplier line, e.g. `Express × 1.5` with `+225.00`. */
  surcharge?: { label: string; amount: number };
  /** Merchant discount, shown in green and subtracted. */
  discount?: { label: string; note?: string; amount: number };
  total: number;
  /** Recalculated only: the earlier total, shown as `Was LKR 675.00`. */
  previousTotal?: number;
  /** Recalculated only: what the courier measured. */
  measuredNote?: ReactNode;
  onViewEvidence?: () => void;
  /** Small print under the total. */
  footnote?: string;
  className?: string;
}

const formatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const defaultTitles: Record<PriceBreakdownType, string> = {
  customer: 'Delivery fee',
  merchant: 'Delivery fee',
  recalculated: 'Fee updated at pickup'
};

function Amount({
  value,
  signed = false,
  negative = false,
  currency,
  className
}: {
  value: number;
  signed?: boolean;
  negative?: boolean;
  currency: string;
  className?: string;
}) {
  const text = formatter.format(Math.abs(value));
  const prefix = negative ? '−' : signed && value >= 0 ? '+' : '';
  return (
    <span className="flex shrink-0 items-baseline gap-[3px]">
      <span className={cn('text-caption', className ?? 'text-muted')}>{currency}</span>
      <span className={cn('font-mono text-body tabular-nums', className ?? 'text-primary')}>
        {prefix}
        {text}
      </span>
    </span>
  );
}

function Row({
  label,
  note,
  tone = 'primary',
  children
}: {
  label: string;
  note?: string;
  tone?: 'primary' | 'secondary' | 'success';
  children: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-2">
      <div className="flex min-w-0 flex-col">
        <span
          className={cn(
            'text-body',
            tone === 'secondary'
              ? 'text-secondary'
              : tone === 'success'
                ? 'text-fsm-delivered-fg'
                : 'text-primary'
          )}
        >
          {label}
        </span>
        {note ? <span className="text-caption text-secondary">{note}</span> : null}
      </div>
      {children}
    </div>
  );
}

/** Figma PriceBreakdown: fee lines, subtotal, speed surcharge, optional discount and total. Type = Customer / Merchant / Recalculated. */
export function PriceBreakdown({
  type = 'customer',
  title,
  currency = 'LKR',
  lines,
  subtotal,
  surcharge,
  discount,
  total,
  previousTotal,
  measuredNote,
  onViewEvidence,
  footnote,
  className
}: PriceBreakdownProps) {
  return (
    <section
      data-type={type}
      aria-label={title ?? defaultTitles[type]}
      className={cn(
        'flex w-full max-w-[360px] flex-col gap-2.5 rounded-xl border border-border-subtle bg-card p-5',
        className
      )}
    >
      <h3 className="text-h3 text-primary">{title ?? defaultTitles[type]}</h3>

      {type === 'recalculated' && measuredNote ? (
        <div className="flex items-start gap-2 rounded-lg border border-fsm-in-transit-border bg-fsm-in-transit-bg px-3 py-2.5 text-caption text-fsm-in-transit-fg">
          <Info aria-hidden="true" className="mt-px size-4 shrink-0" />
          <span>{measuredNote}</span>
        </div>
      ) : null}

      {lines.map((line) => (
        <Row key={line.label} label={line.label} note={line.note}>
          <Amount value={line.amount} currency={currency} />
        </Row>
      ))}

      <div role="separator" className="h-px bg-border-subtle" />

      <Row label="Subtotal" tone="secondary">
        <Amount value={subtotal} currency={currency} className="text-secondary" />
      </Row>
      {surcharge ? (
        <Row label={surcharge.label}>
          <Amount value={surcharge.amount} signed currency={currency} />
        </Row>
      ) : null}
      {type === 'merchant' && discount ? (
        <Row label={discount.label} note={discount.note} tone="success">
          <Amount
            value={discount.amount}
            negative
            currency={currency}
            className="text-fsm-delivered-fg"
          />
        </Row>
      ) : null}

      <div role="separator" className="h-px bg-border-subtle" />

      <div className="flex items-center justify-between gap-2">
        <span className="text-body-strong text-primary">Total</span>
        <span className="flex items-baseline gap-[3px]">
          <span className="text-caption text-muted">{currency}</span>
          <span className="font-mono text-lg font-medium tabular-nums text-primary">
            {formatter.format(total)}
          </span>
        </span>
      </div>

      {type === 'recalculated' && previousTotal !== undefined ? (
        <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-caption">
          <span className="text-muted">
            Was {currency} {formatter.format(previousTotal)}
          </span>
          <span className="text-fsm-in-transit-fg">
            + {currency} {formatter.format(total - previousTotal)}
          </span>
          {onViewEvidence ? (
            <button
              type="button"
              onClick={onViewEvidence}
              className="ml-auto inline-flex items-center gap-1 text-label text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
            >
              <Camera aria-hidden="true" className="size-3.5" />
              View photo evidence
            </button>
          ) : null}
        </div>
      ) : null}

      {footnote ? (
        <p className="flex items-start gap-2 text-caption text-secondary">
          <Info aria-hidden="true" className="mt-px size-3.5 shrink-0" />
          {footnote}
        </p>
      ) : null}
    </section>
  );
}

export default PriceBreakdown;
