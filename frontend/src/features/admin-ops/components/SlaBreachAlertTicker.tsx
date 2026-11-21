import { AlertTriangle, CheckCircle2, ShieldAlert } from 'lucide-react';
import { slaColors } from '@/design-system/tokens/colors';

interface SlaBreachAlertTickerProps {
  breachCount?: number | null;
  onTimeRate?: number | null;
  criticalDistricts?: string[];
}

export function SlaBreachAlertTicker({
  breachCount,
  onTimeRate,
  criticalDistricts = []
}: SlaBreachAlertTickerProps) {
  if (breachCount == null || onTimeRate == null) {
    return (
      <div
        role="status"
        className="flex items-center gap-2 rounded-lg border border-border-subtle bg-card px-4 py-3 text-sm text-secondary"
      >
        <AlertTriangle className="size-4 shrink-0" />
        SLA data is unavailable right now.
      </div>
    );
  }
  const isBreached = breachCount > 0;

  return (
    <div
      className={`flex flex-wrap items-center justify-between gap-3 rounded-lg border px-4 py-2.5 text-xs transition-all ${
        isBreached
          ? `${slaColors.critical.background} ${slaColors.critical.foreground} ${slaColors.critical.border}`
          : 'border-emerald-200 bg-emerald-50/80 text-emerald-800'
      }`}
    >
      <div className="flex items-center gap-2">
        {isBreached ? (
          <ShieldAlert className="h-4 w-4 shrink-0 animate-pulse text-rose-500" />
        ) : (
          <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" />
        )}
        <div className="flex flex-wrap items-center gap-1.5 font-medium">
          <span className="font-bold">
            {isBreached
              ? `${breachCount} Active SLA Breaches Detected`
              : 'All Island Deliveries Within SLA'}
          </span>
          <span className="text-slate-400">|</span>
          <span>
            System On-Time Adherence:{' '}
            <strong className="font-mono">{onTimeRate.toFixed(1)}%</strong>
          </span>
        </div>
      </div>

      {isBreached && criticalDistricts.length > 0 && (
        <div className="flex items-center gap-1.5 text-[11px]">
          <AlertTriangle className="h-3.5 w-3.5 text-amber-500" />
          <span>Hotspots:</span>
          {criticalDistricts.map((d) => (
            <span
              key={d}
              className="rounded bg-rose-200/80 px-1.5 py-0.5 font-mono font-bold text-rose-900"
            >
              {d}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
