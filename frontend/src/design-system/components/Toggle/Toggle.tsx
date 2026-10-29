import { forwardRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/utils/cn';

export interface ToggleProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'role'> {
  label?: ReactNode;
}

/** Figma Toggle: 36x20 track, 16px thumb. Value = Off / On, State = Default / Disabled. */
export const Toggle = forwardRef<HTMLInputElement, ToggleProps>(
  ({ label, className, disabled, ...props }, ref) => (
    <label
      className={cn(
        'inline-flex items-center gap-2 text-body',
        disabled ? 'cursor-not-allowed text-disabled-fg' : 'cursor-pointer text-primary',
        className
      )}
    >
      <span className="relative inline-flex h-5 w-9 shrink-0 items-center">
        <input
          {...props}
          ref={ref}
          type="checkbox"
          role="switch"
          disabled={disabled}
          className="peer absolute inset-0 size-full cursor-[inherit] opacity-0"
        />
        <span
          aria-hidden="true"
          className="absolute inset-0 rounded-full bg-border-subtle transition-colors peer-checked:bg-brand-primary peer-disabled:bg-disabled-bg peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-border-focus"
        />
        <span
          aria-hidden="true"
          className="pointer-events-none absolute left-0.5 size-4 rounded-full bg-card shadow-sm transition-transform peer-checked:translate-x-4"
        />
      </span>
      {label ? <span>{label}</span> : null}
    </label>
  )
);
Toggle.displayName = 'Toggle';

export default Toggle;
