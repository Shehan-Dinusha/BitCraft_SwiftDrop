import { Send, Warehouse } from 'lucide-react';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { districtLabel } from '@/features/profile/profileFormat';
import { Badge, CELL, DispatchTable, TabEmpty, TrackingCell } from './DispatchTable';
import { HubParcelTable } from './HubParcelTable';
import { orderKey } from './dispatchQueues';

interface HubTabProps {
  orders: DispatchAssignmentResponse[];
  searching: boolean;
  busy: boolean;
  now: number;
  onRun: (orders: DispatchAssignmentResponse[]) => void;
}

/** Handed in at this district's hub, waiting to leave for the drop-off district. */
export function ReadyToSendTab({ orders, searching, ...rest }: HubTabProps) {
  if (orders.length === 0) {
    return <TabEmpty searching={searching} title="No parcels waiting at the hub" />;
  }
  return (
    <HubParcelTable
      label="Ready to send"
      orders={orders}
      groupField="deliveryDistrict"
      groupPrefix="To"
      timeLabel="Handed in"
      actionLabel="Mark outbound"
      busyLabel="Sending"
      actionIcon={<Send />}
      {...rest}
    />
  );
}

function notSentStage(order: DispatchAssignmentResponse) {
  if (order.hubStage === 'AT_HUB') return `At the ${districtLabel(order.pickupDistrict)} hub`;
  if (order.pickedUpAt) return 'Picked up';
  return order.courierId ? 'Courier assigned' : 'Waiting for a courier';
}

/** On the way from other districts. Only a parcel already marked Outbound can be accepted. */
export function IncomingTab({ orders, searching, ...rest }: HubTabProps) {
  const arriving = orders.filter((order) => order.hubStage === 'OUTBOUND');
  const notSent = orders.filter((order) => order.hubStage !== 'OUTBOUND');
  if (orders.length === 0) return <TabEmpty searching={searching} title="Nothing on the way" />;
  return (
    <>
      {arriving.length ? (
        <HubParcelTable
          label="On the way to the hub"
          orders={arriving}
          groupField="pickupDistrict"
          groupPrefix="From"
          timeLabel="Sent"
          actionLabel="Accept at hub"
          busyLabel="Accepting"
          actionIcon={<Warehouse />}
          {...rest}
        />
      ) : null}
      {notSent.length ? (
        <section>
          <h2 className="px-3 py-3 text-label font-semibold text-primary">
            Not sent yet ({notSent.length})
          </h2>
          <DispatchTable label="Not sent yet" headers={['Order', 'From', 'Stage']}>
            {notSent.map((order) => (
              <tr key={orderKey(order)} className="hover:bg-canvas">
                <td className={CELL}>
                  <TrackingCell order={order} />
                </td>
                <td className={CELL}>{districtLabel(order.pickupDistrict) || 'Unknown'}</td>
                <td className={CELL}>
                  <Badge>{notSentStage(order)}</Badge>
                </td>
              </tr>
            ))}
          </DispatchTable>
        </section>
      ) : null}
    </>
  );
}
