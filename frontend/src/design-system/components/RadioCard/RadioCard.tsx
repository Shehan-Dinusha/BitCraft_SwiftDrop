import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/utils/cn';

export interface RadioCardProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'type' | 'title'
> {
  title: string;
  caption?: string;
  /** Formatted fee, e.g. `450.00`. Rendered in the mono face after the currency. */
  fee?: string;
  currency?: string;
  /** Icon shown in the 36px tile on the left. */
  icon?: ReactNode;
}

/** Figma RadioCard: a selectable option card (e.g. Standard / Express). Group with a shared `name`. */
export const RadioCard = forwardRef<HTMLInputElement, RadioCardProps>(
  ({ title, caption, fee, currency = 'LKR', icon, className, ...props }, ref) => (
    <label
      className={cn(
        'group relative flex cursor-pointer items-center gap-3 rounded-xl border border-border-subtle bg-card px-4 py-3.5',
        'has-[:checked]:border-border-selected has-[:checked]:bg-brand-subtle has-[:checked]:shadow-[inset_0_0_0_1px_var(--color-border-selected)]',
        'has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-border-focus',
        'has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60',
        className
      )}
    >
      <input {...props} ref={ref} type="radio" className="peer sr-only" />
      {icon ? (
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-elevated text-secondary [&_svg]:size-[18px]">
          {icon}
        </span>
      ) : null}
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-body-strong text-primary">{title}</span>
        {caption ? <span className="text-caption text-secondary">{caption}</span> : null}
        {fee ? (
          <span className="flex items-baseline gap-1">
            <span className="text-caption text-muted">{currency}</span>
            <span className="font-mono text-body tabular-nums text-primary">{fee}</span>
          </span>
        ) : null}
      </span>
      <span
        aria-hidden="true"
        className="relative flex size-[18px] shrink-0 items-center justify-center rounded-full border-[1.5px] border-muted bg-card group-has-[:checked]:border-brand-primary group-has-[:checked]:bg-brand-primary"
      >
        <span className="size-2 rounded-full bg-card opacity-0 group-has-[:checked]:opacity-100" />
      </span>
    </label>
  )
);
RadioCard.displayName = 'RadioCard';

export default RadioCard;
