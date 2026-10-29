import { useId, type InputHTMLAttributes, type ReactNode } from 'react';
import { MapPin, Scale } from 'lucide-react';
import { cn } from '@/utils/cn';

interface ShellProps {
  label: string;
  required?: boolean;
  helper?: string;
  error?: string;
  className?: string;
}

export interface PostalCodeFieldProps extends ShellProps {
  field: 'postal-code';
  value: string;
  onChange: (value: string) => void;
  /** Town resolved from the code by the caller, shown after the digits. */
  town?: string;
}

export interface WeightFieldProps extends ShellProps {
  field: 'weight';
  value: string;
  onChange: (value: string) => void;
}

export interface DimensionsValue {
  length: string;
  width: string;
  height: string;
}

export interface DimensionsFieldProps extends ShellProps {
  field: 'dimensions';
  value: DimensionsValue;
  onChange: (value: DimensionsValue) => void;
}

export type SpecialFieldProps = PostalCodeFieldProps | WeightFieldProps | DimensionsFieldProps;

const container =
  'flex items-center gap-2 rounded-lg border border-border-subtle bg-card px-4 py-3 focus-within:outline focus-within:outline-2 focus-within:-outline-offset-2 focus-within:outline-border-focus';
const monoInput =
  'min-w-0 flex-1 bg-transparent font-mono text-body tabular-nums text-primary outline-none placeholder:text-muted';

function Shell({
  label,
  required,
  helper,
  error,
  className,
  children,
  fieldId
}: ShellProps & { children: ReactNode; fieldId: string }) {
  const message = error ?? helper;
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <label htmlFor={fieldId} className="text-label font-semibold text-primary">
        {label}
        {required ? <span className="ml-1 font-medium text-status-danger">*</span> : null}
      </label>
      {children}
      {message ? (
        <span
          id={`${fieldId}-message`}
          className={cn('text-caption', error ? 'text-status-danger' : 'text-secondary')}
        >
          {message}
        </span>
      ) : null}
    </div>
  );
}

function numericProps(
  fieldId: string,
  props: Pick<ShellProps, 'error' | 'helper' | 'required'>
): InputHTMLAttributes<HTMLInputElement> {
  return {
    id: fieldId,
    required: props.required,
    'aria-invalid': props.error ? true : undefined,
    'aria-describedby': props.error || props.helper ? `${fieldId}-message` : undefined
  };
}

/**
 * Figma SpecialField: compound inputs for the booking form.
 * `postal-code` (5 digits + town), `weight` (kg, one decimal) and `dimensions` (L × W × H in cm).
 */
export function SpecialField(props: SpecialFieldProps) {
  const generatedId = useId();
  const fieldId = `special-${generatedId}`;

  if (props.field === 'postal-code') {
    return (
      <Shell {...props} fieldId={fieldId}>
        <div className={cn(container, props.error && 'border-status-danger')}>
          <MapPin aria-hidden="true" className="size-[18px] shrink-0 text-muted" />
          <input
            {...numericProps(fieldId, props)}
            inputMode="numeric"
            maxLength={5}
            autoComplete="postal-code"
            value={props.value}
            onChange={(event) => props.onChange(event.target.value.replace(/\D/g, ''))}
            className={cn(monoInput, 'flex-none basis-[5ch]')}
          />
          {props.town ? (
            <span className="truncate text-body text-secondary">{props.town}</span>
          ) : null}
        </div>
      </Shell>
    );
  }

  if (props.field === 'weight') {
    return (
      <Shell {...props} fieldId={fieldId}>
        <div className={cn(container, props.error && 'border-status-danger')}>
          <Scale aria-hidden="true" className="size-[18px] shrink-0 text-muted" />
          <input
            {...numericProps(fieldId, props)}
            inputMode="decimal"
            value={props.value}
            onChange={(event) => props.onChange(event.target.value.replace(/[^\d.]/g, ''))}
            className={monoInput}
          />
          <span className="text-body text-secondary">kg</span>
        </div>
      </Shell>
    );
  }

  const { value, onChange } = props;
  const parts: { key: keyof DimensionsValue; label: string }[] = [
    { key: 'length', label: 'Length' },
    { key: 'width', label: 'Width' },
    { key: 'height', label: 'Height' }
  ];

  return (
    <Shell {...props} fieldId={fieldId}>
      <div role="group" aria-label={props.label} className="flex items-center gap-2">
        {parts.map((part, index) => (
          <div key={part.key} className="contents">
            <div className={cn(container, 'min-w-0 flex-1', props.error && 'border-status-danger')}>
              <input
                {...(index === 0 ? numericProps(fieldId, props) : {})}
                aria-label={`${part.label} in centimetres`}
                inputMode="numeric"
                value={value[part.key]}
                onChange={(event) =>
                  onChange({ ...value, [part.key]: event.target.value.replace(/\D/g, '') })
                }
                className={monoInput}
              />
            </div>
            {index < parts.length - 1 ? (
              <span aria-hidden="true" className="text-body text-muted">
                ×
              </span>
            ) : (
              <span className="text-body text-secondary">cm</span>
            )}
          </div>
        ))}
      </div>
    </Shell>
  );
}

export default SpecialField;
