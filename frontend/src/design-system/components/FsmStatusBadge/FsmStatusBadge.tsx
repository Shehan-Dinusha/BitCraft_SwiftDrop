import { cn } from '@/utils/cn';
import type { DeliveryState } from '@/types/contracts';
import { deliveryStateColors } from '@/design-system/tokens/colors';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import type { DensityMode } from '@/design-system/tokens/density';

export type FsmStatusBadgeSize = 'sm' | 'md' | 'lg';
export type FsmStatusBadgeTone = 'solid' | 'muted';

export interface FsmStatusBadgeProps {
  /** Backend delivery state. Drives every color from the FSM tokens. */
  state: DeliveryState;
  /** Explicit size. Defaults to the persona density. */
  size?: FsmStatusBadgeSize;
  /** `muted` renders the state quietly, e.g. for steps the parcel has not reached. */
  tone?: FsmStatusBadgeTone;
  /** Overrides the derived label (defaults to the state name, e.g. `PICKED UP`). */
  label?: string;
  density?: DensityMode;
  className?: string;
}

const sizeClasses: Record<FsmStatusBadgeSize, { box: string; dot: string }> = {
  sm: { box: 'gap-1 px-2 py-0.5 text-[10px]', dot: 'size-1' },
  md: { box: 'gap-1.5 px-2.5 py-1 text-xs', dot: 'size-1.5' },
  lg: { box: 'gap-2 px-3 py-1.5 text-sm', dot: 'size-2' }
};

const densitySizes: Record<DensityMode, FsmStatusBadgeSize> = {
  touch: 'lg',
  comfortable: 'md',
  compact: 'sm'
};

export function FsmStatusBadge({
  state,
  size,
  tone = 'solid',
  label,
  density,
  className
}: FsmStatusBadgeProps) {
  const contextDensity = useDensityMode();
  const tokens = deliveryStateColors[state];
  const resolvedSize = size ?? densitySizes[density ?? contextDensity];
  const sizing = sizeClasses[resolvedSize];
  const isMuted = tone === 'muted';

  return (
    <span
      data-state={state}
      data-tone={tone}
      className={cn(
        'inline-flex max-w-full items-center rounded-full border font-semibold uppercase tracking-wide',
        sizing.box,
        isMuted
          ? 'border-border-subtle bg-card text-muted'
          : cn(tokens.background, tokens.foreground, tokens.border),
        className
      )}
    >
      <span
        className={cn(
          'shrink-0 rounded-full',
          isMuted ? 'bg-disabled-fg' : 'bg-current',
          sizing.dot
        )}
        aria-hidden="true"
      />
      <span className="truncate">{label ?? state.replace(/_/g, ' ')}</span>
    </span>
  );
}

export default FsmStatusBadge;
