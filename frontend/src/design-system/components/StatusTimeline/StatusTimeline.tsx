import { cn } from '@/utils/cn';
import { TimelineStep, type TimelineStepProps } from './TimelineStep';

export type StatusTimelineEntry = Omit<TimelineStepProps, 'isLast' | 'className'> & {
  key?: string;
};

export interface StatusTimelineProps {
  steps: StatusTimelineEntry[];
  /** Card heading. Omit it (and set `bare`) to render only the list. */
  title?: string;
  /** Renders the list without the surrounding card. */
  bare?: boolean;
  className?: string;
}

/** Figma StatusTimeline: the tracking history card, a vertical list of `TimelineStep`s. */
export function StatusTimeline({
  steps,
  title = 'Tracking history',
  bare = false,
  className
}: StatusTimelineProps) {
  const list = (
    <ol aria-label={title}>
      {steps.map(({ key, ...step }, index) => (
        <TimelineStep
          key={key ?? `${step.title}-${index}`}
          {...step}
          isLast={index === steps.length - 1}
        />
      ))}
    </ol>
  );

  if (bare) return <div className={className}>{list}</div>;

  return (
    <section
      className={cn('rounded-xl border border-border-subtle bg-card p-6', className)}
      aria-label={title}
    >
      <h3 className="pb-4 text-h3 text-primary">{title}</h3>
      {list}
    </section>
  );
}

export default StatusTimeline;
