import { useEffect, useRef } from 'react';
import { LogOut } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';

export interface SignOutDialogProps {
  open: boolean;
  /** Shown so the user knows which account they are leaving. */
  accountLabel?: string;
  /** True while the logout request is in flight. The dialog can't be dismissed then. */
  signingOut: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/** "Are you sure?" step before signing out, built on the design-system Modal. */
export function SignOutDialog({
  open,
  accountLabel,
  signingOut,
  onCancel,
  onConfirm
}: SignOutDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);

  // Start on the safe choice. The Modal focuses its panel in its own effect (which runs after ours),
  // so move focus on the next frame.
  useEffect(() => {
    if (!open) return;
    const frame = requestAnimationFrame(() => cancelRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [open]);

  const dismiss = () => {
    if (!signingOut) onCancel();
  };

  return (
    <Modal
      open={open}
      onClose={dismiss}
      size="sm"
      title="Sign out of SwiftDrop?"
      description={accountLabel ? `Signed in as ${accountLabel}` : undefined}
      footer={
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button
            ref={cancelRef}
            type="button"
            variant="outline"
            onClick={dismiss}
            disabled={signingOut}
            className="w-full sm:w-auto"
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={onConfirm}
            loading={signingOut}
            loadingLabel="Signing out"
            leftIcon={<LogOut aria-hidden="true" />}
            className="w-full sm:w-auto"
          >
            Sign out
          </Button>
        </div>
      }
    >
      <p className="text-body text-secondary">
        You will need to sign in again to book, track or manage your parcels. Any unsaved changes on
        this page will be lost.
      </p>
    </Modal>
  );
}

export default SignOutDialog;
