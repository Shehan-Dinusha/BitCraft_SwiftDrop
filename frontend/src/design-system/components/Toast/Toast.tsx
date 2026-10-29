import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode
} from 'react';
import { X } from 'lucide-react';
import { cn } from '@/utils/cn';
import { alertToneClasses, type AlertTone } from '@/design-system/components/Alert/Alert';

export type ToastTone = AlertTone;

export interface ToastOptions {
  tone?: ToastTone;
  title: string;
  message?: ReactNode;
}

export interface ToastProps extends ToastOptions {
  onClose?: () => void;
  className?: string;
}

/** Figma Toast: card with a coloured accent bar, icon, title, message and close button. */
export function Toast({ tone = 'info', title, message, onClose, className }: ToastProps) {
  const { accent, Icon } = alertToneClasses[tone];
  const accentBar: Record<ToastTone, string> = {
    info: 'bg-fsm-picked-up-fg',
    success: 'bg-fsm-delivered-fg',
    warning: 'bg-fsm-in-transit-fg',
    danger: 'bg-fsm-failed-fg'
  };

  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      data-tone={tone}
      className={cn(
        'flex w-[380px] max-w-full items-stretch gap-3 rounded-xl border border-border-subtle bg-card px-4 py-3.5 shadow-ambient',
        className
      )}
    >
      <span aria-hidden="true" className={cn('w-1 shrink-0 rounded-sm', accentBar[tone])} />
      <Icon aria-hidden="true" className={cn('mt-0.5 size-5 shrink-0', accent)} />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <p className="text-body-strong text-primary">{title}</p>
        {message ? <div className="text-caption text-secondary">{message}</div> : null}
      </div>
      {onClose ? (
        <button
          type="button"
          onClick={onClose}
          aria-label="Close notification"
          className="inline-flex size-4 shrink-0 text-muted hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
        >
          <X aria-hidden="true" className="size-4" />
        </button>
      ) : null}
    </div>
  );
}

/** Auto-dismiss time and visible cap from the Figma spec. */
export const TOAST_DURATION_MS = 5000;
export const TOAST_MAX_VISIBLE = 3;

interface ToastEntry extends ToastOptions {
  id: number;
}

interface ToastContextValue {
  toast: (options: ToastOptions) => number;
  dismiss: (id: number) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

function ToastItem({ entry, onDismiss }: { entry: ToastEntry; onDismiss: (id: number) => void }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(entry.id), TOAST_DURATION_MS);
    return () => window.clearTimeout(timer);
  }, [entry.id, onDismiss]);

  return (
    <Toast
      tone={entry.tone}
      title={entry.title}
      message={entry.message}
      onClose={() => onDismiss(entry.id)}
    />
  );
}

/** Mount once near the app root. Shows at most 3 toasts; each closes after 5 seconds. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastEntry[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((entry) => entry.id !== id));
  }, []);

  const toast = useCallback((options: ToastOptions) => {
    const id = nextId.current++;
    setToasts((current) => [...current, { ...options, id }].slice(-TOAST_MAX_VISIBLE));
    return id;
  }, []);

  const value = useMemo(() => ({ toast, dismiss }), [toast, dismiss]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-[380px] max-w-[calc(100vw-2rem)] flex-col gap-2"
      >
        {toasts.map((entry) => (
          <div key={entry.id} className="pointer-events-auto">
            <ToastItem entry={entry} onDismiss={dismiss} />
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used inside <ToastProvider>.');
  return context;
}

export default Toast;
