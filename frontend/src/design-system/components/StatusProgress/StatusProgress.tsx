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

type StepState = 'done' | 'current' | 'upcoming';

function stepStates(current: StatusProgressStatus): StepState[] {
  const currentIndex = STATUS_PROGRESS_STEPS.findIndex((step) => step.status === current);
  const lastIndex = STATUS_PROGRESS_STEPS.length - 1;
  return STATUS_PROGRESS_STEPS.map((_, index) =>
    index < currentIndex || (index === currentIndex && index === lastIndex)
      ? 'done'
      : index === currentIndex
        ? 'current'
        : 'upcoming'
  );
}

function Dot({ state }: { state: StepState }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex size-6 shrink-0 items-center justify-center rounded-full',
        state === 'done' && 'bg-fsm-delivered-fg text-brand-on',
        state === 'current' && 'border-2 border-brand-primary bg-brand-subtle',
        state === 'upcoming' && 'border-2 border-border-subtle bg-card'
      )}
    >
      {state === 'done' ? <Check className="size-3.5" strokeWidth={3} /> : null}
      {state === 'current' ? <span className="size-2.5 rounded-full bg-brand-primary" /> : null}
    </span>
  );
}

const labelClass = (state: StepState) =>
  state === 'current'
    ? 'text-body-strong text-brand-pressed'
    : state === 'done'
      ? 'text-caption text-primary'
      : 'text-caption text-muted';

/**
 * Figma StatusProgress: the five public steps with connectors. Customer parcel hero and mobile sheet.
 * Five labels side by side need about 500px, so below `sm` the steps stack vertically: every step is
 * named, done ones ticked and the ones still to come greyed, so the customer sees the whole journey.
 */
export function StatusProgress({ current, className }: StatusProgressProps) {
  const states = stepStates(current);
  const currentIndex = STATUS_PROGRESS_STEPS.findIndex((step) => step.status === current);
  const lastIndex = STATUS_PROGRESS_STEPS.length - 1;
  const label = `Parcel progress, step ${currentIndex + 1} of ${STATUS_PROGRESS_STEPS.length}`;

  return (
    <div className={cn('w-full', className)}>
      {/* Phone: one step per row, joined by a vertical line. */}
      <ol aria-label={label} className="flex flex-col sm:hidden">
        {STATUS_PROGRESS_STEPS.map((step, index) => {
          const state = states[index];
          return (
            <li
              key={step.status}
              data-state={state}
              aria-current={state === 'current' ? 'step' : undefined}
              className="flex gap-3"
            >
              <div className="flex flex-col items-center">
                <Dot state={state} />
                {index < lastIndex ? (
                  <span
                    aria-hidden="true"
                    className={cn(
                      'my-0.5 min-h-4 w-[3px] flex-1',
                      index < currentIndex ? 'bg-fsm-delivered-fg' : 'bg-border-subtle'
                    )}
                  />
                ) : null}
              </div>
              <span className={cn('pb-3 pt-0.5', labelClass(state))}>{step.label}</span>
            </li>
          );
        })}
      </ol>

      {/* Tablet and up: the horizontal bar. */}
      <ol aria-label={label} className="hidden w-full grid-cols-5 sm:grid">
        {STATUS_PROGRESS_STEPS.map((step, index) => {
          const state = states[index];
          return (
            <li
              key={step.status}
              data-state={state}
              aria-current={state === 'current' ? 'step' : undefined}
              className="flex flex-col items-center gap-2"
            >
              <div className="flex w-full items-center">
                <span
                  aria-hidden="true"
                  className={cn(
                    'h-[3px] flex-1',
                    index === 0
                      ? 'invisible'
                      : index <= currentIndex
                        ? 'bg-fsm-delivered-fg'
                        : 'bg-border-subtle'
                  )}
                />
                <Dot state={state} />
                <span
                  aria-hidden="true"
                  className={cn(
                    'h-[3px] flex-1',
                    index === lastIndex
                      ? 'invisible'
                      : index < currentIndex
                        ? 'bg-fsm-delivered-fg'
                        : 'bg-border-subtle'
                  )}
                />
              </div>
              <span className={cn('text-center', labelClass(state))}>{step.label}</span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

export default StatusProgress;
