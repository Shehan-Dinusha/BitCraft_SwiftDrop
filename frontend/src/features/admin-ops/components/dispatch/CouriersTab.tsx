import type { CourierShiftResponse } from '@/services/api/dispatchApi';
import { districtLabel } from '@/features/profile/profileFormat';
import { vehicleLabel } from '@/utils/vehicles';
import { Badge, CELL, DispatchTable, TabEmpty, type BadgeTone } from './DispatchTable';
import { LoadBar } from './LoadBar';
import { sortCouriers } from './dispatchQueues';

const SHIFT: Record<string, { label: string; tone: BadgeTone }> = {
  AVAILABLE: { label: 'Available', tone: 'success' },
  BUSY: { label: 'Busy', tone: 'warning' },
  ON_BREAK: { label: 'On break', tone: 'info' },
  OFFLINE: { label: 'Off shift', tone: 'neutral' }
};

export function CouriersTab({
  couriers,
  searching,
  showDistrict
}: {
  couriers: CourierShiftResponse[];
  searching: boolean;
  showDistrict: boolean;
}) {
  if (couriers.length === 0) return <TabEmpty searching={searching} title="No couriers yet" />;
  return (
    <DispatchTable
      label="Courier workload"
      headers={['Courier', 'Vehicle', ...(showDistrict ? ['District'] : []), 'Shift', 'Parcels']}
    >
      {sortCouriers(couriers).map((courier) => {
        const shift = SHIFT[courier.shiftStatus ?? ''] ?? { label: 'Unknown', tone: 'neutral' };
        const used = courier.activeParcelsCount ?? 0;
        const limit = courier.maxCapacity ?? 0;
        return (
          <tr key={courier.courierId} className="hover:bg-canvas">
            <td className={CELL}>
              <p className="font-semibold">{courier.courierName ?? courier.courierId}</p>
              {courier.phone ? <p className="text-secondary">{courier.phone}</p> : null}
            </td>
            <td className={CELL}>{vehicleLabel(courier.vehicleType)}</td>
            {showDistrict ? (
              <td className={CELL}>{districtLabel(courier.currentDistrict) || '—'}</td>
            ) : null}
            <td className={CELL}>
              <Badge tone={shift.tone}>{shift.label}</Badge>
            </td>
            <td className={CELL}>
              <LoadBar used={used} limit={limit} label={`${used} of ${limit}`} />
            </td>
          </tr>
        );
      })}
    </DispatchTable>
  );
}
