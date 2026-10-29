import { Check, X } from 'lucide-react';
import { cn } from '@/utils/cn';

export type TimelineStepState = 'done' | 'current' | 'upcoming' | 'failed';

export interface TimelineStepProps {
  state: TimelineStepState;
  title: string;
  /** e.g. `26 Oct 2026, 14:32 · Colombo`. */
  meta?: string;
  /** Extra line, such as a failure reason. */
  note?: string;
  /** The last step has no connector line below its marker. */
  isLast?: boolean;
  className?: string;
}

const markerClasses: Record<TimelineStepState, string> = {
  done: 'bg-fsm-delivered-fg text-brand-on',
  current: 'border-2 border-brand-primary bg-brand-subtle',
  upcoming: 'border-2 border-border-subtle bg-card',
  failed: 'bg-fsm-failed-fg text-brand-on'
};

const lineClasses: Record<TimelineStepState, string> = {
  done: 'bg-fsm-delivered-border',
  current: 'bg-border-subtle',
  upcoming: 'bg-border-subtle',
  failed: 'bg-border-subtle'
};

/** Figma TimelineStep: rail marker + title, meta and optional note. State = Done / Current / Upcoming / Failed. */
export function TimelineStep({
  state,
  title,
  meta,
  note,
  isLast = false,
  className
}: TimelineStepProps) {
  return (
    <li
      data-state={state}
      aria-current={state === 'current' ? 'step' : undefined}
      className={cn('flex gap-3', className)}
    >
      <div className="flex w-5 shrink-0 flex-col items-center gap-1">
        <span
          aria-hidden="true"
          className={cn(
            'flex size-5 shrink-0 items-center justify-center rounded-full',
            markerClasses[state]
          )}
        >
          {state === 'done' ? <Check className="size-3" strokeWidth={3} /> : null}
          {state === 'failed' ? <X className="size-3" strokeWidth={3} /> : null}
          {state === 'current' ? <span className="size-2 rounded-full bg-brand-primary" /> : null}
        </span>
        {isLast ? null : (
          <span aria-hidden="true" className={cn('w-0.5 flex-1', lineClasses[state])} />
        )}
      </div>
      <div className={cn('flex min-w-0 flex-1 flex-col gap-0.5', isLast ? 'pb-0' : 'pb-5')}>
        <span
          className={cn(
            'text-body',
            state === 'upcoming' && 'text-muted',
            state === 'failed' && 'font-semibold text-fsm-failed-fg',
            (state === 'done' || state === 'current') && 'font-semibold text-primary'
          )}
        >
          {title}
        </span>
        {meta ? <span className="text-caption text-secondary">{meta}</span> : null}
        {note ? (
          <span
            className={cn(
              'text-caption',
              state === 'failed' ? 'text-fsm-failed-fg' : 'text-secondary'
            )}
          >
            {note}
          </span>
        ) : null}
      </div>
    </li>
  );
}

export default TimelineStep;
