import { cn } from '@/utils/cn';

/** How full a courier is. Turns amber from 80%. The label carries the numbers for screen readers. */
export function LoadBar({ used, limit, label }: { used: number; limit: number; label: string }) {
  const percent = limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 0;
  return (
    <div className="flex items-center gap-2">
      <div
        className="h-1.5 w-16 shrink-0 overflow-hidden rounded-full bg-elevated"
        aria-hidden="true"
      >
        <div
          className={cn('h-full', percent >= 80 ? 'bg-status-warning' : 'bg-status-success')}
          style={{ width: `${percent}%` }}
        />
      </div>
      <span className="whitespace-nowrap text-caption tabular-nums text-secondary">{label}</span>
    </div>
  );
}
