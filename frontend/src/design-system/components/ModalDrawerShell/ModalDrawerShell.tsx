import { X } from 'lucide-react';
import { ReactNode } from 'react';
import { Button } from '@/design-system/primitives/Button';

export function ModalDrawerShell({
  mode = 'modal',
  open,
  title,
  onClose,
  children
}: {
  mode?: 'modal' | 'drawer';
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  if (!open) return null;
  return (
    <div className={mode === 'modal' ? 'fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4' : 'fixed inset-0 z-50 bg-slate-950/30'}>
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={mode === 'modal'
          ? 'w-full max-w-2xl rounded-lg border border-subtle bg-elevated p-6 shadow-2xl'
          : 'absolute right-0 h-full w-full max-w-xl border-l border-subtle bg-elevated p-6 shadow-2xl'}
      >
        <header className="mb-5 flex items-center justify-between">
          <h2 className="text-lg font-bold text-primary">{title}</h2>
          <Button variant="ghost" onClick={onClose} aria-label="Close"><X size={18} /></Button>
        </header>
        {children}
      </section>
    </div>
  );
}
