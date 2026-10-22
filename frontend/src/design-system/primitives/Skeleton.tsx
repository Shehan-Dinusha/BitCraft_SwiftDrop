import { cn } from '@/utils/cn';

export type SkeletonShape = 'text' | 'block' | 'circle';

export interface SkeletonProps {
  className?: string;
  shape?: SkeletonShape;
  /** Renders stacked text bars; the last bar is shortened. */
  lines?: number;
}

const shapeClasses: Record<SkeletonShape, string> = {
  text: 'h-4 w-full rounded',
  block: 'rounded-lg',
  circle: 'rounded-full'
};

const barClass = 'block animate-pulse bg-border-subtle motion-reduce:animate-none';

export function Skeleton({ className, shape = 'text', lines = 1 }: SkeletonProps) {
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
