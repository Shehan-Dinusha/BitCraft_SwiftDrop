import type { DeliveryState } from '@/types/contracts';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import type { DensityMode } from '@/design-system/tokens/density';
import {
  FsmStatusBadge,
  type FsmStatusBadgeSize
} from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';

const steps: DeliveryState[] = ['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'];

export interface DeliveryProgressStepperProps {
  current: DeliveryState;
  size?: FsmStatusBadgeSize;
  density?: DensityMode;
  className?: string;
}

const densitySizes: Record<DensityMode, FsmStatusBadgeSize> = {
  kinetic: 'lg',
  touch: 'lg',
  comfortable: 'md',
  compact: 'sm'
};

export function DeliveryProgressStepper({
  current,
  size,
  density,
  className
}: DeliveryProgressStepperProps) {
  const contextDensity = useDensityMode();
  const resolvedSize = size ?? densitySizes[density ?? contextDensity];

  const activeIndex = steps.indexOf(current);
  const isTerminal = activeIndex === -1;
  const items: DeliveryState[] = isTerminal ? [...steps, current] : steps;
  const currentIndex = isTerminal ? items.length - 1 : activeIndex;

  return (
    <ol
      aria-label="Delivery progress"
      className={cn('flex items-center overflow-x-auto', className)}
    >
      {items.map((state, index) => {
        const isCurrent = index === currentIndex;
        const isComplete = !isTerminal && index < currentIndex;
        const isUpcoming = index !== currentIndex && !isComplete;
        const connectorReached = !isTerminal && index <= currentIndex;

        return (
          <li
            key={state}
            aria-current={isCurrent ? 'step' : undefined}
            className="flex shrink-0 items-center"
          >
            {index > 0 ? (
              <span
                aria-hidden="true"
                className={cn(
                  'mx-2 h-0.5 w-6 shrink-0 rounded-full sm:w-10',
                  connectorReached ? 'bg-status-success' : 'bg-border-subtle'
                )}
              />
            ) : null}
            <FsmStatusBadge
              state={state}
              size={resolvedSize}
              tone={isUpcoming ? 'muted' : 'solid'}
            />
          </li>
        );
      })}
    </ol>
  );
}

export default DeliveryProgressStepper;
