import { forwardRef, useId, useState, type ChangeEvent, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  /** Helper text under the field. Replaced by `error` when present. */
  helper?: string;
  error?: string;
  /** Shows the "n / max" counter when `maxLength` is set. */
  showCount?: boolean;
}

/** Figma Textarea: label, multi-line field, helper/error on the left and a character counter on the right. */
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  (
    {
      label,
      helper,
      error,
      showCount = true,
      className,
      id,
      required,
      maxLength,
      value,
      defaultValue,
      onChange,
      rows = 4,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const fieldId = id ?? `textarea-${generatedId}`;
    const [uncontrolledLength, setUncontrolledLength] = useState(String(defaultValue ?? '').length);
    const length = value !== undefined ? String(value).length : uncontrolledLength;
    const message = error ?? helper;
    const messageId = message ? `${fieldId}-message` : undefined;

    const handleChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
      setUncontrolledLength(event.target.value.length);
      onChange?.(event);
    };

    return (
      <div className={cn('flex w-full flex-col gap-2', className)}>
        <label htmlFor={fieldId} className="text-label font-semibold text-primary">
          {label}
          {required ? <span className="ml-1 font-medium text-status-danger">*</span> : null}
        </label>
        <textarea
          {...props}
          ref={ref}
          id={fieldId}
          rows={rows}
          required={required}
          maxLength={maxLength}
          value={value}
          defaultValue={defaultValue}
          onChange={handleChange}
          aria-invalid={error ? true : undefined}
          aria-describedby={messageId}
          className={cn(
            'w-full resize-y rounded-lg border bg-card px-3 py-2.5 text-body text-primary outline-none placeholder:text-muted',
            error
              ? 'border-status-danger focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-status-danger'
              : 'border-border-subtle focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-border-focus',
            'disabled:cursor-not-allowed disabled:bg-disabled-bg disabled:text-disabled-fg'
          )}
        />
        {message || (showCount && maxLength) ? (
          <div className="flex items-start justify-between gap-3 text-caption">
            <span id={messageId} className={error ? 'text-status-danger' : 'text-secondary'}>
              {message}
            </span>
            {showCount && maxLength ? (
              <span className="shrink-0 tabular-nums text-muted">
                {length} / {maxLength}
              </span>
            ) : null}
          </div>
        ) : null}
      </div>
    );
  }
);
Textarea.displayName = 'Textarea';

export default Textarea;
