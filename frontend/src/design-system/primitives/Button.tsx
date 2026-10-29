import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'destructive';
export type ButtonState = 'default' | 'hover' | 'pressed' | 'focused' | 'disabled' | 'loading';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  density?: DensityMode;
  state?: ButtonState;
  loading?: boolean;
  loadingLabel?: string;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
}

const baseClasses: Record<ButtonVariant, string> = {
  primary: 'bg-brand-primary text-brand-on',
  secondary: 'bg-disabled-bg text-primary',
  outline: 'bg-transparent text-brand-primary border border-border-subtle',
  ghost: 'bg-transparent text-brand-primary',
  destructive: 'bg-status-danger text-brand-on'
};

const hoverClasses: Record<ButtonVariant, string> = {
  primary: 'bg-brand-hover',
  secondary: 'bg-elevated text-primary',
  outline: 'bg-elevated text-brand-primary',
  ghost: 'bg-elevated text-brand-primary',
  destructive: 'bg-danger-hover'
};

const pressedClasses: Record<ButtonVariant, string> = {
  primary: 'bg-brand-pressed',
  secondary: 'bg-brand-subtle text-primary',
  outline: 'bg-brand-subtle text-brand-primary',
  ghost: 'bg-brand-subtle text-brand-primary',
  destructive: 'bg-danger-pressed'
};

const disabledClasses: Record<ButtonVariant, string> = {
  primary: 'bg-disabled-bg text-disabled-fg',
  secondary: 'bg-disabled-bg text-disabled-fg',
  outline: 'bg-disabled-bg text-disabled-fg border border-border-subtle',
  ghost: 'bg-disabled-bg text-disabled-fg',
  destructive: 'bg-disabled-bg text-disabled-fg'
};

const liveClasses: Record<ButtonVariant, string> = {
  primary: 'hover:bg-brand-hover active:bg-brand-pressed',
  secondary:
    'hover:bg-elevated hover:text-primary active:bg-brand-subtle active:text-primary',
  outline:
    'hover:bg-elevated hover:text-brand-primary active:bg-brand-subtle active:text-brand-primary',
  ghost:
    'hover:bg-elevated hover:text-brand-primary active:bg-brand-subtle active:text-brand-primary',
  destructive: 'hover:bg-danger-hover active:bg-danger-pressed'
};

const focusRing =
  'outline outline-2 outline-border-focus shadow-[0_0_0_2px_var(--color-border-focus)]';
const focusRingVisible =
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus focus-visible:shadow-[0_0_0_2px_var(--color-border-focus)]';

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      className,
      variant = 'primary',
      density,
      state,
      loading = false,
      loadingLabel,
      leftIcon,
      rightIcon,
      disabled,
      children,
      ...props
    },
    ref
  ) => {
    const contextMode = useDensityMode();
    const recipe = densityRecipes[density ?? contextMode];
    const resolved: ButtonState =
      state ?? (disabled ? 'disabled' : loading ? 'loading' : 'default');
    const isLoading = resolved === 'loading';
    const isDisabled = resolved === 'disabled' || isLoading || disabled === true;

    const stateClass =
      resolved === 'hover'
        ? cn(baseClasses[variant], hoverClasses[variant])
        : resolved === 'pressed'
          ? cn(baseClasses[variant], pressedClasses[variant])
          : resolved === 'focused'
            ? cn(baseClasses[variant], focusRing)
            : resolved === 'disabled'
              ? disabledClasses[variant]
              : baseClasses[variant];

    const interactionClass =
      resolved === 'default' ? cn(liveClasses[variant], focusRingVisible) : '';

    const label = isLoading && loadingLabel ? loadingLabel : children;
    const leading = isLoading ? (
      <span
        className={cn(
          'inline-flex shrink-0 items-center justify-center',
          recipe.icon,
          '[&_svg]:size-full'
        )}
      >
        <Loader2 className="animate-spin" aria-hidden="true" />
      </span>
    ) : leftIcon ? (
      <span
        className={cn(
          'inline-flex shrink-0 items-center justify-center',
          recipe.icon,
          '[&_svg]:size-full'
        )}
      >
        {leftIcon}
      </span>
    ) : null;

    return (
      <button
        ref={ref}
        disabled={isDisabled}
        aria-busy={isLoading || undefined}
        data-variant={variant}
        data-state={resolved}
        className={cn(
          'inline-flex select-none items-center justify-center whitespace-nowrap font-semibold transition-colors duration-150',
          recipe.control,
          recipe.label,
          recipe.gap,
          recipe.radius,
          stateClass,
          interactionClass,
          isDisabled && 'cursor-not-allowed',
          className
        )}
        {...props}
      >
        {leading}
        {label}
        {rightIcon ? (
          <span
            className={cn(
              'inline-flex shrink-0 items-center justify-center',
              recipe.icon,
              '[&_svg]:size-full'
            )}
          >
            {rightIcon}
          </span>
        ) : null}
      </button>
    );
  }
);
Button.displayName = 'Button';
