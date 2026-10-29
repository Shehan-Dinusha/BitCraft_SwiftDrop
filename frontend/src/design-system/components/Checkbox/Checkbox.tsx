import { forwardRef, useEffect, useRef, type InputHTMLAttributes, type ReactNode } from 'react';
import { Check, Minus } from 'lucide-react';
import { cn } from '@/utils/cn';

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: ReactNode;
  /** Mixed state (dash), e.g. a parent row with some children selected. */
  mixed?: boolean;
}

/** Figma Checkbox: Value = Off / On / Mixed, State = Default / Disabled. */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ label, mixed = false, className, disabled, ...props }, forwardedRef) => {
    const innerRef = useRef<HTMLInputElement | null>(null);

    useEffect(() => {
      if (innerRef.current) innerRef.current.indeterminate = mixed;
    }, [mixed]);

    return (
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
            ref={(node) => {
              innerRef.current = node;
              if (typeof forwardedRef === 'function') forwardedRef(node);
              else if (forwardedRef) forwardedRef.current = node;
            }}
            type="checkbox"
            disabled={disabled}
            aria-checked={mixed ? 'mixed' : undefined}
            className="peer absolute inset-0 size-full cursor-[inherit] opacity-0"
          />
          <span
            aria-hidden="true"
            className="absolute inset-0 rounded border-[1.5px] border-muted bg-card peer-checked:border-brand-primary peer-checked:bg-brand-primary peer-indeterminate:border-brand-primary peer-indeterminate:bg-brand-primary peer-disabled:border-disabled-fg peer-disabled:bg-disabled-bg peer-checked:peer-disabled:bg-disabled-fg peer-indeterminate:peer-disabled:bg-disabled-fg peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-border-focus"
          />
          <Check
            aria-hidden="true"
            strokeWidth={3}
            className="pointer-events-none absolute size-3.5 text-brand-on opacity-0 peer-checked:opacity-100 peer-indeterminate:opacity-0"
          />
          <Minus
            aria-hidden="true"
            strokeWidth={3}
            className="pointer-events-none absolute size-3.5 text-brand-on opacity-0 peer-indeterminate:opacity-100"
          />
        </span>
        {label ? <span>{label}</span> : null}
      </label>
    );
  }
);
Checkbox.displayName = 'Checkbox';

export default Checkbox;
