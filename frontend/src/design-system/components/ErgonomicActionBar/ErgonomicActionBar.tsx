import type { DeliveryState } from '@/types/contracts';
import { cn } from '@/utils/cn';
import { Button } from '@/design-system/primitives/Button';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export interface ErgonomicActionBarProps {
  /** Allowed next states from `useFsmTransition`; never pass the full FSM. */
  next: DeliveryState[];
  onTransition: (state: DeliveryState) => void;
  /** State currently being submitted; blocks repeat taps and shows a spinner. */
  pendingState?: DeliveryState | null;
  label?: string;
  sticky?: boolean;
  density?: DensityMode;
  className?: string;
}

const actionLabels: Partial<Record<DeliveryState, string>> = {
  ASSIGNED: 'Accept job',
  PICKED_UP: 'Confirm pickup',
  IN_TRANSIT: 'Start trip',
  DELIVERED: 'Complete delivery',
  FAILED: 'Report failure',
  CANCELLED: 'Cancel delivery'
};

const destructiveStates: DeliveryState[] = ['FAILED', 'CANCELLED'];

const humanize = (state: DeliveryState) => state.replace(/_/g, ' ').toLowerCase();

export function ErgonomicActionBar({
  next,
  onTransition,
  pendingState = null,
  label = 'Delivery actions',
  sticky = false,
  density,
  className
}: ErgonomicActionBarProps) {
  const contextDensity = useDensityMode();
  const resolvedDensity = density ?? contextDensity;
  const recipe = densityRecipes[resolvedDensity];
  const visible = next.slice(0, recipe.maxActions);
  const isPending = pendingState !== null;

  if (visible.length === 0) return null;

  return (
    <div
      role="group"
      aria-label={label}
      className={cn(
        'flex rounded-xl border border-border-subtle bg-card',
        recipe.gap,
        recipe.surfacePadding,
        sticky && 'sticky bottom-0 z-10 shadow-ambient',
        className
      )}
    >
      {visible.map((state, index) => {
        const destructive = destructiveStates.includes(state);

        return (
          <Button
            key={state}
            variant={destructive ? 'destructive' : index === 0 ? 'primary' : 'secondary'}
            density={resolvedDensity}
            className="flex-1"
            onClick={() => onTransition(state)}
            loading={pendingState === state}
            disabled={isPending && pendingState !== state}
          >
            {actionLabels[state] ?? humanize(state)}
          </Button>
        );
      })}
    </div>
  );
}

export default ErgonomicActionBar;
