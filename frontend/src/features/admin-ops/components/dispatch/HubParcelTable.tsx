import { Fragment, useState, type ReactNode } from 'react';
import { Button, Checkbox } from '@/design-system';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { CELL, DispatchTable, TrackingCell } from './DispatchTable';
import { formatAge, groupByDistrict, orderKey, orderLabel } from './dispatchQueues';

/**
 * Parcels taking the same hub step, grouped by the other district because a van carries one district's
 * parcels. Tick parcels (or a whole district) and run the step once for all of them.
 */
export function HubParcelTable({
  label,
  orders,
  groupField,
  groupPrefix,
  timeLabel,
  actionLabel,
  busyLabel,
  actionIcon,
  busy,
  now,
  onRun
}: {
  label: string;
  orders: DispatchAssignmentResponse[];
  groupField: 'pickupDistrict' | 'deliveryDistrict';
  /** "To" or "From", before the district name. */
  groupPrefix: string;
  /** What the last column counts from, e.g. "At the hub". */
  timeLabel: string;
  actionLabel: string;
  busyLabel: string;
  actionIcon: ReactNode;
  busy: boolean;
  now: number;
  onRun: (orders: DispatchAssignmentResponse[]) => void;
}) {
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const isSelected = (order: DispatchAssignmentResponse) => selected.has(orderKey(order));
  // Only rows still listed count: a parcel that moved on or was filtered out is never acted on.
  const chosen = orders.filter(isSelected);

  const toggle = (targets: DispatchAssignmentResponse[], on: boolean) =>
    setSelected((current) => {
      const next = new Set(current);
      for (const order of targets) {
        if (on) next.add(orderKey(order));
        else next.delete(orderKey(order));
      }
      return next;
    });

  const checkState = (targets: DispatchAssignmentResponse[]) => {
    const count = targets.filter(isSelected).length;
    return { checked: count === targets.length, mixed: count > 0 && count < targets.length };
  };

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3 px-3 pb-3">
        <Checkbox
          label={chosen.length ? `${chosen.length} selected` : `Select all ${orders.length}`}
          {...checkState(orders)}
          disabled={busy}
          onChange={(event) => toggle(orders, event.target.checked)}
        />
        <Button
          density="compact"
          leftIcon={actionIcon}
          disabled={chosen.length === 0}
          loading={busy}
          loadingLabel={busyLabel}
          onClick={() => onRun(chosen)}
        >
          {chosen.length ? `${actionLabel} (${chosen.length})` : actionLabel}
        </Button>
      </div>
      <DispatchTable label={label} headers={[null, 'Order', timeLabel]}>
        {groupByDistrict(orders, groupField).map((group) => (
          <Fragment key={group.district}>
            <tr className="bg-canvas">
              <td className={`${CELL} w-10`}>
                <Checkbox
                  aria-label={`Select all ${groupPrefix.toLowerCase()} ${group.label}`}
                  {...checkState(group.orders)}
                  disabled={busy}
                  onChange={(event) => toggle(group.orders, event.target.checked)}
                />
              </td>
              <td className={`${CELL} font-semibold`} colSpan={2}>
                {groupPrefix} {group.label} ({group.orders.length})
              </td>
            </tr>
            {group.orders.map((order) => (
              <tr key={orderKey(order)} className="hover:bg-canvas">
                <td className={`${CELL} w-10`}>
                  <Checkbox
                    aria-label={`Select ${orderLabel(order)}`}
                    checked={isSelected(order)}
                    disabled={busy}
                    onChange={(event) => toggle([order], event.target.checked)}
                  />
                </td>
                <td className={CELL}>
                  <TrackingCell order={order} />
                </td>
                <td className={`${CELL} whitespace-nowrap text-secondary`}>
                  {`${formatAge(order.updatedAt ?? order.createdAt, now) || '0 min'} ago`}
                </td>
              </tr>
            ))}
          </Fragment>
        ))}
      </DispatchTable>
    </>
  );
}
