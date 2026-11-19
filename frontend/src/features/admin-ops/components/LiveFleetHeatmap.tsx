import { MapPin } from 'lucide-react';
import type { CourierShiftResponse } from '@/services/api/dispatchApi';

export function LiveFleetHeatmap({ couriers = [] }: { couriers?: CourierShiftResponse[] }) {
  return (
    <div className="rounded-xl border border-border-subtle bg-card p-3">
      <div className="mb-3 flex items-center justify-between">
        <div>
          <h2 className="text-label font-semibold text-primary">Island-wide fleet view</h2>
          <p className="text-caption text-secondary">Current courier distribution by district</p>
        </div>
        <MapPin aria-hidden="true" className="size-4 text-brand-primary" />
      </div>
      {couriers.length === 0 ? (
        <p className="py-8 text-center text-caption text-muted">No courier telemetry available.</p>
      ) : (
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {Object.entries(
            couriers.reduce<Record<string, { total: number; available: number }>>(
              (result, courier) => {
                const district = courier.currentDistrict ?? 'UNKNOWN';
                const entry = result[district] ?? { total: 0, available: 0 };
                entry.total += 1;
                if (courier.shiftStatus === 'AVAILABLE') entry.available += 1;
                result[district] = entry;
                return result;
              },
              {}
            )
          )
            .sort(([left], [right]) => left.localeCompare(right))
            .map(([district, counts]) => (
              <div key={district} className="rounded-md bg-canvas px-2.5 py-2 text-caption">
                <p className="font-semibold text-primary">{district.replaceAll('_', ' ')}</p>
                <p className="text-secondary">
                  {counts.available} available · {counts.total} total
                </p>
              </div>
            ))}
        </div>
      )}
    </div>
  );
}
