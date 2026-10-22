import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export type StatusBannerTone = 'success' | 'warning' | 'danger' | 'info';

export interface StatusBannerProps {
  tone: StatusBannerTone;
  title: string;
  children?: ReactNode;
  /** Rendered under the message, e.g. a retry button. */
  action?: ReactNode;
  onDismiss?: () => void;
  dismissLabel?: string;
  density?: DensityMode;
  className?: string;
}

const toneSurfaces: Record<StatusBannerTone, string> = {
  success: 'border-status-success-border bg-status-success-bg text-status-success-fg',
  warning: 'border-status-warning-border bg-status-warning-bg text-status-warning-fg',
  danger: 'border-status-danger-border bg-status-danger-bg text-status-danger-fg',
  info: 'border-status-info-border bg-status-info-bg text-status-info-fg'
};

const toneIcons: Record<StatusBannerTone, typeof Info> = {
  success: CheckCircle2,
  warning: TriangleAlert,
  danger: AlertCircle,
  info: Info
};

export function StatusBanner({
  tone,
  title,
  children,
  action,
  onDismiss,
  dismissLabel = 'Dismiss',
  density,
  className
}: StatusBannerProps) {
  const contextDensity = useDensityMode();
  const recipe = densityRecipes[density ?? contextDensity];
  const Icon = toneIcons[tone];

  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      data-tone={tone}
      className={cn(
        'flex items-start rounded-lg border',
        recipe.surfaceSection,
        recipe.gap,
        recipe.caption,
        toneSurfaces[tone],
        className
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'mt-0.5 inline-flex shrink-0 items-center justify-center',
          recipe.icon,
          '[&_svg]:size-full'
        )}
      >
        <Icon />
      </span>
      <div className="min-w-0 flex-1">
        <p className={cn('font-semibold', recipe.label)}>{title}</p>
        {children ? <div className="mt-1 opacity-90">{children}</div> : null}
        {action ? <div className="mt-3 flex flex-wrap items-center gap-2">{action}</div> : null}
      </div>
      {onDismiss ? (
        <button
          type="button"
          onClick={onDismiss}
          aria-label={dismissLabel}
          className={cn(
            'inline-flex shrink-0 items-center justify-center rounded-md opacity-70 transition-opacity hover:opacity-100',
            'focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus',
            recipe.target
          )}
        >
          <X aria-hidden="true" className={recipe.icon} />
        </button>
      ) : null}
    </div>
  );
}

export default StatusBanner;
