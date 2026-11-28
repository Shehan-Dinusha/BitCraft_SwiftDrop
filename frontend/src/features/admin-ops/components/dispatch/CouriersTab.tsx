import type { CourierShiftResponse } from '@/services/api/dispatchApi';
import { vehicleLabel } from '@/utils/vehicles';
import { Badge, CELL, DispatchTable, TabEmpty, type BadgeTone } from './DispatchTable';
import { LoadBar } from './LoadBar';
import { amount, onBreakUntil } from './courierFit';
import { colomboClock } from '@/utils/colomboDate';
import { sortCouriers } from './dispatchQueues';

export const SHIFT: Record<string, { label: string; tone: BadgeTone }> = {
  AVAILABLE: { label: 'Available', tone: 'success' },
  BUSY: { label: 'Full', tone: 'warning' },
  ON_BREAK: { label: 'On break', tone: 'info' },
  OFFLINE: { label: 'Off shift', tone: 'neutral' }
};

export function CouriersTab({
  couriers,
  searching
}: {
  couriers: CourierShiftResponse[];
  searching: boolean;
}) {
  if (couriers.length === 0) return <TabEmpty searching={searching} title="No couriers yet" />;
  return (
    <DispatchTable
      label="Courier workload"
      headers={['Courier', 'Vehicle', 'Shift', 'Parcels', 'Weight', 'Volume']}
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
            <td className={CELL}>
              <Badge tone={shift.tone}>{shift.label}</Badge>
              <ShiftNote courier={courier} />
            </td>
            <td className={CELL}>
              <LoadBar used={used} limit={limit} label={`${used} of ${limit}`} />
            </td>
            <td className={CELL}>
              <LoadBar
                used={courier.activeWeightKg ?? 0}
                limit={courier.maxWeightKg ?? 0}
                label={`${amount(courier.activeWeightKg)} of ${amount(courier.maxWeightKg)} kg`}
              />
            </td>
            <td className={CELL}>
              <LoadBar
                used={courier.activeVolumeLitres ?? 0}
                limit={courier.maxVolumeLitres ?? 0}
                label={`${amount(courier.activeVolumeLitres)} of ${amount(courier.maxVolumeLitres)} L`}
              />
            </td>
          </tr>
        );
      })}
    </DispatchTable>
  );
}

/**
 * What the dispatcher should know about the shift: at the hub or on the road (hub parcels go only to couriers at
 * the hub), the break, a late start, a shift left open overnight.
 */
function ShiftNote({ courier }: { courier: CourierShiftResponse }) {
  const status = courier.shiftStatus;
  if (status === 'ON_BREAK') {
    const over = Date.now() > Date.parse(courier.breakEndsAt ?? '');
    return (
      <p className={over ? 'text-status-warning-fg' : 'text-secondary'}>
        {over ? `Break ended ${colomboClock(courier.breakEndsAt)}` : onBreakUntil(courier.breakEndsAt)}
      </p>
    );
  }
  if (status === 'OFFLINE') {
    return courier.closedAutomatically ? (
      <p className="text-status-warning-fg">Left open, closed at night</p>
    ) : null;
  }
  return (
    <>
      <p className="text-secondary">
        {courier.runStartedAt ? `On the road since ${colomboClock(courier.runStartedAt)}` : 'At the hub'}
      </p>
      {courier.startedLate ? (
        <p className="text-status-warning-fg">Started late, {colomboClock(courier.shiftStartedAt)}</p>
      ) : null}
    </>
  );
}
