import type { DeliveryState } from '@/types/contracts';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import type { DensityMode } from '@/design-system/tokens/density';
import {
  FsmStatusBadge,
  type FsmStatusBadgeSize
} from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';

const directSteps: DeliveryState[] = ['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'];
/** A parcel going to another district passes both districts' hubs. */
const hubSteps: DeliveryState[] = [
  'CREATED',
  'ASSIGNED',
  'PICKED_UP',
  'AT_ORIGIN_HUB',
  'IN_TRANSIT',
  'AT_DESTINATION_HUB',
  'OUT_FOR_DELIVERY',
  'DELIVERED'
];

/** After a failed attempt: back to the hub, then back to the sender. */
const returnSteps: DeliveryState[] = [
  'ATTEMPT_FAILED',
  'HELD_AT_HUB',
  'RETURN_IN_TRANSIT',
  'RETURN_AT_HUB',
  'OUT_FOR_RETURN',
  'RETURNED'
];
/** Several of these steps share one public status, so they need their own words. */
const returnLabels: Partial<Record<DeliveryState, string>> = {
  ATTEMPT_FAILED: 'Not delivered',
  HELD_AT_HUB: 'At the hub',
  RETURN_IN_TRANSIT: 'On the way back',
  RETURN_AT_HUB: "At the sender's hub",
  OUT_FOR_RETURN: 'To the sender',
  RETURNED: 'Returned'
};

export interface DeliveryProgressStepperProps {
  current: DeliveryState;
  /** The parcel goes to another district (through both hubs). A hub state implies it. */
  viaHubs?: boolean;
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
  viaHubs = false,
  size,
  density,
  className
}: DeliveryProgressStepperProps) {
  const contextDensity = useDensityMode();
  const resolvedSize = size ?? densitySizes[density ?? contextDensity];

  const steps = returnSteps.includes(current)
    ? returnSteps.filter((step) => step !== 'RETURN_IN_TRANSIT' || viaHubs || step === current)
    : viaHubs || current === 'AT_ORIGIN_HUB' || current === 'AT_DESTINATION_HUB'
      ? hubSteps
      : directSteps;
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
              label={returnLabels[state]}
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
