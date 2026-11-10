import { Package, Timer, Zap } from 'lucide-react';
import { cn } from '@/utils/cn';

export type Speed = 'standard' | 'express' | 'same-day';

export interface SpeedTagProps {
  speed: Speed;
  className?: string;
}

const speeds: Record<Speed, { label: string; className: string; Icon: typeof Zap }> = {
  standard: {
    label: 'STANDARD',
    className: 'border border-border-subtle bg-elevated text-secondary',
    Icon: Package
  },
  express: { label: 'EXPRESS', className: 'bg-brand-primary text-brand-on', Icon: Zap },
  'same-day': { label: 'SAME DAY', className: 'bg-brand-pressed text-brand-on', Icon: Timer }
};

/** SpeedTag: Standard (neutral), Express (brand fill) or Same day (darker brand fill). Always keeps the text label. */
export function SpeedTag({ speed, className }: SpeedTagProps) {
  const { label, className: tone, Icon } = speeds[speed];
  return (
    <span
      data-speed={speed}
      className={cn(
        'inline-flex items-center gap-1 rounded-md px-2 py-[3px] text-[11px] font-semibold leading-4 tracking-[0.04em]',
        tone,
        className
      )}
    >
      <Icon aria-hidden="true" className="size-3" />
      {label}
    </span>
  );
}

export default SpeedTag;
