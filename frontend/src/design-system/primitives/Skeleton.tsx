import { cn } from '@/utils/cn';

export type SkeletonShape = 'text' | 'block' | 'circle';
/** Figma Skeleton variants: a text block, a table row, or a card. */
export type SkeletonPreset = 'text-block' | 'table-row' | 'card';

export interface SkeletonProps {
  className?: string;
  shape?: SkeletonShape;
  /** Renders stacked text bars; the last bar is shortened. */
  lines?: number;
  /** Renders one of the composed Figma placeholders instead of a single bone. */
  preset?: SkeletonPreset;
}

const shapeClasses: Record<SkeletonShape, string> = {
  text: 'h-3 w-full rounded',
  block: 'rounded-lg',
  circle: 'rounded-full'
};

const barClass = 'block animate-pulse bg-elevated motion-reduce:animate-none';

function Bone({ className }: { className: string }) {
  return <span className={cn(barClass, 'shrink-0 rounded', className)} />;
}

function PresetSkeleton({ preset, className }: { preset: SkeletonPreset; className?: string }) {
  if (preset === 'card') {
    return (
      <span
        aria-hidden="true"
        className={cn(
          'flex w-[280px] max-w-full flex-col items-start gap-3 rounded-xl border border-border-subtle bg-card p-4',
          className
        )}
      >
        <Bone className="h-3.5 w-[120px]" />
        <Bone className="h-2.5 w-full" />
        <Bone className="h-2.5 w-[200px] max-w-full" />
        <span className="flex gap-2">
          <Bone className="h-7 w-20 rounded-lg" />
          <Bone className="h-7 w-20 rounded-lg" />
        </span>
      </span>
    );
  }

  if (preset === 'table-row') {
    return (
      <span
        aria-hidden="true"
        className={cn(
          'flex w-full items-center gap-6 border-b border-border-subtle px-4 py-3.5',
          className
        )}
      >
        <Bone className="h-3 w-[110px]" />
        <Bone className="h-5 w-[90px] rounded-full" />
        <Bone className="h-3 w-[200px]" />
        <Bone className="h-3 w-20" />
      </span>
    );
  }

  return (
    <span aria-hidden="true" className={cn('flex w-[280px] max-w-full flex-col gap-2', className)}>
      <Bone className="h-3 w-full" />
      <Bone className="h-3 w-[240px] max-w-full" />
      <Bone className="h-3 w-40" />
    </span>
  );
}

export function Skeleton({ className, shape = 'text', lines = 1, preset }: SkeletonProps) {
  if (preset) return <PresetSkeleton preset={preset} className={className} />;

  const resolved = shapeClasses[shape];

  if (lines > 1) {
    return (
      <span className="flex flex-col gap-2" aria-hidden="true">
        {Array.from({ length: lines }, (_, index) => (
          <span
            key={index}
            className={cn(barClass, resolved, index === lines - 1 && 'w-4/5', className)}
          />
        ))}
      </span>
    );
  }

  return <span aria-hidden="true" className={cn(barClass, resolved, className)} />;
}

export default Skeleton;
