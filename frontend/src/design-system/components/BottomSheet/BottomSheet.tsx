import { X } from 'lucide-react';
import { useEffect, useId, type ReactNode } from 'react';
import { cn } from '@/utils/cn';

export interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  /** Renders in the page flow instead of fixed over it (used by the catalog). */
  inline?: boolean;
  className?: string;
}

/** Figma BottomSheet: mobile sheet with a drag handle, title with a close button, and content. Closes on Esc or scrim tap. */
export function BottomSheet({
  open,
  onClose,
  title,
  children,
  inline = false,
  className
}: BottomSheetProps) {
  const titleId = useId();

  useEffect(() => {
    if (!open || inline) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, inline, onClose]);

  if (!open) return null;

  const sheet = (
    <div
      role="dialog"
      aria-modal={inline ? undefined : true}
      aria-labelledby={titleId}
      className={cn(
        'flex w-full max-w-[390px] flex-col gap-3.5 rounded-t-2xl bg-card px-5 pb-6 pt-2.5 shadow-overlay',
        className
      )}
    >
      <span aria-hidden="true" className="mx-auto h-1 w-10 rounded-sm bg-border-subtle" />
      <div className="flex items-center justify-between gap-2">
        <h2 id={titleId} className="text-h3 text-primary">
          {title}
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="inline-flex size-8 items-center justify-center rounded-lg text-secondary hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
        >
          <X aria-hidden="true" className="size-5" />
        </button>
      </div>
      {children}
    </div>
  );

  if (inline) return sheet;

  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center">
      <button
        type="button"
        aria-label="Close sheet"
        tabIndex={-1}
        onClick={onClose}
        className="absolute inset-0 bg-scrim"
      />
      <div className="relative w-full max-w-[390px]">{sheet}</div>
    </div>
  );
}

export default BottomSheet;
