import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import { Alert, Button, Field, SurfaceCard } from '@/design-system';
import { Modal } from '@/design-system/primitives/Modal';
import { leaveToLogin } from '@/features/auth/leaveToLogin';
import { parseApiError } from '@/services/api/apiError';
import { deleteOwnAccount } from '@/services/api/authApi';
import { useAuthStore } from '@/store/useAuthStore';

/**
 * Erases the account and the personal data on it. The customer has to type their password in a
 * confirmation dialog first, because this cannot be undone.
 */
export function DeleteAccountCard() {
  const navigate = useNavigate();
  const signOut = useAuthStore((state) => state.signOut);

  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    if (deleting) return;
    setOpen(false);
    setPassword('');
    setError(null);
  };

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!password) return;
    setError(null);
    setDeleting(true);
    try {
      await deleteOwnAccount(password);
      leaveToLogin(signOut, navigate, 'Your account has been deleted.');
    } catch (requestError) {
      setError(
        parseApiError(requestError).message ?? 'We could not delete the account. Try again.'
      );
      setDeleting(false);
    }
  }

  return (
    <>
      <SurfaceCard title="Delete account">
        <div className="flex flex-col gap-4">
          <p className="max-w-prose text-body text-secondary">
            This erases your account and the personal details on it: your name, username, email,
            phone number and home district. You will be signed out everywhere and can no longer sign
            in. It cannot be undone.
          </p>
          <p className="max-w-prose text-body text-secondary">
            Parcels you already booked stay on record for delivery and billing, but they will no
            longer be linked to your name.
          </p>
          <Button
            type="button"
            variant="destructive"
            className="self-start"
            leftIcon={<Trash2 aria-hidden="true" />}
            onClick={() => setOpen(true)}
          >
            Delete my account
          </Button>
        </div>
      </SurfaceCard>

      <Modal
        open={open}
        onClose={close}
        size="sm"
        title="Delete your account?"
        description="This cannot be undone."
        footer={
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={close}
              disabled={deleting}
              className="w-full sm:w-auto"
            >
              Keep my account
            </Button>
            <Button
              type="submit"
              form="delete-account-form"
              variant="destructive"
              loading={deleting}
              loadingLabel="Deleting account"
              disabled={!password}
              className="w-full sm:w-auto"
            >
              Delete my account
            </Button>
          </div>
        }
      >
        <form id="delete-account-form" className="flex flex-col gap-4" onSubmit={onSubmit}>
          <p className="text-body text-secondary">
            Enter your password to confirm. Your personal details will be erased straight away.
          </p>
          {error ? (
            <Alert tone="danger" title="Account not deleted">
              {error}
            </Alert>
          ) : null}
          <Field
            label="Your password"
            variant="password"
            autoComplete="current-password"
            maxLength={72}
            required
            autoFocus
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </form>
      </Modal>
    </>
  );
}

export default DeleteAccountCard;
