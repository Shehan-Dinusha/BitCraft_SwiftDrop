import { HTMLAttributes, KeyboardEvent, MouseEvent as ReactMouseEvent, ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export type SurfaceCardVariant = 'base' | 'elevated' | 'interactive' | 'selected';
export type SurfaceCardState = 'default' | 'hover' | 'focused' | 'disabled';

export interface SurfaceCardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  /**
   * Surface role. `base` is the standard content card, `elevated` sits above the
   * page, `interactive` is a tappable card, and `selected` marks the active choice.
   */
  variant?: SurfaceCardVariant;
  /** Forces one visual state. Omit for live `:hover`/`:focus-visible` behaviour. */
  state?: SurfaceCardState;
  /** Heading rendered in the header slot. */
  title?: ReactNode;
  /** Extra header content rendered under the title. */
  header?: ReactNode;
  /** Header-right content, typically icon buttons or a badge. */
  actions?: ReactNode;
  /** Footer slot, separated by a hairline. Use for primary/secondary actions. */
  footer?: ReactNode;
  density?: DensityMode;
}

const variantClasses: Record<SurfaceCardVariant, string> = {
  base: 'bg-card border border-border-subtle',
  elevated: 'bg-elevated border border-border-subtle shadow-ambient',
  interactive:
    'bg-card border border-border-subtle hover:border-brand-primary hover:shadow-ambient',
  selected: 'bg-brand-subtle border border-brand-primary'
};

const stateClasses: Record<SurfaceCardState, string> = {
  default: '',
  hover: 'border-secondary hover:border-secondary',
  focused: 'outline outline-2 outline-border-focus shadow-[0_0_0_2px_var(--color-border-focus)]',
  disabled: 'bg-disabled-bg border-border-subtle text-disabled-fg'
};

const liveFocus =
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus focus-visible:shadow-[0_0_0_2px_var(--color-border-focus)]';

export function SurfaceCard({
  variant = 'base',
  state,
  title,
  header,
  actions,
  footer,
  density,
  className,
  children,
  onClick,
  onKeyDown,
  ...props
}: SurfaceCardProps) {
  const contextDensity = useDensityMode();
  const recipe = densityRecipes[density ?? contextDensity];
  const isDisabled = state === 'disabled';
  const isInteractive = variant === 'interactive' || Boolean(onClick);
  const hasHeader = Boolean(title || header || actions);

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    onKeyDown?.(event);
    if (!isInteractive || isDisabled || event.defaultPrevented) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick?.(event as unknown as ReactMouseEvent<HTMLElement>);
    }
  };

  return (
    <section
      {...props}
      onClick={isDisabled ? undefined : onClick}
      onKeyDown={isInteractive ? handleKeyDown : onKeyDown}
      role={isInteractive && !props.role ? 'button' : props.role}
      tabIndex={isInteractive && !isDisabled ? (props.tabIndex ?? 0) : props.tabIndex}
      aria-disabled={isDisabled || undefined}
      className={cn(
        'flex flex-col overflow-hidden rounded-xl text-primary transition-colors',
        variantClasses[variant],
        stateClasses[state ?? 'default'],
        isInteractive && !isDisabled ? liveFocus : '',
        isInteractive && !isDisabled ? 'cursor-pointer' : '',
        isDisabled ? 'pointer-events-none' : '',
        className
      )}
    >
      {hasHeader ? (
        <header
          className={cn(
            'flex items-start justify-between gap-3 border-b border-border-subtle',
            recipe.surfaceSection
          )}
        >
          <div className="min-w-0 flex-1">
            {title ? <h3 className="truncate font-semibold text-primary">{title}</h3> : null}
            {header}
          </div>
          {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
        </header>
      ) : null}
      <div className={cn('min-w-0 flex-1', recipe.surfacePadding)}>{children}</div>
      {footer ? (
        <footer className={cn('border-t border-border-subtle', recipe.surfaceSection)}>
          {footer}
        </footer>
      ) : null}
    </section>
  );
}

export default SurfaceCard;
