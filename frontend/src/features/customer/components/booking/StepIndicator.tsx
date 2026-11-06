import { Check } from 'lucide-react';
import { cn } from '@/utils/cn';
import type { BookingStepId } from './validation';

export interface BookingStepDef {
  id: BookingStepId;
  label: string;
}

interface StepIndicatorProps {
  steps: BookingStepDef[];
  current: BookingStepId;
  /** Steps the customer has already passed; they can jump back to these. */
  completed: Set<BookingStepId>;
  onSelect: (step: BookingStepId) => void;
}

/** Wizard progress: numbered steps, completed ones are links back. */
export function StepIndicator({ steps, current, completed, onSelect }: StepIndicatorProps) {
  const currentIndex = steps.findIndex((step) => step.id === current);

  return (
    <nav aria-label="Booking steps">
      <p className="text-caption text-secondary sm:hidden">
        Step {currentIndex + 1} of {steps.length} ·{' '}
        <span className="font-semibold text-primary">{steps[currentIndex]?.label}</span>
      </p>
      <ol className="hidden items-center gap-2 sm:flex">
        {steps.map((step, index) => {
          const isCurrent = step.id === current;
          const isDone = completed.has(step.id) && !isCurrent;
          const marker = (
            <span
              aria-hidden="true"
              className={cn(
                'grid size-7 shrink-0 place-items-center rounded-full text-label font-semibold',
                isCurrent && 'bg-brand-primary text-brand-on',
                isDone && 'bg-status-success-bg text-status-success-fg',
                !isCurrent && !isDone && 'border border-border-subtle bg-card text-muted'
              )}
            >
              {isDone ? <Check className="size-4" /> : index + 1}
            </span>
          );
          const label = (
            <span
              className={cn(
                'text-label',
                isCurrent ? 'font-semibold text-primary' : 'text-secondary'
              )}
            >
              {step.label}
            </span>
          );

          return (
            <li key={step.id} className="flex flex-1 items-center gap-2 last:flex-none">
              {isDone ? (
                <button
                  type="button"
                  onClick={() => onSelect(step.id)}
                  className="flex items-center gap-2 rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
                >
                  {marker}
                  {label}
                  <span className="sr-only">(completed, edit)</span>
                </button>
              ) : (
                <span
                  className="flex items-center gap-2"
                  aria-current={isCurrent ? 'step' : undefined}
                >
                  {marker}
                  {label}
                </span>
              )}
              {index < steps.length - 1 ? (
                <span
                  aria-hidden="true"
                  className={cn(
                    'h-px flex-1',
                    index < currentIndex ? 'bg-status-success' : 'bg-border-subtle'
                  )}
                />
              ) : null}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
