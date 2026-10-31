import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

export interface ChoiceChipProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  selected?: boolean;
}

/** Figma ChoiceChip: pill used for parcel type, saved addresses and date picks. Exposes `aria-pressed`. */
export const ChoiceChip = forwardRef<HTMLButtonElement, ChoiceChipProps>(
  ({ selected = false, className, children, ...props }, ref) => (
    <button
      {...props}
      ref={ref}
      type="button"
      aria-pressed={selected}
      className={cn(
        'inline-flex items-center rounded-full px-3 py-1.5 text-label transition-colors',
        selected
          ? 'bg-brand-primary text-brand-on'
          : 'bg-elevated text-secondary hover:text-primary',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
        'disabled:cursor-not-allowed disabled:bg-disabled-bg disabled:text-disabled-fg',
        className
      )}
    >
      {children}
    </button>
  )
);
ChoiceChip.displayName = 'ChoiceChip';

export default ChoiceChip;
