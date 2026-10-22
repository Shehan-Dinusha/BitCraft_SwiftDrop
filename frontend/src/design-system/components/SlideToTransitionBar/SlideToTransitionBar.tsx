import { KeyboardEvent, PointerEvent, ReactNode, useEffect, useRef, useState } from 'react';
import { ArrowRight, Check } from 'lucide-react';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export type SlideToTransitionBarState = 'idle' | 'dragging' | 'armed' | 'committed' | 'disabled';

export interface SlideToTransitionBarProps {
  /** Instruction shown at rest, e.g. "Slide to pick up". */
  label: string;
  /** Shown once the thumb passes the preview threshold. */
  confirmLabel?: string;
  /** Fires when the thumb is released past the commit threshold, or on Enter/Space. */
  onConfirm: () => void;
  disabled?: boolean;
  /** Forces a visual state for catalog and review screens. */
  state?: SlideToTransitionBarState;
  density?: DensityMode;
  icon?: ReactNode;
  className?: string;
}

const PREVIEW_THRESHOLD = 0.5;
const COMMIT_THRESHOLD = 0.85;

export function SlideToTransitionBar({
  label,
  confirmLabel = 'Release to confirm',
  onConfirm,
  disabled = false,
  state,
  density,
  icon,
  className
}: SlideToTransitionBarProps) {
  const contextDensity = useDensityMode();
  const recipe = densityRecipes[density ?? contextDensity];
  const trackHeight = recipe.slideTrack;
  const thumbSize = trackHeight - 8;
  const inset = 4;

  const trackRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef({ active: false, startX: 0, startOffset: 0 });
  const resetTimer = useRef<number | null>(null);

  const [trackWidth, setTrackWidth] = useState(0);
  const [offset, setOffset] = useState(0);
  const [dragging, setDragging] = useState(false);

  const maxOffset = Math.max(trackWidth - thumbSize - inset * 2, 0);

  useEffect(() => {
    const node = trackRef.current;
    if (!node) return;
    const update = () => setTrackWidth(node.clientWidth);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  useEffect(
    () => () => {
      if (resetTimer.current !== null) window.clearTimeout(resetTimer.current);
    },
    []
  );

  const forcedProgress =
    state === 'committed' ? 1 : state === 'armed' ? 0.8 : state === 'dragging' ? 0.4 : null;
  const progress = forcedProgress ?? (maxOffset > 0 ? offset / maxOffset : 0);
  const armed = progress >= PREVIEW_THRESHOLD;

  const resolvedState: SlideToTransitionBarState = disabled
    ? 'disabled'
    : (state ?? (armed ? 'armed' : dragging ? 'dragging' : 'idle'));

  const locked = disabled || Boolean(state);

  const commit = () => {
    setOffset(maxOffset);
    onConfirm();
    resetTimer.current = window.setTimeout(() => setOffset(0), 240);
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (locked || maxOffset === 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = { active: true, startX: event.clientX, startOffset: offset };
    setDragging(true);
  };

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!dragRef.current.active) return;
    const next = Math.min(
      Math.max(dragRef.current.startOffset + (event.clientX - dragRef.current.startX), 0),
      maxOffset
    );
    setOffset(next);
  };

  const endDrag = (commitIfArmed: boolean) => {
    if (!dragRef.current.active) return;
    dragRef.current.active = false;
    setDragging(false);
    if (commitIfArmed && maxOffset > 0 && offset / maxOffset >= COMMIT_THRESHOLD) commit();
    else setOffset(0);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (locked) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      commit();
    }
  };

  return (
    <div
      ref={trackRef}
      role="button"
      tabIndex={locked ? -1 : 0}
      aria-label={label}
      aria-disabled={disabled || undefined}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={() => endDrag(true)}
      onPointerCancel={() => endDrag(false)}
      onKeyDown={onKeyDown}
      className={cn(
        'relative flex w-full select-none items-center overflow-hidden rounded-full border transition-colors touch-none',
        'border-border-subtle bg-card',
        armed && !disabled ? 'border-brand-primary' : '',
        resolvedState === 'committed' ? 'border-status-success' : '',
        resolvedState === 'disabled'
          ? 'cursor-not-allowed border-border-subtle bg-disabled-bg'
          : '',
        !locked && resolvedState !== 'armed' ? 'cursor-grab' : '',
        dragging ? 'cursor-grabbing' : '',
        className
      )}
      style={{ height: trackHeight }}
    >
      <span
        aria-hidden="true"
        className={cn(
          'pointer-events-none absolute inset-y-0 left-0 rounded-full',
          resolvedState === 'disabled' ? 'bg-canvas' : 'bg-brand-subtle',
          dragging || forcedProgress !== null
            ? ''
            : 'transition-[width] duration-200 motion-reduce:transition-none'
        )}
        style={{ width: thumbSize + inset * 2 + progress * maxOffset }}
      />

      <span
        aria-hidden="true"
        className={cn(
          'pointer-events-none absolute inset-0 flex items-center justify-center px-2 font-semibold',
          recipe.label,
          resolvedState === 'disabled'
            ? 'text-disabled-fg'
            : armed || resolvedState === 'committed'
              ? 'text-brand-primary'
              : 'text-secondary'
        )}
      >
        <span className="truncate">
          {resolvedState === 'committed' ? 'Confirmed' : armed ? confirmLabel : label}
        </span>
      </span>

      <span
        className={cn(
          'relative z-10 grid shrink-0 place-items-center rounded-full',
          resolvedState === 'committed'
            ? 'bg-status-success text-brand-on'
            : 'bg-brand-primary text-brand-on',
          resolvedState === 'disabled'
            ? 'border border-border-subtle bg-disabled-bg text-disabled-fg'
            : '',
          dragging || forcedProgress !== null
            ? ''
            : 'transition-transform duration-200 motion-reduce:transition-none'
        )}
        style={{
          width: thumbSize,
          height: thumbSize,
          marginLeft: inset,
          transform: `translateX(${progress * maxOffset}px)`
        }}
      >
        {resolvedState === 'committed' ? (
          <Check size={thumbSize / 2} aria-hidden="true" />
        ) : (
          (icon ?? <ArrowRight size={thumbSize / 2} aria-hidden="true" />)
        )}
      </span>
    </div>
  );
}

export default SlideToTransitionBar;
