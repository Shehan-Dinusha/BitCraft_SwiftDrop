import { Button } from '@/design-system';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { Badge, CELL, DispatchTable, RouteCell, TabEmpty, TrackingCell } from './DispatchTable';
import { formatAge, isAtHub, orderKey, orderLabel } from './dispatchQueues';

function stage(order: DispatchAssignmentResponse) {
  if (order.pickedUpAt) return isAtHub(order) ? 'Out for delivery' : 'Picked up';
  return isAtHub(order) ? 'Collecting from the hub' : 'Going to pick up';
}

/** Orders a courier holds. Once picked up, the server no longer allows a reassignment. */
export function AssignedTab({
  orders,
  hiddenCount,
  searching,
  now,
  onReassign
}: {
  orders: DispatchAssignmentResponse[];
  /** Assigned orders beyond the loaded page. */
  hiddenCount: number;
  searching: boolean;
  now: number;
  onReassign: (order: DispatchAssignmentResponse) => void;
}) {
  if (orders.length === 0) return <TabEmpty searching={searching} title="No assigned orders" />;
  return (
    <>
      <DispatchTable
        label="Assigned orders"
        headers={['Order', 'Route', 'Courier', 'Stage', 'Assigned', 'Action']}
      >
        {orders.map((order) => (
          <tr key={orderKey(order)} className="hover:bg-canvas">
            <td className={CELL}>
              <TrackingCell order={order} />
            </td>
            <td className={CELL}>
              <RouteCell order={order} />
            </td>
            <td className={CELL}>{order.courierName ?? '—'}</td>
            <td className={CELL}>
              <Badge tone={order.pickedUpAt ? 'success' : 'neutral'}>{stage(order)}</Badge>
            </td>
            <td className={`${CELL} whitespace-nowrap text-secondary`}>
              {order.assignedAt ? `${formatAge(order.assignedAt, now)} ago` : '—'}
            </td>
            <td className={`${CELL} text-right`}>
              {order.pickedUpAt ? null : (
                <Button
                  variant="outline"
                  density="compact"
                  aria-label={`Reassign ${orderLabel(order)}`}
                  onClick={() => onReassign(order)}
                >
                  Reassign
                </Button>
              )}
            </td>
          </tr>
        ))}
      </DispatchTable>
      {hiddenCount > 0 ? (
        <p className="px-3 py-2 text-caption text-secondary">
          {hiddenCount} older {hiddenCount === 1 ? 'order is' : 'orders are'} not listed.
        </p>
      ) : null}
    </>
  );
}
