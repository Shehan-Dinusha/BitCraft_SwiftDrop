import { PackageCheck, Send, Warehouse } from 'lucide-react';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { districtLabel } from '@/features/profile/profileFormat';
import { Badge, CELL, DispatchTable, TabEmpty, TrackingCell } from './DispatchTable';
import { HubParcelTable } from './HubParcelTable';
import { bySpeed, formatAge, orderKey } from './dispatchQueues';

interface HubTabProps {
  orders: DispatchAssignmentResponse[];
  searching: boolean;
  busy: boolean;
  now: number;
  onRun: (orders: DispatchAssignmentResponse[]) => void;
}

const staysInDistrict = (order: DispatchAssignmentResponse) =>
  Boolean(order.pickupDistrict) && order.pickupDistrict === order.deliveryDistrict;

/**
 * Handed in at this district's hub. Parcels for another district are marked Outbound; those staying in the
 * district (Standard and Express go through the hub too) are sorted for delivery, for a courier's next run.
 * Below them, read-only, what was sent and is not yet accepted by the drop-off hub.
 */
export function ReadyToSendTab({
  orders,
  sent,
  searching,
  onRun,
  onSort,
  ...rest
}: HubTabProps & {
  sent: DispatchAssignmentResponse[];
  onSort: (orders: DispatchAssignmentResponse[]) => void;
}) {
  if (orders.length === 0 && sent.length === 0) {
    return <TabEmpty searching={searching} title="No parcels waiting at the hub" />;
  }
  const local = orders.filter(staysInDistrict);
  const leaving = orders.filter((order) => !staysInDistrict(order));
  return (
    <>
      {local.length ? (
        <HubParcelTable
          label="For this district"
          orders={local}
          groupField="deliveryDistrict"
          groupPrefix="In"
          timeLabel="Handed in"
          actionLabel="Sort for delivery"
          busyLabel="Sorting"
          actionIcon={<PackageCheck />}
          onRun={onSort}
          {...rest}
        />
      ) : null}
      {leaving.length ? (
        <HubParcelTable
          label="Ready to send"
          orders={leaving}
          groupField="deliveryDistrict"
          groupPrefix="To"
          timeLabel="Handed in"
          actionLabel="Mark outbound"
          busyLabel="Sending"
          actionIcon={<Send />}
          onRun={onRun}
          {...rest}
        />
      ) : null}
      {sent.length ? <SentTable orders={sent} now={rest.now} /> : null}
    </>
  );
}

/** Sent from this hub, waiting for the drop-off hub to accept it. Nothing to do here. */
function SentTable({ orders, now }: { orders: DispatchAssignmentResponse[]; now: number }) {
  return (
    <section>
      <h2 className="px-3 py-3 text-label font-semibold text-primary">
        Sent, not accepted yet ({orders.length})
      </h2>
      <DispatchTable label="Sent, not accepted yet" headers={['Order', 'To', 'Sent']}>
        {bySpeed(orders).map((order) => (
          <tr key={orderKey(order)} className="hover:bg-canvas">
            <td className={CELL}>
              <TrackingCell order={order} />
            </td>
            <td className={CELL}>{districtLabel(order.deliveryDistrict) || 'Unknown'}</td>
            <td className={`${CELL} whitespace-nowrap text-secondary`}>
              {`${formatAge(order.updatedAt ?? order.createdAt, now) || '0 min'} ago`}
            </td>
          </tr>
        ))}
      </DispatchTable>
    </section>
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
