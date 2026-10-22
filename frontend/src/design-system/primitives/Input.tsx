import { InputHTMLAttributes, ReactNode, forwardRef, useState } from 'react';
import { AlertCircle, Eye, EyeOff, Lock, Search, X } from 'lucide-react';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export type InputVariant = 'text' | 'password' | 'search' | 'currency' | 'phone';
export type InputState = 'default' | 'hover' | 'focused' | 'filled' | 'error' | 'disabled' | 'readOnly';

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  /** Field archetype. Drives the input type, monospace treatment and slot defaults. */
  variant?: InputVariant;
  /** Forces a single visual state. Omit for live behaviour driven by `invalid`/`disabled`/`readOnly`. */
  state?: InputState;
  invalid?: boolean;
  readOnly?: boolean;
  disabled?: boolean;
  /** Slot rendered before the input. Defaults to the variant affordance (search/currency/phone). */
  leading?: ReactNode;
  /** Slot rendered after the input. Defaults to the variant affordance (reveal/clear) or the state icon. */
  trailing?: ReactNode;
  density?: DensityMode;
  /** Currency symbol prefix, used when `variant="currency"`. */
  currency?: string;
  /** International dialling prefix, used when `variant="phone"`. */
  countryCode?: string;
  /** Invoked by the search variant's clear affordance. */
  onClear?: () => void;
  /** Classes applied to the inner native input. */
  inputClassName?: string;
}

const variantTypes: Record<InputVariant, string> = {
  text: 'text',
  password: 'password',
  search: 'search',
  currency: 'text',
  phone: 'tel'
};

const variantInputModes: Record<InputVariant, InputHTMLAttributes<HTMLInputElement>['inputMode']> = {
  text: undefined,
  password: undefined,
  search: 'search',
  currency: 'decimal',
  phone: 'tel'
};

const monospacedVariants: InputVariant[] = ['currency', 'phone'];

const focusRing = 'outline outline-2 outline-offset-[-2px] outline-border-focus';
const focusRingError = 'outline outline-2 outline-offset-[-2px] outline-status-danger';
const liveFocusRing = 'focus-within:outline focus-within:outline-2 focus-within:outline-offset-[-2px] focus-within:outline-border-focus';
const liveFocusRingError = 'focus-within:outline focus-within:outline-2 focus-within:outline-offset-[-2px] focus-within:outline-status-danger';

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className,
      inputClassName,
      variant = 'text',
      state,
      invalid = false,
      readOnly = false,
      disabled = false,
      leading,
      trailing,
      density,
      currency = '$',
      countryCode = '+1',
      onClear,
      value,
      defaultValue,
      ...props
    },
    ref
  ) => {
    const contextDensity = useDensityMode();
    const recipe = densityRecipes[density ?? contextDensity];
    const [revealed, setRevealed] = useState(false);

    const isInvalid = invalid || state === 'error';
    const hasValue =
      typeof value === 'string'
        ? value.length > 0
        : typeof defaultValue === 'string'
          ? defaultValue.length > 0
          : false;

    const resolved: InputState =
      state ??
      (disabled
        ? 'disabled'
        : isInvalid
          ? 'error'
          : readOnly
            ? 'readOnly'
            : hasValue
              ? 'filled'
              : 'default');

    const surfaces: Record<InputState, string> = {
      default: 'bg-card border-border-subtle text-primary',
      hover: 'bg-card border-secondary text-primary',
      focused: 'bg-card border-border-subtle text-primary',
      filled: 'bg-card border-border-subtle text-primary',
      error: 'bg-card border-status-danger text-primary',
      disabled: 'bg-disabled-bg border-border-subtle text-disabled-fg',
      readOnly: 'bg-canvas border-border-subtle text-primary'
    };

    const liveHover =
      resolved === 'default' || resolved === 'filled' || resolved === 'hover' ? 'hover:border-secondary' : '';
    const liveFocusEnabled =
      resolved === 'default' || resolved === 'filled' || resolved === 'focused' || resolved === 'error';
    const liveFocus = liveFocusEnabled ? (isInvalid ? liveFocusRingError : liveFocusRing) : '';

    const variantLeading =
      variant === 'currency' ? (
        <span className={cn('shrink-0 font-mono text-muted', recipe.label)}>{currency}</span>
      ) : variant === 'phone' ? (
        <span className={cn('shrink-0 font-mono text-muted', recipe.label)}>{countryCode}</span>
      ) : variant === 'search' ? (
        <Search className={cn('shrink-0 text-muted', recipe.icon)} aria-hidden="true" />
      ) : null;

    const variantTrailing =
      variant === 'password' ? (
        <button
          type="button"
          onClick={() => setRevealed((current) => !current)}
          aria-label={revealed ? 'Hide password' : 'Show password'}
          aria-pressed={revealed}
          className={cn('shrink-0 rounded text-muted transition-colors hover:text-primary', recipe.target)}
        >
          {revealed ? <EyeOff className={recipe.icon} aria-hidden="true" /> : <Eye className={recipe.icon} aria-hidden="true" />}
        </button>
      ) : variant === 'search' && onClear && hasValue ? (
        <button
          type="button"
          onClick={onClear}
          aria-label="Clear search"
          className={cn('shrink-0 rounded text-muted transition-colors hover:text-primary', recipe.target)}
        >
          <X className={recipe.icon} aria-hidden="true" />
        </button>
      ) : null;

    const stateTrailing =
      resolved === 'error' ? (
        <AlertCircle className={cn('shrink-0 text-status-danger', recipe.icon)} aria-hidden="true" />
      ) : resolved === 'readOnly' ? (
        <Lock className={cn('shrink-0 text-muted', recipe.icon)} aria-hidden="true" />
      ) : null;

    const leadingSlot =
      leading ?? variantLeading ? (
        <span className={cn('flex shrink-0 items-center', recipe.icon)}>{leading ?? variantLeading}</span>
      ) : null;

    const trailingSlot = (
      <>
        {variantTrailing}
        {stateTrailing}
        {trailing ? <span className={cn('flex shrink-0 items-center', recipe.icon)}>{trailing}</span> : null}
      </>
    );

    const inputType = variant === 'password' ? (revealed ? 'text' : 'password') : variantTypes[variant];

    return (
      <span
        className={cn(
          'flex w-full items-center gap-2 border transition-colors',
          recipe.fieldControl,
          recipe.radius,
          surfaces[resolved],
          liveHover,
          liveFocus,
          resolved === 'focused' ? (isInvalid ? focusRingError : focusRing) : '',
          className
        )}
      >
        {leadingSlot}
        <input
          ref={ref}
          type={inputType}
          inputMode={variantInputModes[variant]}
          value={value}
          defaultValue={defaultValue}
          readOnly={readOnly}
          disabled={disabled}
          aria-invalid={isInvalid || undefined}
          aria-readonly={readOnly || undefined}
          className={cn(
            'min-w-0 flex-1 bg-transparent outline-none placeholder:text-muted disabled:cursor-not-allowed',
            recipe.label,
            monospacedVariants.includes(variant) && 'font-mono',
            inputClassName
          )}
          {...props}
        />
        {trailingSlot}
      </span>
    );
  }
);
Input.displayName = 'Input';
