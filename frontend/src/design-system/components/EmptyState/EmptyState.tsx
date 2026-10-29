import { Inbox, SearchX, WifiOff } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

export type EmptyStateType = 'no-results' | 'empty-queue' | 'offline';

export interface EmptyStateProps {
  type: EmptyStateType;
  /** Overrides the default heading for the type. */
  title?: string;
  /** Overrides the default message for the type. */
  message?: string;
  /** Optional button, e.g. "Clear filters" or "Retry". */
  action?: { label: string; onClick: () => void };
  /** Custom icon replacing the default one. */
  icon?: ReactNode;
  className?: string;
}

const defaults: Record<
  EmptyStateType,
  { title: string; message: string; Icon: typeof Inbox; dashed: boolean }
> = {
  'no-results': {
    title: 'No orders match these filters',
    message: 'Try another date, district or postal code.',
    Icon: SearchX,
    dashed: true
  },
  'empty-queue': {
    title: 'This queue is empty',
    message: 'New parcels will appear here as they arrive.',
    Icon: Inbox,
    dashed: false
  },
  offline: {
    title: 'You are offline',
    message: 'Changes are saved on this device and sync when you reconnect.',
    Icon: WifiOff,
    dashed: false
  }
};

/** Figma EmptyState: centred card with an icon, heading, message and optional action. */
export function EmptyState({ type, title, message, action, icon, className }: EmptyStateProps) {
  const config = defaults[type];
  const Icon = config.Icon;

  return (
    <div
      data-type={type}
      className={cn(
        'flex w-full max-w-80 flex-col items-center gap-3 rounded-xl border bg-card px-6 py-8 text-center',
        config.dashed ? 'border-dashed border-border-subtle' : 'border-border-subtle',
        className
      )}
    >
      <span className="flex size-14 items-center justify-center rounded-full bg-elevated text-secondary">
        {icon ?? <Icon aria-hidden="true" className="size-6" />}
      </span>
      <h3 className="text-h3 text-primary">{title ?? config.title}</h3>
      <p className="text-body text-secondary">{message ?? config.message}</p>
      {action ? (
        <button
          type="button"
          onClick={action.onClick}
          className="inline-flex h-9 items-center rounded-lg border border-border-subtle bg-card px-3.5 text-label text-primary transition-colors hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
        >
          {action.label}
        </button>
      ) : null}
    </div>
  );
}

export default EmptyState;
