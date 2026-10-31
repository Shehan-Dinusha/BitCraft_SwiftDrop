import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent as ReactPointerEvent
} from 'react';
import { Check, ChevronsRight, Fingerprint, Lock } from 'lucide-react';
import { cn } from '@/utils/cn';

export type SlideToConfirmState = 'idle' | 'dragging' | 'holding' | 'done' | 'locked';

export interface SlideToConfirmProps {
  /** Instruction at rest, e.g. `Slide to confirm pickup`. */
  label: string;
  /** Shown while dragging. */
  dragLabel?: string;
  /** Shown once confirmed, e.g. `Picked up · cash collected`. */
  doneLabel?: string;
  /** Shown when locked, e.g. `Scan the QR to unlock`. */
  lockedLabel?: string;
  onConfirm: () => void;
  /** Locks the control until a precondition is met (Figma: State = Locked). */
  locked?: boolean;
  /** Marks the action as done from outside (Figma: State = Done). */
  done?: boolean;
  /**
   * Keep the thumb at the end for this long before confirming (Figma: State = Holding).
   * `0` confirms as soon as the thumb is released past the threshold.
   */
  holdMs?: number;
  /** Forces a visual state for the catalog and design review. */
  state?: SlideToConfirmState;
  className?: string;
}

const COMMIT_THRESHOLD = 0.85;
const THUMB = 48;
const INSET = 6;

/** Figma SlideToConfirm: 60px track with a 48px thumb. State = Idle / Dragging / Holding / Done / Locked. */
export function SlideToConfirm({
  label,
  dragLabel = 'Release to confirm',
  doneLabel = 'Done',
  lockedLabel = 'Locked',
  onConfirm,
  locked = false,
  done = false,
  holdMs = 0,
  state,
  className
}: SlideToConfirmProps) {
  const trackRef = useRef<HTMLDivElement>(null);
  const drag = useRef({ active: false, startX: 0, startOffset: 0 });
  const holdTimer = useRef<number | undefined>(undefined);
  const tick = useRef<number | undefined>(undefined);

  const [trackWidth, setTrackWidth] = useState(0);
  const [offset, setOffset] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [holdLeft, setHoldLeft] = useState<number | null>(null);
  const [confirmed, setConfirmed] = useState(false);

  const maxOffset = Math.max(trackWidth - THUMB - INSET * 2, 0);

  useEffect(() => {
    const node = trackRef.current;
    if (!node) return undefined;
    const update = () => setTrackWidth(node.clientWidth);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const clearHold = () => {
    window.clearTimeout(holdTimer.current);
    window.clearInterval(tick.current);
    setHoldLeft(null);
  };

  useEffect(() => clearHold, []);

  const isDone = done || confirmed;
  const phase: SlideToConfirmState =
    state ??
    (locked
      ? 'locked'
      : isDone
        ? 'done'
        : holdLeft !== null
          ? 'holding'
          : dragging
            ? 'dragging'
            : 'idle');

  const forcedOffset = state === 'dragging' ? 0.45 : state === 'holding' ? 1 : null;
  const progress = forcedOffset ?? (maxOffset > 0 ? offset / maxOffset : 0);
  const interactive = !state && !locked && !isDone;

  const confirm = () => {
    clearHold();
    setConfirmed(true);
    setOffset(0);
    onConfirm();
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!interactive || maxOffset === 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { active: true, startX: event.clientX, startOffset: offset };
    setDragging(true);
  };

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!drag.current.active) return;
    const next = Math.min(
      Math.max(drag.current.startOffset + (event.clientX - drag.current.startX), 0),
      maxOffset
    );
    setOffset(next);
    const atEnd = maxOffset > 0 && next / maxOffset >= COMMIT_THRESHOLD;

    if (atEnd && holdMs > 0 && holdLeft === null) {
      const startedAt = Date.now();
      setHoldLeft(holdMs / 1000);
      tick.current = window.setInterval(() => {
        setHoldLeft(Math.max(0, (holdMs - (Date.now() - startedAt)) / 1000));
      }, 100);
      holdTimer.current = window.setTimeout(() => {
        drag.current.active = false;
        setDragging(false);
        confirm();
      }, holdMs);
    } else if (!atEnd && holdLeft !== null) {
      clearHold();
    }
  };

  const endDrag = () => {
    if (!drag.current.active) return;
    drag.current.active = false;
    setDragging(false);
    const atEnd = maxOffset > 0 && offset / maxOffset >= COMMIT_THRESHOLD;
    if (atEnd && holdMs === 0) confirm();
    else {
      clearHold();
      setOffset(0);
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!interactive) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      confirm();
    }
  };

  const text =
    phase === 'done'
      ? doneLabel
      : phase === 'locked'
        ? lockedLabel
        : phase === 'holding'
          ? `Keep holding… ${(holdLeft ?? holdMs / 1000).toFixed(1)} s`
          : phase === 'dragging'
            ? dragLabel
            : label;

  const Icon =
    phase === 'done'
      ? Check
      : phase === 'locked'
        ? Lock
        : phase === 'holding'
          ? Fingerprint
          : ChevronsRight;

  return (
    <div
      ref={trackRef}
      role="button"
      tabIndex={interactive ? 0 : -1}
      aria-label={label}
      aria-disabled={phase === 'locked' || undefined}
      data-state={phase}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onKeyDown={onKeyDown}
      style={{ height: THUMB + INSET * 2, padding: INSET }}
      className={cn(
        'relative flex w-full touch-none select-none items-center overflow-hidden rounded-full border transition-colors',
        phase === 'idle' && 'cursor-grab border-border-selected bg-brand-subtle',
        (phase === 'dragging' || phase === 'holding') && 'border-brand-primary bg-brand-primary',
        phase === 'done' && 'justify-start border-fsm-delivered-fg bg-fsm-delivered-fg',
        phase === 'locked' && 'cursor-not-allowed border-border-subtle bg-disabled-bg',
        interactive &&
          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
        className
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'pointer-events-none absolute inset-0 flex items-center justify-center px-16 text-label',
          phase === 'idle' && 'text-brand-pressed',
          (phase === 'dragging' || phase === 'holding' || phase === 'done') && 'text-brand-on',
          phase === 'locked' && 'text-disabled-fg'
        )}
      >
        <span className="truncate">{text}</span>
      </span>
      <span
        aria-hidden="true"
        className={cn(
          'relative z-10 flex shrink-0 items-center justify-center rounded-full',
          phase === 'idle' && 'bg-brand-primary text-brand-on',
          (phase === 'dragging' || phase === 'holding') && 'bg-card text-brand-primary',
          phase === 'done' && 'bg-card text-fsm-delivered-fg',
          phase === 'locked' && 'bg-card text-disabled-fg',
          !dragging && 'transition-transform duration-200 motion-reduce:transition-none'
        )}
        style={{
          width: THUMB,
          height: THUMB,
          transform: `translateX(${progress * maxOffset}px)`
        }}
      >
        <Icon className="size-[22px]" />
      </span>
    </div>
  );
}

export default SlideToConfirm;
