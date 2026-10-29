import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

export interface QueueChipProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  /** Queue name, e.g. `Created`, `At Hub`, `Failed / Return`. */
  label: string;
  /** Parcels currently in the queue. */
  count: number;
  selected?: boolean;
}

/** Figma QueueChip: dispatcher filter pill with a live count badge. */
export const QueueChip = forwardRef<HTMLButtonElement, QueueChipProps>(
  ({ label, count, selected = false, className, ...props }, ref) => (
    <button
      {...props}
      ref={ref}
      type="button"
      aria-pressed={selected}
      className={cn(
        'inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-label transition-colors',
        selected
          ? 'border-border-selected bg-brand-subtle text-brand-pressed shadow-[inset_0_0_0_0.5px_var(--color-border-selected)]'
          : 'border-border-subtle bg-card text-primary hover:bg-elevated',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
        className
      )}
    >
      {label}
      <span
        className={cn(
          'rounded-full px-[7px] py-px text-caption font-semibold tabular-nums',
          selected ? 'bg-brand-primary text-brand-on' : 'bg-elevated text-secondary'
        )}
      >
        {count}
      </span>
    </button>
  )
);
QueueChip.displayName = 'QueueChip';

export default QueueChip;
