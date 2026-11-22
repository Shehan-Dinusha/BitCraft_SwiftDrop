import { cn } from '@/utils/cn';
import { initials } from './initials';

export interface AvatarProps {
  name: string;
  /** Profile picture URL; the person's initials are shown when absent. */
  src?: string | null;
  /** `sm` 36px (navigation), `lg` 80px (profile page). */
  size?: 'sm' | 'lg';
  className?: string;
}

const boxSize = { sm: 'size-9', lg: 'size-20' };
const textSize = { sm: 'text-label', lg: 'text-h2' };

/** Round profile picture or initials. The ring keeps the circle visible around a white photo. */
export function Avatar({ name, src, size = 'sm', className }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center overflow-hidden rounded-full border-2 border-card bg-brand-primary text-brand-on ring-2 ring-border-focus',
        boxSize[size],
        className
      )}
    >
      {src ? (
        <img src={src} alt="" className="size-full object-cover" />
      ) : (
        <span className={cn('font-semibold', textSize[size])}>{initials(name)}</span>
      )}
    </span>
  );
}

export default Avatar;
