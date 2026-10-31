import { cn } from '@/utils/cn';

export type LiveIndicatorState = 'live' | 'reconnecting' | 'offline';

export interface LiveIndicatorProps {
  state: LiveIndicatorState;
  /** Changes waiting to sync, shown while offline (e.g. courier updates queued on the phone). */
  queued?: number;
  className?: string;
}

const tones: Record<LiveIndicatorState, string> = {
  live: 'border-fsm-delivered-border bg-fsm-delivered-bg text-fsm-delivered-fg',
  reconnecting: 'border-fsm-in-transit-border bg-fsm-in-transit-bg text-fsm-in-transit-fg',
  offline: 'border-fsm-cancelled-border bg-fsm-cancelled-bg text-fsm-cancelled-fg'
};

function labelFor(state: LiveIndicatorState, queued: number) {
  if (state === 'live') return 'Live';
  if (state === 'reconnecting') return 'Reconnecting…';
  return queued > 0
    ? `Offline · ${queued} ${queued === 1 ? 'change' : 'changes'} queued`
    : 'Offline';
}

/** Figma LiveIndicator: real-time connection pill (Live / Reconnecting / Offline). Announced politely. */
export function LiveIndicator({ state, queued = 0, className }: LiveIndicatorProps) {
  return (
    <span
      role="status"
      data-state={state}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-label',
        tones[state],
        className
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'size-2 rounded-full bg-current',
          state !== 'offline' && 'animate-pulse motion-reduce:animate-none'
        )}
      />
      {labelFor(state, queued)}
    </span>
  );
}

export default LiveIndicator;
