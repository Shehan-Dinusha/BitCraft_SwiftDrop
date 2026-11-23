import { useState } from 'react';
import { Button, StatusBanner } from '@/design-system';
import { Modal } from '@/design-system/primitives/Modal';
import type {
  CourierSuggestionResponse,
  DispatchAssignmentResponse
} from '@/services/api/dispatchApi';
import { vehicleLabel } from '@/utils/vehicles';
import { cn } from '@/utils/cn';
import { Badge, RouteCell, TableSkeleton, TrackingCell } from './dispatch/DispatchTable';
import { amount, blockedBecause, onBreakUntil, parcelSize } from './dispatch/courierFit';

interface Props {
  order: DispatchAssignmentResponse | null;
  onClose: () => void;
  /** Couriers on shift, best first; the ones who cannot take the parcel carry a reason. */
  suggestions: CourierSuggestionResponse[] | undefined;
  loadError: boolean;
  submitting: boolean;
  /** Why the last attempt was refused. */
  error: string | null;
  onSubmit: (courierId: string) => void;
}

/** Choose the courier for an order. Reopening it for another order starts afresh. */
export function ManualDispatchOverrideModal(props: Props) {
  return props.order ? (
    <AssignForm key={props.order.orderId} {...props} order={props.order} />
  ) : null;
}

function AssignForm({
  order,
  onClose,
  suggestions,
  loadError,
  submitting,
  error,
  onSubmit
}: Props & { order: DispatchAssignmentResponse }) {
  const [chosen, setChosen] = useState<string>();
  const reassigning = Boolean(order.courierId);

  const eligible = (suggestions ?? []).filter((courier) => courier.eligible);
  const blocked = (suggestions ?? []).filter((courier) => !courier.eligible);
  // The best match is preselected, until the dispatcher picks someone who is still eligible.
  const selected = (eligible.find((c) => c.courierId === chosen) ?? eligible[0])?.courierId;
  const size = parcelSize(order);

  return (
    <Modal
      open
      onClose={onClose}
      title={reassigning ? 'Reassign courier' : 'Assign courier'}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" density="compact" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button
            density="compact"
            disabled={!selected}
            loading={submitting}
            onClick={() => selected && onSubmit(selected)}
          >
            {reassigning ? 'Reassign' : 'Assign'}
          </Button>
        </div>
      }
    >
      <div className="space-y-4 text-caption">
        <div className="space-y-1.5 rounded-lg bg-canvas p-3 text-primary">
          <TrackingCell order={order} />
          <RouteCell order={order} />
          {size ? <p className="font-semibold tabular-nums">{size}</p> : null}
          {reassigning ? (
            <p className="text-secondary">With {order.courierName ?? 'another courier'}</p>
          ) : null}
        </div>

        {error ? (
          <StatusBanner tone="danger" title="Not assigned">
            {error}
          </StatusBanner>
        ) : null}

        {loadError ? (
          <StatusBanner tone="danger" title="Couriers could not be loaded">
            Close this and try again.
          </StatusBanner>
        ) : suggestions === undefined ? (
          <TableSkeleton />
        ) : eligible.length === 0 ? (
          <StatusBanner tone="warning" title="No courier can take this parcel now">
            It stays in the queue.
          </StatusBanner>
        ) : (
          <div role="radiogroup" aria-label="Courier" className="space-y-2">
            {eligible.map((courier, index) => (
              <CourierOption
                key={courier.courierId}
                courier={courier}
                recommended={index === 0}
                checked={courier.courierId === selected}
                disabled={submitting}
                sized={Boolean(size)}
                onSelect={() => setChosen(courier.courierId)}
              />
            ))}
          </div>
        )}

        {blocked.length ? (
          <section aria-label="Cannot take it">
            <h3 className="text-overline uppercase text-secondary">
              Cannot take it ({blocked.length})
            </h3>
            <ul className="mt-1 divide-y divide-border-subtle">
              {blocked.map((courier) => (
                <li
                  key={courier.courierId}
                  className="flex flex-wrap justify-between gap-x-4 py-1.5 text-secondary"
                >
                  <span>
                    {courier.courierName ?? courier.courierId} · {vehicleLabel(courier.vehicleType)}
                  </span>
                  <span>{blockedBecause(courier)}</span>
                </li>
              ))}
            </ul>
          </section>
        ) : null}
      </div>
    </Modal>
  );
}

function CourierOption({
  courier,
  recommended,
  checked,
  disabled,
  sized,
  onSelect
}: {
  courier: CourierSuggestionResponse;
  recommended: boolean;
  checked: boolean;
  disabled: boolean;
  /** The order has a recorded size, so weight and volume matter too. */
  sized: boolean;
  onSelect: () => void;
}) {
  const loads = [
    { used: courier.activeParcels, limit: courier.maxCapacity, unit: 'parcels' },
    ...(sized
      ? [
          { used: courier.activeWeightKg, limit: courier.maxWeightKg, unit: 'kg' },
          { used: courier.activeVolumeLitres, limit: courier.maxVolumeLitres, unit: 'L' }
        ]
      : [])
  ];
  return (
    <label
      className={cn(
        'flex cursor-pointer flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border p-3',
        'has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-border-focus',
        checked
          ? 'border-border-selected bg-brand-subtle'
          : 'border-border-subtle bg-card hover:bg-canvas'
      )}
    >
      <input
        type="radio"
        name="dispatch-courier"
        className="accent-brand-primary"
        checked={checked}
        disabled={disabled}
        onChange={onSelect}
      />
      <span className="min-w-40 flex-1">
        <span className="flex flex-wrap items-center gap-2 font-semibold text-primary">
          {courier.courierName ?? courier.courierId}
          {recommended ? <Badge tone="success">Best match</Badge> : null}
          {courier.breakEndsAt ? <Badge tone="info">{onBreakUntil(courier.breakEndsAt)}</Badge> : null}
        </span>
        <span className="text-secondary">{vehicleLabel(courier.vehicleType)}</span>
      </span>
      <span className="text-right tabular-nums text-secondary">
        <span className="sr-only">Carrying </span>
        {loads.map(({ used, limit, unit }) => (
          <span key={unit} className="block">
            {`${amount(used)} of ${amount(limit)} ${unit}`}
          </span>
        ))}
      </span>
    </label>
  );
}
