import { useEffect, useRef, useState } from 'react';
import { Button, ChoiceChip, Modal, MoneyAmount, Textarea } from '@/design-system';
import { CANCEL_REASONS, type CancelReason } from './orderDisplay';

const REASON_MAX = 512;

interface CancelDialogProps {
  open: boolean;
  trackingNumber: string;
  pending: boolean;
  onClose: () => void;
  onConfirm: (reason: string) => void;
}

/**
 * Cancel needs a reason (the order service rejects a blank one). Common reasons are one tap; only
 * "Other" asks the customer to type.
 */
export function CancelDialog({
  open,
  trackingNumber,
  pending,
  onClose,
  onConfirm
}: CancelDialogProps) {
  const [choice, setChoice] = useState<CancelReason | null>(null);
  const [other, setOther] = useState('');
  const [error, setError] = useState<string>();
  const [focusError, setFocusError] = useState(false);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const firstChipRef = useRef<HTMLButtonElement>(null);

  // A reason error is announced by moving focus to the control that needs fixing (WCAG 3.3.1).
  useEffect(() => {
    if (!focusError) return;
    (choice === 'Other' ? reasonRef.current : firstChipRef.current)?.focus();
    setFocusError(false);
  }, [focusError, choice]);

  const fail = (message: string) => {
    setError(message);
    setFocusError(true);
  };

  const close = () => {
    if (pending) return;
    setChoice(null);
    setOther('');
    setError(undefined);
    onClose();
  };

  const confirm = () => {
    if (!choice) return fail('Choose a reason');
    if (choice === 'Other') {
      const text = other.trim();
      if (!text) return fail('Tell us why you are cancelling');
      if (/[<>]/.test(text)) return fail('Remove the < and > characters');
      setError(undefined);
      return onConfirm(text);
    }
    setError(undefined);
    onConfirm(choice);
  };

  return (
    <Modal
      open={open}
      onClose={close}
      title="Cancel this parcel?"
      description={`${trackingNumber} will not be picked up. This can't be undone.`}
      size="sm"
      footer={
        <>
          <Button type="button" variant="secondary" disabled={pending} onClick={close}>
            Keep parcel
          </Button>
          <Button
            type="button"
            variant="destructive"
            loading={pending}
            loadingLabel="Cancelling parcel"
            onClick={confirm}
          >
            Cancel parcel
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <p id="cancel-reason-label" className="text-label font-semibold text-primary">
          Why are you cancelling?
        </p>
        <div
          role="group"
          aria-labelledby="cancel-reason-label"
          aria-describedby={error && choice !== 'Other' ? 'cancel-reason-error' : undefined}
          className="flex flex-wrap gap-2"
        >
          {CANCEL_REASONS.map((reason, index) => (
            <ChoiceChip
              key={reason}
              ref={index === 0 ? firstChipRef : undefined}
              selected={choice === reason}
              onClick={() => {
                setChoice(reason);
                setError(undefined);
              }}
            >
              {reason}
            </ChoiceChip>
          ))}
        </div>
        {choice === 'Other' ? (
          <Textarea
            ref={reasonRef}
            label="Your reason"
            required
            maxLength={REASON_MAX}
            showCount
            value={other}
            error={error}
            onChange={(event) => {
              setOther(event.target.value);
              if (error) setError(undefined);
            }}
          />
        ) : error ? (
          <p id="cancel-reason-error" role="alert" className="text-caption text-status-danger">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}

interface DeclineDialogProps {
  open: boolean;
  pending: boolean;
  bookedFee: number;
  revisedFee: number;
  currency: string;
  onClose: () => void;
  onConfirm: () => void;
}

export function DeclineFeeDialog({
  open,
  pending,
  bookedFee,
  revisedFee,
  currency,
  onClose,
  onConfirm
}: DeclineDialogProps) {
  return (
    <Modal
      open={open}
      onClose={() => !pending && onClose()}
      title="Decline the new fee?"
      description="Declining cancels this parcel. You won't be charged."
      size="sm"
      footer={
        <>
          <Button type="button" variant="secondary" disabled={pending} onClick={onClose}>
            Go back
          </Button>
          <Button
            type="button"
            variant="destructive"
            loading={pending}
            loadingLabel="Declining the new fee"
            onClick={onConfirm}
          >
            Decline and cancel
          </Button>
        </>
      }
    >
      <p className="text-body text-secondary">
        The fee would change from <MoneyAmount amount={bookedFee} size="sm" currency={currency} />{' '}
        to <MoneyAmount amount={revisedFee} size="sm" currency={currency} />. Declining stops the
        delivery.
      </p>
    </Modal>
  );
}
