import { useState } from 'react';
import { UserCheck } from 'lucide-react';
import { Button, QueueChip } from '@/design-system';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { Badge, CELL, DispatchTable, RouteCell, TabEmpty, TrackingCell } from './DispatchTable';
import { ShopPickups } from './ShopPickups';
import { parcelSize } from './courierFit';
import {
  formatAge,
  groupByShop,
  isAtHub,
  isReturn,
  orderKey,
  orderLabel,
  sortQueue,
  waitingSince
} from './dispatchQueues';

type Filter = 'all' | 'pickup' | 'hub';

/** Orders waiting for a courier: pickups in this district, and parcels its hub has accepted. */
export function ToAssignTab({
  orders: all,
  searching,
  now,
  showDistrict,
  onAssign,
  onShopAssigned
}: {
  orders: DispatchAssignmentResponse[];
  searching: boolean;
  now: number;
  /** An admin looking at every district. */
  showDistrict: boolean;
  onAssign: (order: DispatchAssignmentResponse) => void;
  onShopAssigned: (message: string) => void;
}) {
  const [filter, setFilter] = useState<Filter>('all');
  // A shop's parcels are one row above, not one row each.
  const shops = groupByShop(all);
  const orders = all.filter((order) => !order.shopPickup);
  const atHub = orders.filter(isAtHub);
  const pickups = orders.filter((order) => !isAtHub(order));
  const chips: { id: Filter; label: string; list: DispatchAssignmentResponse[] }[] = [
    { id: 'all', label: 'All', list: orders },
    { id: 'pickup', label: 'To pick up', list: pickups },
    { id: 'hub', label: 'At the hub', list: atHub }
  ];
  // Orders booked before sizes were recorded have none; without any, the column has nothing to say.
  const sized = orders.some((order) => parcelSize(order));
  const shown = sortQueue(chips.find((chip) => chip.id === filter)?.list ?? orders);

  return (
    <>
      <ShopPickups
        shops={shops}
        now={now}
        showDistrict={showDistrict}
        onAssigned={onShopAssigned}
      />
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
          headers={[
            'Order',
            'Route',
            ...(sized ? ['Size and weight'] : []),
            'Stage',
            'Waiting',
            'Action'
          ]}
        >
          {shown.map((order) => (
            <tr key={orderKey(order)} className="hover:bg-canvas">
              <td className={CELL}>
                <TrackingCell order={order} />
              </td>
              <td className={CELL}>
                <RouteCell order={order} />
              </td>
              {sized ? (
                <td className={`${CELL} whitespace-nowrap text-secondary`}>
                  {parcelSize(order) ?? 'Not recorded'}
                </td>
              ) : null}
              <td className={CELL}>
                <Badge tone={isReturn(order) ? 'warning' : isAtHub(order) ? 'info' : 'neutral'}>
                  {isReturn(order) ? 'Return' : isAtHub(order) ? 'At the hub' : 'To pick up'}
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
