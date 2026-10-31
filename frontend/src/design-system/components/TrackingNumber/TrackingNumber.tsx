import { useEffect, useRef, useState } from 'react';
import { Check, Copy } from 'lucide-react';
import { cn } from '@/utils/cn';

export type TrackingNumberSize = 'md' | 'lg';

export interface TrackingNumberProps {
  /** e.g. `SD-7K4M-9QX2`. */
  value: string;
  /** Figma sizes: `md` = M, `lg` = L. */
  size?: TrackingNumberSize;
  /** Shows the copy button. */
  copyable?: boolean;
  className?: string;
}

const sizeClasses: Record<TrackingNumberSize, { box: string; text: string; icon: string }> = {
  md: { box: 'gap-2 px-2.5 py-[5px]', text: 'font-mono text-mono', icon: 'size-3.5' },
  lg: { box: 'gap-3 px-4 py-2.5', text: 'font-mono text-mono-l', icon: 'size-[18px]' }
};

/** Figma TrackingNumber: mono ID in a pill with a copy affordance. */
export function TrackingNumber({
  value,
  size = 'md',
  copyable = true,
  className
}: TrackingNumberProps) {
  const [copied, setCopied] = useState(false);
  const timer = useRef<number | undefined>(undefined);
  const sizing = sizeClasses[size];

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(() => setCopied(false), 1500);
    } catch {
      /* Clipboard access can be blocked; the number stays selectable. */
    }
  };

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-lg border border-border-subtle bg-elevated',
        sizing.box,
        className
      )}
    >
      <span className={cn('select-all tabular-nums text-primary', sizing.text)}>{value}</span>
      {copyable ? (
        <button
          type="button"
          onClick={() => void copy()}
          aria-label={copied ? 'Tracking number copied' : 'Copy tracking number'}
          className="inline-flex text-muted transition-colors hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
        >
          {copied ? (
            <Check aria-hidden="true" className={cn(sizing.icon, 'text-status-success')} />
          ) : (
            <Copy aria-hidden="true" className={sizing.icon} />
          )}
        </button>
      ) : null}
    </span>
  );
}

export default TrackingNumber;
