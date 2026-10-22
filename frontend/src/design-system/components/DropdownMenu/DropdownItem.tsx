import { ReactNode } from 'react';
import { Check } from 'lucide-react';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export type DropdownItemStatus = 'success' | 'warning' | 'danger' | 'info';

export interface DropdownItemProps {
  children: ReactNode;
  /** Marks the row as the current choice. */
  selected?: boolean;
  /** Keyboard/pointer active row (roving highlight). */
  active?: boolean;
  disabled?: boolean;
  /** Leading status dot. */
  status?: DropdownItemStatus;
  /** Secondary text rendered at the end of the row. */
  description?: string;
  icon?: ReactNode;
  onSelect?: () => void;
  id?: string;
  density?: DensityMode;
  className?: string;
}

const statusDots: Record<DropdownItemStatus, string> = {
  success: 'bg-status-success',
  warning: 'bg-status-warning',
  danger: 'bg-status-danger',
  info: 'bg-status-info'
};

export function DropdownItem({
  children,
  selected = false,
  active = false,
  disabled = false,
  status,
  description,
  icon,
  onSelect,
  id,
  density,
  className
}: DropdownItemProps) {
  const contextDensity = useDensityMode();
  const recipe = densityRecipes[density ?? contextDensity];

  return (
    <div
      role="option"
      id={id}
      aria-selected={selected}
      aria-disabled={disabled || undefined}
      onClick={() => {
        if (disabled) return;
        onSelect?.();
      }}
      className={cn(
        'flex cursor-pointer items-center gap-2 rounded-md transition-colors',
        recipe.menuItem,
        recipe.label,
        disabled ? 'cursor-not-allowed text-disabled-fg' : selected ? 'bg-brand-subtle text-brand-primary' : 'text-primary',
        active && !selected && !disabled ? 'bg-canvas' : '',
        className
      )}
    >
      {status ? <span className={cn('size-2 shrink-0 rounded-full', statusDots[status])} aria-hidden="true" /> : null}
      {icon ? <span className={cn('flex shrink-0 items-center', recipe.icon)}>{icon}</span> : null}
      <span className="min-w-0 flex-1 truncate">{children}</span>
      {description ? <span className={cn('shrink-0 text-muted', recipe.caption)}>{description}</span> : null}
      {selected ? <Check className={cn('shrink-0 text-brand-primary', recipe.icon)} aria-hidden="true" /> : null}
    </div>
  );
}

export default DropdownItem;
