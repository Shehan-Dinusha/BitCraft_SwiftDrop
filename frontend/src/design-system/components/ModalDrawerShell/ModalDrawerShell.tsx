import { X } from 'lucide-react';
import { ReactNode, useEffect, useId, useRef } from 'react';
import { cn } from '@/utils/cn';
import { Button } from '@/design-system/primitives/Button';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export type ModalDrawerMode = 'modal' | 'drawer';
export type ModalDrawerSize = 'sm' | 'md' | 'lg';

export interface ModalDrawerShellProps {
  mode?: ModalDrawerMode;
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  /** Extra header content under the title, e.g. a status badge. */
  header?: ReactNode;
  /** Footer slot. Sits below the scroll area, always visible. */
  footer?: ReactNode;
  /** `md` is 800x480 for modals and 700x720 for drawers. */
  size?: ModalDrawerSize;
  /** Supporting text announced with the dialog. */
  description?: string;
  /**
   * Renders the panel in normal flow instead of a fixed overlay, without the
   * scrim, scroll lock, or Escape handling. Use for docs and catalogs only.
   */
  inline?: boolean;
  density?: DensityMode;
  className?: string;
}

const sizes: Record<ModalDrawerMode, Record<ModalDrawerSize, string>> = {
  modal: {
    sm: 'max-w-[560px] max-h-[380px]',
    md: 'max-w-[800px] max-h-[480px]',
    lg: 'max-w-[1040px] max-h-[640px]'
  },
  drawer: {
    sm: 'max-w-[480px] max-h-[520px]',
    md: 'max-w-[700px] max-h-[720px]',
    lg: 'max-w-[960px] max-h-[880px]'
  }
};

export function ModalDrawerShell({
  mode = 'modal',
  open,
  title,
  onClose,
  children,
  header,
  footer,
  size = 'md',
  description,
  inline = false,
  density,
  className
}: ModalDrawerShellProps) {
  const contextDensity = useDensityMode();
  const recipe = densityRecipes[density ?? contextDensity];
  const generatedId = useId();
  const titleId = `dialog-title-${generatedId}`;
  const descriptionId = `dialog-description-${generatedId}`;
  const panelRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!open || inline) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, inline, onClose]);

  useEffect(() => {
    if (!open || inline) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    panelRef.current?.focus();
    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [open, inline]);

  if (!open) return null;

  const panel = (
    <section
      ref={panelRef}
      role="dialog"
      aria-modal={inline ? undefined : true}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      tabIndex={-1}
      className={cn(
        'flex w-full flex-col overflow-hidden border-border-subtle bg-elevated text-primary shadow-overlay',
        mode === 'drawer' && !inline ? 'h-full border-l' : 'rounded-xl border',
        sizes[mode][size],
        className
      )}
    >
      <header
        className={cn(
          'flex items-start justify-between gap-3 border-b border-border-subtle',
          recipe.surfaceSection
        )}
      >
        <div className="min-w-0 flex-1">
          <h2 id={titleId} className="truncate text-lg font-bold text-primary">
            {title}
          </h2>
          {description ? (
            <p id={descriptionId} className={cn('text-secondary', recipe.caption)}>
              {description}
            </p>
          ) : null}
          {header}
        </div>
        <Button
          variant="ghost"
          density={density}
          className="text-secondary"
          onClick={onClose}
          aria-label="Close dialog"
        >
          <X size={18} />
        </Button>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className={recipe.surfacePadding}>{children}</div>
      </div>
      {footer ? (
        <footer className={cn('border-t border-border-subtle', recipe.surfaceSection)}>
          {footer}
        </footer>
      ) : null}
    </section>
  );

  if (inline) {
    return <div className="flex w-full justify-center">{panel}</div>;
  }

  return (
    <div
      className={cn(
        'fixed inset-0 z-50',
        mode === 'modal' ? 'grid place-items-center bg-scrim p-4' : 'flex justify-end bg-scrim-soft'
      )}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      {panel}
    </div>
  );
}

export default ModalDrawerShell;
