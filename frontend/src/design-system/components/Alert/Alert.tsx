import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';

export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

export interface AlertProps {
  tone: AlertTone;
  title: string;
  /** Message under the title. */
  children?: ReactNode;
  /** Inline action under the message, e.g. a link or text button ("View details"). */
  action?: ReactNode;
  onDismiss?: () => void;
  dismissLabel?: string;
  className?: string;
}

/** Tone colours come from the FSM triads, as in Figma: info = picked-up, success = delivered, warning = in-transit, danger = failed. */
export const alertToneClasses: Record<
  AlertTone,
  { surface: string; accent: string; title: string; Icon: typeof Info }
> = {
  info: {
    surface: 'border-fsm-picked-up-border bg-fsm-picked-up-bg',
    accent: 'text-fsm-picked-up-fg',
    title: 'text-fsm-picked-up-fg',
    Icon: Info
  },
  success: {
    surface: 'border-fsm-delivered-border bg-fsm-delivered-bg',
    accent: 'text-fsm-delivered-fg',
    title: 'text-fsm-delivered-fg',
    Icon: CheckCircle2
  },
  warning: {
    surface: 'border-fsm-in-transit-border bg-fsm-in-transit-bg',
    accent: 'text-fsm-in-transit-fg',
    title: 'text-fsm-in-transit-fg',
    Icon: TriangleAlert
  },
  danger: {
    surface: 'border-fsm-failed-border bg-fsm-failed-bg',
    accent: 'text-fsm-failed-fg',
    title: 'text-fsm-failed-fg',
    Icon: AlertCircle
  }
};

/** Figma Alert: inline message with icon, title, message and optional action. Tone = Info / Success / Warning / Danger. */
export function Alert({
  tone,
  title,
  children,
  action,
  onDismiss,
  dismissLabel = 'Dismiss',
  className
}: AlertProps) {
  const { surface, accent, title: titleClass, Icon } = alertToneClasses[tone];

  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      data-tone={tone}
      className={cn('flex items-start gap-3 rounded-[10px] border px-4 py-3', surface, className)}
    >
      <Icon aria-hidden="true" className={cn('mt-0.5 size-5 shrink-0', accent)} />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <p className={cn('text-body-strong', titleClass)}>{title}</p>
        {children ? <div className="text-body text-primary">{children}</div> : null}
        {action ? <div className={cn('mt-0.5 text-label', accent)}>{action}</div> : null}
      </div>
      {onDismiss ? (
        <button
          type="button"
          onClick={onDismiss}
          aria-label={dismissLabel}
          className={cn(
            'inline-flex size-6 shrink-0 items-center justify-center rounded-md opacity-70 transition-opacity hover:opacity-100',
            'focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus',
            accent
          )}
        >
          <X aria-hidden="true" className="size-4" />
        </button>
      ) : null}
    </div>
  );
}

export default Alert;
