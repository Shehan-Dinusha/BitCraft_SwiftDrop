import { forwardRef, InputHTMLAttributes, ReactNode } from 'react';
import { cn } from '@/utils/cn';

export type FieldState = 'default' | 'focused' | 'error' | 'disabled';

export interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
}

export const Field = forwardRef<HTMLInputElement, FieldProps>(
  ({ label, hint, error, leading, trailing, className, id, required, disabled, ...props }, ref) => {
    const fieldId = id ?? `field-${label.toLowerCase().replace(/\W+/g, '-')}`;
    const describedBy = error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined;

    return (
      <label className="flex w-full flex-col gap-2" htmlFor={fieldId}>
        <span className="text-xs font-semibold text-primary">
          {label} {required && <span className="text-status-danger">*</span>}
        </span>
        <span
          className={cn(
            'flex min-h-11 items-center gap-2 rounded-lg border bg-card px-4 py-2 transition',
            error ? 'border-status-danger' : 'border-subtle focus-within:border-focus focus-within:ring-2 focus-within:ring-blue-100',
            disabled && 'cursor-not-allowed opacity-60'
          )}
        >
          {leading}
          <input
            ref={ref}
            id={fieldId}
            className={cn('min-w-0 flex-1 bg-transparent text-sm text-primary outline-none placeholder:text-muted', className)}
            aria-invalid={Boolean(error)}
            aria-describedby={describedBy}
            required={required}
            disabled={disabled}
            {...props}
          />
          {trailing}
        </span>
        {error ? <span id={`${fieldId}-error`} className="text-xs text-status-danger">{error}</span> : hint ? <span id={`${fieldId}-hint`} className="text-xs text-secondary">{hint}</span> : null}
      </label>
    );
  }
);

Field.displayName = 'Field';
