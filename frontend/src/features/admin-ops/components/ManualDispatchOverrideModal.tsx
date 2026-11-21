import { Modal } from '@/design-system/primitives/Modal';
import { Button, Field } from '@/design-system';
import type {
  CourierShiftResponse,
  CourierSuggestionResponse,
  DispatchAssignmentResponse
} from '@/services/api/dispatchApi';
import { useEffect, useState } from 'react';

const STATUS_REASONS: Record<string, string> = {
  BUSY: 'Busy',
  ON_BREAK: 'On break',
  OFFLINE: 'Offline'
};

/**
 * Mirrors the backend check used by /assign and /reassign (CourierShift.canAcceptParcel):
 * a courier can take a parcel only when AVAILABLE and below their own maxCapacity.
 * Returns why a courier cannot be chosen, or null when they can.
 */
function getIneligibilityReason(
  status: CourierShiftResponse['shiftStatus'],
  active: number | undefined,
  capacity: number | undefined
): string | null {
  if (!status) return 'Status unknown';
  if (status !== 'AVAILABLE') return STATUS_REASONS[status] ?? status;
  if (active === undefined || capacity === undefined) return 'Workload unknown';
  if (active >= capacity) return 'At capacity';
  return null;
}

export function ManualDispatchOverrideModal({
  open,
  onClose,
  order,
  couriers,
  suggestions,
  loadingSuggestions,
  submitting,
  onSubmit
}: {
  open: boolean;
  onClose: () => void;
  order: DispatchAssignmentResponse | null;
  couriers: CourierShiftResponse[];
  suggestions: CourierSuggestionResponse[];
  loadingSuggestions: boolean;
  submitting: boolean;
  onSubmit: (courierId: string, reason: string) => Promise<void>;
}) {
  const [courierId, setCourierId] = useState('');
  const [reason, setReason] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  // When reassigning, the courier who already holds the order cannot be its replacement.
  const currentCourierId = order?.courierId;
  const isReassignment = Boolean(currentCourierId);
  const courierOptions = couriers
    .filter((courier) => courier.courierId !== currentCourierId)
    .map((courier) => {
      // Suggestions are fetched when the modal opens, so their counts are the freshest.
      const suggestion = suggestions.find((item) => item.courierId === courier.courierId);
      const active = suggestion?.activeParcels ?? courier.activeParcelsCount;
      const capacity = suggestion?.maxCapacity ?? courier.maxCapacity;
      return {
        courier,
        active,
        capacity,
        unavailableReason: getIneligibilityReason(courier.shiftStatus, active, capacity)
      };
    });
  const eligibleOptions = courierOptions.filter((option) => option.unavailableReason === null);
  // Offline couriers are not on shift, so they are counted rather than listed.
  const unavailableOptions = courierOptions.filter(
    (option) => option.unavailableReason !== null && option.courier.shiftStatus !== 'OFFLINE'
  );
  const offlineCount = courierOptions.length - eligibleOptions.length - unavailableOptions.length;
  const eligibleIds = new Set(eligibleOptions.map((option) => option.courier.courierId));
  const selectableSuggestions = suggestions.filter(
    (suggestion) => suggestion.courierId !== currentCourierId && eligibleIds.has(suggestion.courierId)
  );

  // Preselect the top-ranked suggestion that is still eligible.
  const defaultCourierId = selectableSuggestions[0]?.courierId ?? '';
  useEffect(() => {
    if (!open) return;
    setCourierId(defaultCourierId);
    setReason('');
    setValidationError(null);
  }, [open, order?.orderId, defaultCourierId]);

  const courierHint = loadingSuggestions
    ? 'Loading ranked suggestions...'
    : eligibleOptions.length === 0
      ? 'No courier can take this order right now: a courier must be AVAILABLE and below their capacity.'
      : offlineCount > 0
        ? `${offlineCount} offline courier${offlineCount === 1 ? '' : 's'} not listed.`
        : undefined;

  const submit = async () => {
    if (!courierId) {
      setValidationError('Select an available courier before confirming.');
      return;
    }
    if (courierId === currentCourierId) {
      setValidationError('Select a courier other than the one currently assigned.');
      return;
    }
    if (!eligibleIds.has(courierId)) {
      setValidationError('This courier is not available or has reached capacity. Select another.');
      return;
    }
    setValidationError(null);
    await onSubmit(
      courierId,
      reason.trim() ||
        (isReassignment ? 'Dispatcher console reassignment' : 'Dispatcher console assignment')
    );
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={order?.courierId ? 'Reassign courier' : 'Assign courier'}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button variant="primary" onClick={submit} loading={submitting}>
            {order?.courierId ? 'Confirm reassignment' : 'Confirm assignment'}
          </Button>
        </div>
      }
    >
      {order ? (
        <div className="space-y-4">
          <div className="rounded-lg bg-canvas p-3 text-caption text-secondary">
            <p className="font-mono font-semibold text-primary">
              {order.trackingNumber ?? order.orderId}
            </p>
            <p className="mt-1">
              {order.pickupDistrict ?? 'Unknown pickup'} to{' '}
              {order.deliveryDistrict ?? 'Unknown destination'} · {order.orderSpeed ?? 'STANDARD'}
            </p>
            {isReassignment ? (
              <p className="mt-1">
                Currently assigned to {order.courierName ?? currentCourierId}
              </p>
            ) : null}
          </div>

          <Field
            label="Courier"
            hint={courierHint}
            error={validationError ?? undefined}
          >
            <select
              value={courierId}
              onChange={(event) => {
                setCourierId(event.target.value);
                setValidationError(null);
              }}
              disabled={submitting}
              className="h-9 w-full rounded-md border border-border-subtle bg-card px-2 text-caption text-primary outline-none focus:border-border-focus focus:ring-2 focus:ring-border-focus/20"
              aria-label="Courier"
            >
              <option value="">Select a courier</option>
              {eligibleOptions.map(({ courier, active, capacity }) => (
                <option key={courier.courierId} value={courier.courierId}>
                  {courier.courierName ?? courier.courierId} ·{' '}
                  {courier.currentDistrict ?? 'Unknown district'} · {active ?? 0}/{capacity ?? 0} ·{' '}
                  {courier.shiftStatus}
                </option>
              ))}
              {unavailableOptions.length > 0 ? (
                <optgroup label="Unavailable">
                  {unavailableOptions.map(({ courier, active, capacity, unavailableReason }) => (
                    <option key={courier.courierId} value={courier.courierId} disabled>
                      {courier.courierName ?? courier.courierId} · {active ?? 0}/{capacity ?? 0} ·{' '}
                      {unavailableReason}
                    </option>
                  ))}
                </optgroup>
              ) : null}
            </select>
          </Field>

          <Field label="Reason" hint="Optional">
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              disabled={submitting}
              rows={2}
              className="w-full rounded-md border border-border-subtle bg-card px-2 py-1.5 text-caption text-primary outline-none focus:border-border-focus focus:ring-2 focus:ring-border-focus/20"
            />
          </Field>

          {selectableSuggestions.length > 0 ? (
            <div className="rounded-lg border border-border-subtle p-3">
              <p className="text-overline font-semibold uppercase text-secondary">
                Recommended couriers
              </p>
              <div className="mt-2 space-y-1.5">
                {selectableSuggestions.slice(0, 3).map((suggestion) => (
                  <button
                    key={suggestion.courierId}
                    type="button"
                    onClick={() => setCourierId(suggestion.courierId ?? '')}
                    className="flex w-full items-center justify-between rounded-md px-2 py-1.5 text-left text-caption hover:bg-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
                  >
                    <span className="text-primary">
                      {suggestion.courierName ?? suggestion.courierId}
                    </span>
                    <span className="text-secondary">
                      {suggestion.samePickupDistrict ? 'Same district · ' : ''}
                      {suggestion.activeParcels ?? 0}/{suggestion.maxCapacity ?? 0}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </Modal>
  );
}
