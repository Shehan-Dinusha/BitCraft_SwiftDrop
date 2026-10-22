import { ReactNode, RefObject, useEffect, useRef } from 'react';
import { Search } from 'lucide-react';
import { cn } from '@/utils/cn';
import { DensityProvider, useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export interface DropdownMenuProps {
  /** Renders nothing when false. */
  open: boolean;
  onClose?: () => void;
  /** `role="option"` children. */
  children: ReactNode;
  /** Renders a filter field above the option list. */
  searchable?: boolean;
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  searchPlaceholder?: string;
  /** Reference for the filter field, used to move focus on open. */
  searchInputRef?: RefObject<HTMLInputElement>;
  /** `aria-activedescendant` target of the filter field. */
  searchActiveDescendant?: string;
  /** `aria-controls` target of the filter field. */
  searchListboxId?: string;
  /** Renders below the option list, separated by a hairline. */
  footer?: ReactNode;
  /** Accessible name of the listbox. */
  label?: string;
  id?: string;
  density?: DensityMode;
  className?: string;
  /** Places the panel above the anchor instead of below it. */
  placement?: 'top' | 'bottom';
}

export function DropdownMenu({
  open,
  onClose,
  children,
  searchable = false,
  searchValue,
  onSearchChange,
  searchPlaceholder = 'Search…',
  searchInputRef,
  searchActiveDescendant,
  searchListboxId,
  footer,
  label,
  id,
  density,
  className,
  placement = 'bottom'
}: DropdownMenuProps) {
  const contextDensity = useDensityMode();
  const resolvedDensity = density ?? contextDensity;
  const recipe = densityRecipes[resolvedDensity];
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open || !onClose) return;
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) onClose();
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      ref={rootRef}
      className={cn(
        'absolute z-50 w-full rounded-lg border border-border-subtle bg-elevated p-2 shadow-ambient',
        placement === 'top' ? 'bottom-full mb-2' : 'top-full mt-2',
        className
      )}
    >
      {searchable ? (
        <div className={cn('mb-2 flex items-center gap-2 rounded-md border border-border-subtle bg-card px-3', recipe.fieldControl)}>
          <Search className={cn('shrink-0 text-muted', recipe.icon)} aria-hidden="true" />
          <input
            ref={searchInputRef}
            value={searchValue}
            onChange={(event) => onSearchChange?.(event.target.value)}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
            role={searchListboxId ? 'combobox' : undefined}
            aria-controls={searchListboxId}
            aria-activedescendant={searchActiveDescendant}
            aria-autocomplete="list"
            className={cn('min-w-0 flex-1 bg-transparent text-primary outline-none placeholder:text-muted', recipe.label)}
          />
        </div>
      ) : null}
      <div role="listbox" id={id} aria-label={label} className="max-h-72 overflow-y-auto">
        <DensityProvider density={resolvedDensity}>{children}</DensityProvider>
      </div>
      {footer ? (
        <div className={cn('mt-2 border-t border-border-subtle pt-2 text-muted', recipe.caption)}>{footer}</div>
      ) : null}
    </div>
  );
}

export default DropdownMenu;
