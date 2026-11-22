import { useState } from 'react';
import { Button, Field, StatusBanner } from '@/design-system';
import { Modal } from '@/design-system/primitives/Modal';
import type {
  CourierSuggestionResponse,
  DispatchAssignmentResponse
} from '@/services/api/dispatchApi';
import { vehicleLabel } from '@/utils/vehicles';
import { cn } from '@/utils/cn';
import { Badge, RouteCell, TableSkeleton, TrackingCell } from './dispatch/DispatchTable';
import { LoadBar } from './dispatch/LoadBar';
import { amount, blockedBecause, parcelSize } from './dispatch/courierFit';

interface Props {
  order: DispatchAssignmentResponse | null;
  onClose: () => void;
  /** Couriers on shift, best first; the ones who cannot take the parcel carry a reason. */
  suggestions: CourierSuggestionResponse[] | undefined;
  loadError: boolean;
  submitting: boolean;
  /** Why the last attempt was refused. */
  error: string | null;
  onSubmit: (courierId: string, reason: string) => void;
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
  const [reason, setReason] = useState('');
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
            onClick={() =>
              selected &&
              onSubmit(
                selected,
                reason.trim() ||
                  (reassigning
                    ? 'Dispatcher console reassignment'
                    : 'Dispatcher console assignment')
              )
            }
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
          <p className={cn('tabular-nums', size ? 'font-semibold' : 'text-secondary')}>
            {size ?? 'No size recorded'}
          </p>
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

        {eligible.length ? (
          <Field
            label="Reason"
            hint="Optional"
            value={reason}
            maxLength={200}
            disabled={submitting}
            onChange={(event) => setReason(event.target.value)}
          />
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
  onSelect
}: {
  courier: CourierSuggestionResponse;
  recommended: boolean;
  checked: boolean;
  disabled: boolean;
  onSelect: () => void;
}) {
  const loads = [
    { used: courier.activeParcels, limit: courier.maxCapacity, unit: 'parcels' },
    { used: courier.activeWeightKg, limit: courier.maxWeightKg, unit: 'kg' },
    { used: courier.activeVolumeLitres, limit: courier.maxVolumeLitres, unit: 'L' }
  ];
  return (
    <label
      className={cn(
        'flex cursor-pointer flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border p-3',
        'has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-border-focus',
        checked ? 'border-border-selected bg-brand-subtle' : 'border-border-subtle bg-card hover:bg-canvas'
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
        </span>
        <span className="text-secondary">{vehicleLabel(courier.vehicleType)}</span>
      </span>
      <span className="space-y-1">
        {loads.map(({ used, limit, unit }) => (
          <LoadBar
            key={unit}
            used={used ?? 0}
            limit={limit ?? 0}
            label={`${amount(used)} of ${amount(limit)} ${unit}`}
          />
        ))}
      </span>
    </label>
  );
}
