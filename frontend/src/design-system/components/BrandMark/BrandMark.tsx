import logoUrl from '@/assets/brand/swiftdrop-mark.png';
import { cn } from '@/utils/cn';

export interface BrandMarkProps {
  /** Rendered width and height in pixels. */
  size?: number;
  className?: string;
  /** Accessible name. Leave empty (the default) when visible "SwiftDrop" text sits beside the mark. */
  label?: string;
}

/** The SwiftDrop logo mark: a winged location pin on a transparent background. */
export function BrandMark({ size = 32, className, label = '' }: BrandMarkProps) {
  return (
    <img
      src={logoUrl}
      width={size}
      height={size}
      alt={label}
      aria-hidden={label ? undefined : true}
      draggable={false}
      className={cn('shrink-0 select-none object-contain', className)}
      style={{ width: size, height: size }}
    />
  );
}
