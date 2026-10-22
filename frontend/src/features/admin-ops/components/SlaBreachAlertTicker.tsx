import { slaColors } from '@/design-system/tokens/colors';
export function SlaBreachAlertTicker() {
  return (
    <div
      className={`rounded border px-3 py-2 text-sm ${slaColors.critical.background} ${slaColors.critical.foreground} ${slaColors.critical.border}`}
    >
      SLA breach alerts ? live
    </div>
  );
}
