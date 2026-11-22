import { useState } from 'react';
import { RotateCw, Undo2, Warehouse } from 'lucide-react';
import { Button, Modal } from '@/design-system';
import type { DeliveryFailureReason } from '@/services/api/deliveryApi';
import type { DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { districtLabel } from '@/features/profile/profileFormat';
import { crossesDistricts } from '@/types/fsm';
import { LOSS_REASONS, failureLabel } from '@/utils/failureReasons';
import { Badge, CELL, DispatchTable, RouteCell, TabEmpty, TrackingCell } from './DispatchTable';
import { HubParcelTable } from './HubParcelTable';
import { formatAge, orderKey, orderLabel } from './dispatchQueues';

export type ReturnStep =
  | 'HELD_AT_HUB'
  | 'AT_DESTINATION_HUB'
  | 'RETURN_IN_TRANSIT'
  | 'RETURN_AT_HUB'
  | 'FAILED';

/** Failed deliveries: what waits for a decision, what couriers bring back, and returns arriving here. */
export function ReturnsTab({
  orders,
  searching,
  busy,
  now,
  onRun
}: {
  orders: DispatchAssignmentResponse[];
  searching: boolean;
  busy: boolean;
  now: number;
  onRun: (
    orders: DispatchAssignmentResponse[],
    step: ReturnStep,
    reason?: DeliveryFailureReason
  ) => void;
}) {
  const [lost, setLost] = useState<DispatchAssignmentResponse | null>(null);
  const held = orders.filter((order) => order.hubStage === 'HELD_AT_HUB');
  const withCourier = orders.filter((order) => order.hubStage === 'RETURN_TO_HUB');
  const arriving = orders.filter((order) => order.hubStage === 'RETURN_OUTBOUND');
  if (orders.length === 0) return <TabEmpty searching={searching} title="No returns" />;

  return (
    <>
      {held.length ? (
        <section>
          <Heading text={`At the hub (${held.length})`} />
          <DispatchTable
            label="Failed deliveries at the hub"
            headers={['Order', 'Route', 'Not delivered', 'Here for', 'Action']}
          >
            {held.map((order) => (
              <tr key={orderKey(order)} className="hover:bg-canvas">
                <td className={CELL}>
                  <TrackingCell order={order} />
                </td>
                <td className={CELL}>
                  <RouteCell order={order} />
                </td>
                <td className={CELL}>
                  <Reason order={order} />
                </td>
                <td className={`${CELL} whitespace-nowrap text-secondary`}>
                  {formatAge(order.updatedAt, now) || '—'}
                </td>
                <td className={`${CELL} text-right`}>
                  <div className="flex flex-wrap justify-end gap-2">
                    {order.canRetry ? (
                      <Button
                        density="compact"
                        leftIcon={<RotateCw />}
                        disabled={busy}
                        aria-label={`Try ${orderLabel(order)} again`}
                        onClick={() => onRun([order], 'AT_DESTINATION_HUB')}
                      >
                        Try again
                      </Button>
                    ) : null}
                    <Button
                      variant={order.canRetry ? 'outline' : 'primary'}
                      density="compact"
                      leftIcon={<Undo2 />}
                      disabled={busy}
                      aria-label={`Return ${orderLabel(order)} to the sender`}
                      onClick={() =>
                        onRun(
                          [order],
                          crossesDistricts(order) ? 'RETURN_IN_TRANSIT' : 'RETURN_AT_HUB'
                        )
                      }
                    >
                      {crossesDistricts(order)
                        ? `Send back to ${districtLabel(order.pickupDistrict)}`
                        : 'Return'}
                    </Button>
                    <Button
                      variant="ghost"
                      density="compact"
                      disabled={busy}
                      aria-label={`${orderLabel(order)} is lost or damaged`}
                      onClick={() => setLost(order)}
                    >
                      Lost or damaged
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </DispatchTable>
        </section>
      ) : null}
      {arriving.length ? (
        <section>
          <Heading text={`Coming back to your senders (${arriving.length})`} />
          <HubParcelTable
            label="Returns on the way"
            orders={arriving}
            groupField="deliveryDistrict"
            groupPrefix="From"
            timeLabel="Sent"
            actionLabel="Accept at hub"
            busyLabel="Accepting"
            actionIcon={<Warehouse />}
            busy={busy}
            now={now}
            onRun={(list) => onRun(list, 'RETURN_AT_HUB')}
          />
        </section>
      ) : null}
      {withCourier.length ? (
        <section>
          <Heading text={`With a courier (${withCourier.length})`} />
          <DispatchTable
            label="Failed deliveries with a courier"
            headers={['Order', 'Courier', 'Not delivered', 'Action']}
          >
            {withCourier.map((order) => (
              <tr key={orderKey(order)} className="hover:bg-canvas">
                <td className={CELL}>
                  <TrackingCell order={order} />
                </td>
                <td className={CELL}>{order.courierName ?? '—'}</td>
                <td className={CELL}>
                  <Reason order={order} />
                </td>
                <td className={`${CELL} text-right`}>
                  <Button
                    variant="outline"
                    density="compact"
                    leftIcon={<Warehouse />}
                    disabled={busy}
                    aria-label={`Received ${orderLabel(order)} at the hub`}
                    onClick={() => onRun([order], 'HELD_AT_HUB')}
                  >
                    Received at hub
                  </Button>
                </td>
              </tr>
            ))}
          </DispatchTable>
        </section>
      ) : null}
      {lost ? (
        <Modal
          open
          onClose={() => setLost(null)}
          title="Lost or damaged"
          description={`${orderLabel(lost)} is not delivered or returned. The fee can then be refunded on the Cash page.`}
        >
          <div className="flex flex-wrap justify-end gap-2 pt-2">
            {LOSS_REASONS.map((reason) => (
              <Button
                key={reason}
                variant="destructive"
                onClick={() => {
                  onRun([lost], 'FAILED', reason);
                  setLost(null);
                }}
              >
                {failureLabel(reason)}
              </Button>
            ))}
          </div>
        </Modal>
      ) : null}
    </>
  );
}

function Heading({ text }: { text: string }) {
  return <h2 className="px-3 py-3 text-label font-semibold text-primary">{text}</h2>;
}

function Reason({ order }: { order: DispatchAssignmentResponse }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      {failureLabel(order.lastAttemptReason)}
      <Badge tone={order.canRetry ? 'neutral' : 'warning'}>
        {order.deliveryAttempts === 1 ? '1 attempt' : `${order.deliveryAttempts ?? 0} attempts`}
      </Badge>
    </span>
  );
}
