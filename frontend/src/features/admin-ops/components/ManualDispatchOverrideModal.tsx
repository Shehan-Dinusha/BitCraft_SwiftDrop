import { Modal } from '@/design-system/primitives/Modal';
import { Button, Field } from '@/design-system';
import type {
  CourierShiftResponse,
  CourierSuggestionResponse,
  DispatchAssignmentResponse
} from '@/services/api/dispatchApi';
import { useEffect, useState } from 'react';

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

  useEffect(() => {
    if (!open) return;
    setCourierId(suggestions[0]?.courierId ?? '');
    setReason('');
    setValidationError(null);
  }, [open, order?.orderId, suggestions]);

  const selectableCouriers = couriers.filter((courier) => courier.shiftStatus !== 'OFFLINE');

  const submit = async () => {
    if (!courierId) {
      setValidationError('Select an available courier before confirming.');
      return;
    }
    setValidationError(null);
    await onSubmit(courierId, reason.trim() || 'Dispatcher console assignment');
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={order?.courierId ? 'Reassign courier' : 'Assign courier'}
      description="Choose an available courier for this order. Suggestions are ranked by district and workload."
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
          </div>

          <Field
            label="Courier"
            hint={loadingSuggestions ? 'Loading ranked suggestions...' : undefined}
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
              {selectableCouriers.map((courier) => {
                const suggestion = suggestions.find((item) => item.courierId === courier.courierId);
                return (
                  <option key={courier.courierId} value={courier.courierId}>
                    {courier.courierName ?? courier.courierId} ·{' '}
                    {courier.currentDistrict ?? 'Unknown district'} ·{' '}
                    {suggestion?.activeParcels ?? courier.activeParcelsCount ?? 0}/
                    {suggestion?.maxCapacity ?? courier.maxCapacity ?? 0} · {courier.shiftStatus}
                  </option>
                );
              })}
            </select>
          </Field>

          <Field label="Assignment reason" hint="Optional audit note for the dispatcher action.">
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              disabled={submitting}
              rows={2}
              className="w-full rounded-md border border-border-subtle bg-card px-2 py-1.5 text-caption text-primary outline-none focus:border-border-focus focus:ring-2 focus:ring-border-focus/20"
              placeholder="e.g. Colombo hub overflow"
            />
          </Field>

          {suggestions.length > 0 ? (
            <div className="rounded-lg border border-border-subtle p-3">
              <p className="text-overline font-semibold uppercase text-secondary">
                Recommended couriers
              </p>
              <div className="mt-2 space-y-1.5">
                {suggestions.slice(0, 3).map((suggestion) => (
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
