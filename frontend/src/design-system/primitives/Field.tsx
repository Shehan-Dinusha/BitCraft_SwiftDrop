import { forwardRef, useId } from 'react';
import { cn } from '@/utils/cn';
import { Input, type InputProps } from '@/design-system/primitives/Input';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes } from '@/design-system/tokens/density';

export type FieldState = 'default' | 'hover' | 'focused' | 'filled' | 'error' | 'disabled' | 'readOnly';

export interface FieldProps extends Omit<InputProps, 'id'> {
  label: string;
  hint?: string;
  error?: string;
  id?: string;
}

export const Field = forwardRef<HTMLInputElement, FieldProps>(
  ({ label, hint, error, className, inputClassName, id, required, disabled, density, invalid, state, ...props }, ref) => {
    const contextDensity = useDensityMode();
    const recipe = densityRecipes[density ?? contextDensity];
    const generatedId = useId();
    const fieldId = id ?? `field-${generatedId}`;
    const describedBy = error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined;

    return (
      <div className={cn('flex w-full flex-col gap-2', className)}>
        <label htmlFor={fieldId} className={cn('font-semibold text-primary', recipe.caption)}>
          {label} {required && <span className="text-status-danger">*</span>}
        </label>
        <Input
          ref={ref}
          id={fieldId}
          density={density}
          invalid={invalid || Boolean(error)}
          state={state === 'error' ? 'error' : state}
          disabled={disabled}
          required={required}
          aria-describedby={describedBy}
          inputClassName={inputClassName}
          {...props}
        />
        {error ? (
          <span id={`${fieldId}-error`} className={cn('text-status-danger', recipe.caption)}>
            {error}
          </span>
        ) : hint ? (
          <span id={`${fieldId}-hint`} className={cn('text-secondary', recipe.caption)}>
            {hint}
          </span>
        ) : null}
      </div>
    );
  }
);
Field.displayName = 'Field';
