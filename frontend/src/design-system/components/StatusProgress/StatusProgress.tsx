import { Check } from 'lucide-react';
import { cn } from '@/utils/cn';

export const STATUS_PROGRESS_STEPS = [
  { status: 'CREATED', label: 'Created' },
  { status: 'PICKED_UP', label: 'Picked Up' },
  { status: 'IN_TRANSIT', label: 'In Transit' },
  { status: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { status: 'DELIVERED', label: 'Delivered' }
] as const;

export type StatusProgressStatus = (typeof STATUS_PROGRESS_STEPS)[number]['status'];

export interface StatusProgressProps {
  /** The parcel's current public status. Steps before it are done; `DELIVERED` completes the bar. */
  current: StatusProgressStatus;
  className?: string;
}

/** Figma StatusProgress: the five public steps with connectors. Customer parcel hero and mobile sheet. */
export function StatusProgress({ current, className }: StatusProgressProps) {
  const currentIndex = STATUS_PROGRESS_STEPS.findIndex((step) => step.status === current);
  const lastIndex = STATUS_PROGRESS_STEPS.length - 1;

  return (
    <ol
      aria-label={`Parcel progress, step ${currentIndex + 1} of ${STATUS_PROGRESS_STEPS.length}`}
      className={cn('grid w-full grid-cols-5', className)}
    >
      {STATUS_PROGRESS_STEPS.map((step, index) => {
        const isDone = index < currentIndex || (index === currentIndex && index === lastIndex);
        const isCurrent = index === currentIndex && !isDone;
        const leftColoured = index <= currentIndex;
        const rightColoured = index < currentIndex;

        return (
          <li
            key={step.status}
            data-state={isDone ? 'done' : isCurrent ? 'current' : 'upcoming'}
            aria-current={isCurrent ? 'step' : undefined}
            className="flex flex-col items-center gap-2"
          >
            <div className="flex w-full items-center">
              <span
                aria-hidden="true"
                className={cn(
                  'h-[3px] flex-1',
                  index === 0
                    ? 'invisible'
                    : leftColoured
                      ? 'bg-fsm-delivered-fg'
                      : 'bg-border-subtle'
                )}
              />
              <span
                aria-hidden="true"
                className={cn(
                  'flex size-6 shrink-0 items-center justify-center rounded-full',
                  isDone && 'bg-fsm-delivered-fg text-brand-on',
                  isCurrent && 'border-2 border-brand-primary bg-brand-subtle',
                  !isDone && !isCurrent && 'border-2 border-border-subtle bg-card'
                )}
              >
                {isDone ? <Check className="size-3.5" strokeWidth={3} /> : null}
                {isCurrent ? <span className="size-2.5 rounded-full bg-brand-primary" /> : null}
              </span>
              <span
                aria-hidden="true"
                className={cn(
                  'h-[3px] flex-1',
                  index === lastIndex
                    ? 'invisible'
                    : rightColoured
                      ? 'bg-fsm-delivered-fg'
                      : 'bg-border-subtle'
                )}
              />
            </div>
            <span
              className={cn(
                'text-center',
                isCurrent
                  ? 'text-body-strong text-brand-pressed'
                  : isDone
                    ? 'text-caption text-primary'
                    : 'text-caption text-muted'
              )}
            >
              {step.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

export default StatusProgress;
