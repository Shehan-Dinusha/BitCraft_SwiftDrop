import { cn } from '@/utils/cn';

export type MoneyAmountSize = 'sm' | 'md' | 'lg';

export interface MoneyAmountProps {
  /** Numeric amount; formatted as `1,250.00`. Strings are shown as given. */
  amount: number | string;
  currency?: string;
  /** Figma sizes: `sm` = S, `md` = M, `lg` = L. */
  size?: MoneyAmountSize;
  /** Use the surrounding text colour instead of the muted/primary pair (e.g. inside a coloured pill). */
  inheritColor?: boolean;
  className?: string;
}

const sizeClasses: Record<MoneyAmountSize, { currency: string; amount: string }> = {
  sm: { currency: 'text-caption text-muted', amount: 'font-mono text-xs leading-5' },
  md: { currency: 'text-label text-muted', amount: 'font-mono text-body' },
  lg: { currency: 'text-body-strong text-muted', amount: 'font-mono text-mono-l' }
};

const formatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

/** Figma MoneyAmount: currency prefix plus a tabular mono amount, e.g. `LKR 1,250.00`. */
export function MoneyAmount({
  amount,
  currency = 'LKR',
  size = 'md',
  inheritColor = false,
  className
}: MoneyAmountProps) {
  const sizing = sizeClasses[size];
  return (
    <span className={cn('inline-flex items-baseline gap-1', className)}>
      <span className={cn(sizing.currency, inheritColor && 'text-current opacity-80')}>
        {currency}
      </span>
      <span
        className={cn(
          'tabular-nums',
          inheritColor ? 'text-current' : 'text-primary',
          sizing.amount
        )}
      >
        {typeof amount === 'number' ? formatter.format(amount) : amount}
      </span>
    </span>
  );
}

export default MoneyAmount;
