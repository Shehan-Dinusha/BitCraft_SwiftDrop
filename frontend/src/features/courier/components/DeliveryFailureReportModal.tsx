import { useState } from 'react';
import { Modal } from '@/design-system/primitives/Modal';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { Field } from '@/design-system/primitives/Field';
import type { DeliveryFailureReason } from '@/services/api/deliveryApi';
import { AlertTriangle, FileText } from 'lucide-react';

export interface DeliveryFailureReportModalProps {
  open: boolean;
  onClose: () => void;
  deliveryId: string;
  trackingNumber?: string;
  onSubmitFailure: (reason: DeliveryFailureReason, notes: string) => Promise<void>;
  loading?: boolean;
}

export const FAILURE_REASONS: { value: DeliveryFailureReason; label: string }[] = [
  { value: 'RECIPIENT_UNAVAILABLE', label: 'Recipient not home' },
  { value: 'INCORRECT_ADDRESS', label: 'Wrong or incomplete address' },
  { value: 'ACCESS_RESTRICTED', label: "Couldn't get access" },
  { value: 'RECIPIENT_REJECTED', label: 'Recipient refused it' },
  { value: 'DAMAGED_IN_TRANSIT', label: 'Parcel damaged' },
  { value: 'ADVERSE_WEATHER', label: 'Weather or road blocked' },
  { value: 'SYSTEM_CANCELLATION', label: 'Cancelled by dispatch' },
  { value: 'OTHER', label: 'Other' }
];

export function DeliveryFailureReportModal({
  open,
  onClose,
  trackingNumber,
  onSubmitFailure,
  loading = false
}: DeliveryFailureReportModalProps) {
  const [reason, setReason] = useState<DeliveryFailureReason>('RECIPIENT_UNAVAILABLE');
  const [notes, setNotes] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!notes.trim()) {
      setValidationError('Add a short note.');
      return;
    }
    setValidationError(null);
    await onSubmitFailure(reason, notes.trim());
    onClose();
    setNotes('');
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Report failed delivery"
      description={trackingNumber || undefined}
      size="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-2" data-density="touch">
        <div className="flex items-start gap-3 rounded-xl border border-status-danger-border bg-status-danger-bg p-3 text-xs text-status-danger-fg">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-status-danger-fg" />
          <p>
            This counts as a failed attempt and alerts dispatch.
          </p>
        </div>

        {validationError && (
          <div className="rounded-lg bg-status-danger-bg p-2.5 text-xs font-semibold text-status-danger-fg">
            {validationError}
          </div>
        )}

        <Field label="Reason" required>
          <select
            value={reason}
            onChange={(e) => setReason(e.target.value as DeliveryFailureReason)}
            className="w-full rounded-xl border border-border-subtle bg-card p-3 text-sm text-primary shadow-sm focus:border-status-danger focus:outline-none focus:ring-1 focus:ring-status-danger min-h-[48px]"
          >
            {FAILURE_REASONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Note" required>
          <div className="relative">
            <Input
              value={notes}
              onChange={(e) => {
                setNotes(e.target.value);
                if (validationError) setValidationError(null);
              }}
              className="min-h-[48px] pl-9 text-sm"
              required
              autoFocus
            />
            <FileText className="absolute left-3 top-3.5 h-4 w-4 text-muted" />
          </div>
        </Field>

        <div className="flex items-center gap-3 pt-2">
          <Button
            type="button"
            variant="outline"
            className="flex-1 min-h-[44px]"
            onClick={onClose}
            disabled={loading}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="destructive"
            className="flex-1 min-h-[44px] font-bold"
            loading={loading}
          >
            <AlertTriangle className="mr-2 h-4 w-4" />
            Report
          </Button>
        </div>
      </form>
    </Modal>
  );
}

