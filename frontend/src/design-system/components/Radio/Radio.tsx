import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/utils/cn';

export interface RadioProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: ReactNode;
}

/** Figma Radio: Value = Off / On, State = Default / Disabled. Group radios with a shared `name`. */
export const Radio = forwardRef<HTMLInputElement, RadioProps>(
  ({ label, className, disabled, ...props }, ref) => (
    <label
      className={cn(
        'inline-flex items-center gap-2 text-body',
        disabled ? 'cursor-not-allowed text-disabled-fg' : 'cursor-pointer text-primary',
        className
      )}
    >
      <span className="relative inline-flex size-[18px] shrink-0 items-center justify-center">
        <input
          {...props}
          ref={ref}
          type="radio"
          disabled={disabled}
          className="peer absolute inset-0 size-full cursor-[inherit] opacity-0"
        />
        <span
          aria-hidden="true"
          className="absolute inset-0 rounded-full border-[1.5px] border-muted bg-card peer-checked:border-brand-primary peer-checked:bg-brand-primary peer-disabled:border-disabled-fg peer-disabled:bg-disabled-bg peer-checked:peer-disabled:bg-disabled-fg peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-border-focus"
        />
        <span
          aria-hidden="true"
          className="pointer-events-none absolute size-2 rounded-full bg-card opacity-0 peer-checked:opacity-100"
        />
      </span>
      {label ? <span>{label}</span> : null}
    </label>
  )
);
Radio.displayName = 'Radio';

export default Radio;
