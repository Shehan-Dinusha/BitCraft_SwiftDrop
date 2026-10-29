import { cn } from '@/utils/cn';
import type { DeliveryState, PublicOrderStatus } from '@/types/contracts';
import { publicStatusColors, toPublicStatus } from '@/design-system/tokens/colors';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import type { DensityMode } from '@/design-system/tokens/density';

/** `sm` and `md` are the Figma sizes S and M. `lg` is a deprecated alias of `md`. */
export type FsmStatusBadgeSize = 'sm' | 'md' | 'lg';
export type FsmStatusBadgeTone = 'solid' | 'muted';

export interface FsmStatusBadgeProps {
  /** Public order status (or the older `ASSIGNED` delivery state). Drives every colour from the FSM tokens. */
  state: DeliveryState | PublicOrderStatus;
  /** Explicit size. Defaults to the persona density. */
  size?: FsmStatusBadgeSize;
  /** `muted` renders the state quietly, e.g. for steps the parcel has not reached. */
  tone?: FsmStatusBadgeTone;
  /** Courier update queued offline: dashed border, hollow dot and a "SYNCING" suffix. */
  sync?: boolean;
  /** Overrides the derived label (defaults to the state name, e.g. `PICKED UP`). */
  label?: string;
  density?: DensityMode;
  className?: string;
}

// Figma FSMStatusBadge: M = 10/4 padding, 6 gap, 11px label; S = 6/2 padding, 4 gap, 10px label.
const sizeClasses: Record<FsmStatusBadgeSize, { box: string; dot: string }> = {
  sm: { box: 'gap-1 px-1.5 py-0.5 text-[10px] tracking-[0.04em]', dot: 'size-[5px]' },
  md: { box: 'gap-1.5 px-2.5 py-1 text-[11px] tracking-[0.04em]', dot: 'size-1.5' },
  lg: { box: 'gap-1.5 px-2.5 py-1 text-[11px] tracking-[0.04em]', dot: 'size-1.5' }
};

const densitySizes: Record<DensityMode, FsmStatusBadgeSize> = {
  kinetic: 'md',
  touch: 'md',
  comfortable: 'md',
  compact: 'sm'
};

export function FsmStatusBadge({
  state,
  size,
  tone = 'solid',
  sync = false,
  label,
  density,
  className
}: FsmStatusBadgeProps) {
  const contextDensity = useDensityMode();
  const status = toPublicStatus(state);
  const tokens = publicStatusColors[status];
  const resolvedSize = size ?? densitySizes[density ?? contextDensity];
  const sizing = sizeClasses[resolvedSize];
  const isMuted = tone === 'muted';
  const text = label ?? status.replace(/_/g, ' ');

  return (
    <span
      data-state={status}
      data-tone={tone}
      data-sync={sync ? 'true' : undefined}
      className={cn(
        'inline-flex max-w-full items-center rounded-full border font-bold uppercase',
        sizing.box,
        sync ? 'border-dashed opacity-85' : 'border-solid',
        isMuted
          ? 'border-border-subtle bg-card text-muted'
          : cn(tokens.background, tokens.foreground, tokens.border),
        className
      )}
    >
      <span
        className={cn(
          'shrink-0 rounded-full',
          sync ? 'border border-current bg-transparent' : isMuted ? 'bg-disabled-fg' : 'bg-current',
          sizing.dot
        )}
        aria-hidden="true"
      />
      <span className="truncate">{sync ? `${text} · SYNCING` : text}</span>
    </span>
  );
}

export default FsmStatusBadge;
