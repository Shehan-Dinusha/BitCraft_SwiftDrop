import { useState } from 'react';
import { UserCheck } from 'lucide-react';
import { Button, QueueChip } from '@/design-system';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { Badge, CELL, DispatchTable, RouteCell, TabEmpty, TrackingCell } from './DispatchTable';
import {
  formatAge,
  isAtHub,
  orderKey,
  orderLabel,
  sortQueue,
  waitingSince
} from './dispatchQueues';

type Filter = 'all' | 'pickup' | 'hub';

/** Orders waiting for a courier: pickups in this district, and parcels its hub has accepted. */
export function ToAssignTab({
  orders,
  searching,
  now,
  onAssign
}: {
  orders: DispatchAssignmentResponse[];
  searching: boolean;
  now: number;
  onAssign: (order: DispatchAssignmentResponse) => void;
}) {
  const [filter, setFilter] = useState<Filter>('all');
  const atHub = orders.filter(isAtHub);
  const pickups = orders.filter((order) => !isAtHub(order));
  const chips: { id: Filter; label: string; list: DispatchAssignmentResponse[] }[] = [
    { id: 'all', label: 'All', list: orders },
    { id: 'pickup', label: 'To pick up', list: pickups },
    { id: 'hub', label: 'At the hub', list: atHub }
  ];
  const shown = sortQueue(chips.find((chip) => chip.id === filter)?.list ?? orders);

  return (
    <>
      <div className="flex flex-wrap gap-2 px-3 pb-3">
        {chips.map((chip) => (
          <QueueChip
            key={chip.id}
            label={chip.label}
            count={chip.list.length}
            selected={filter === chip.id}
            onClick={() => setFilter(chip.id)}
          />
        ))}
      </div>
      {shown.length === 0 ? (
        <TabEmpty searching={searching} title="No orders to assign" />
      ) : (
        <DispatchTable
          label="Orders to assign"
          headers={['Order', 'Route', 'Stage', 'Waiting', 'Action']}
        >
          {shown.map((order) => (
            <tr key={orderKey(order)} className="hover:bg-canvas">
              <td className={CELL}>
                <TrackingCell order={order} />
              </td>
              <td className={CELL}>
                <RouteCell order={order} />
              </td>
              <td className={CELL}>
                <Badge tone={isAtHub(order) ? 'info' : 'neutral'}>
                  {isAtHub(order) ? 'At the hub' : 'To pick up'}
                </Badge>
              </td>
              <td className={`${CELL} whitespace-nowrap text-secondary`}>
                {formatAge(waitingSince(order), now) || '—'}
              </td>
              <td className={`${CELL} text-right`}>
                <Button
                  density="compact"
                  leftIcon={<UserCheck />}
                  aria-label={`Assign ${orderLabel(order)}`}
                  onClick={() => onAssign(order)}
                >
                  Assign
                </Button>
              </td>
            </tr>
          ))}
        </DispatchTable>
      )}
    </>
  );
}
