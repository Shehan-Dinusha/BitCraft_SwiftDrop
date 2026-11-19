import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Field, SurfaceCard } from '@/design-system';
import {
  PasswordMatchMeter,
  PasswordStrengthMeter
} from '@/features/auth/components/PasswordStrengthMeter';
import { validatePassword } from '@/features/auth/passwordPolicy';
import { parseApiError } from '@/services/api/apiError';
import { changePassword } from '@/services/api/authApi';
import { useAuthStore } from '@/store/useAuthStore';

/**
 * Changes the password. The server signs the account out everywhere afterwards, so this page does
 * the same and sends the customer to the sign-in page with a note.
 */
export function ChangePasswordCard() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const signOut = useAuthStore((state) => state.signOut);

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const context = { username: user?.username, email: user?.email };

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (next === current) {
      setError('Choose a password that is different from your current one.');
      return;
    }
    if (next !== confirm) {
      setError('The new passwords do not match.');
      return;
    }
    const policyError = validatePassword(next, context);
    if (policyError) {
      setError(policyError);
      return;
    }

    setSaving(true);
    try {
      await changePassword(current, next);
      signOut();
      navigate('/login', {
        replace: true,
        state: { notice: 'Password changed. Sign in with your new password.' }
      });
    } catch (requestError) {
      setError(
        parseApiError(requestError).message ?? 'We could not change the password. Try again.'
      );
      setSaving(false);
    }
  }

  return (
    <SurfaceCard title="Change password">
      <form className="flex max-w-md flex-col gap-4" onSubmit={onSubmit} noValidate>
        <p className="text-body text-secondary">
          You will be signed out on every device afterwards and need to sign in again.
        </p>
        {error ? (
          <Alert tone="danger" title="Password not changed">
            {error}
          </Alert>
        ) : null}

        <Field
          label="Current password"
          variant="password"
          autoComplete="current-password"
          maxLength={72}
          required
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
        />
        <div className="flex flex-col gap-2">
          <Field
            label="New password"
            variant="password"
            autoComplete="new-password"
            maxLength={72}
            required
            value={next}
            onChange={(event) => setNext(event.target.value)}
          />
          <PasswordStrengthMeter password={next} context={context} />
        </div>
        <div className="flex flex-col gap-2">
          <Field
            label="Repeat the new password"
            variant="password"
            autoComplete="new-password"
            maxLength={72}
            required
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
          />
          <PasswordMatchMeter password={next} confirm={confirm} />
        </div>

        <Button
          type="submit"
          className="self-start"
          loading={saving}
          loadingLabel="Changing password"
          disabled={!current || !next || !confirm}
        >
          Change password
        </Button>
      </form>
    </SurfaceCard>
  );
}

export default ChangePasswordCard;
