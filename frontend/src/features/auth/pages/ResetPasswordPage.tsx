import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { KeyRound, Lock, Mail } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { forgotPassword, resetPassword } from '@/services/api/authApi';
import { parseApiError } from '@/services/api/apiError';
import { maskEmail } from '@/features/auth/authStyles';
import { validatePassword, type PasswordContext } from '@/features/auth/passwordPolicy';
import {
  PasswordMatchMeter,
  PasswordStrengthMeter
} from '@/features/auth/components/PasswordStrengthMeter';
import { useCooldown } from '@/features/auth/useCooldown';
import {
  AuthLink,
  AuthShell,
  authFieldClass,
  FieldIcon,
  SrLabel
} from '@/features/auth/components/AuthShell';

const RESEND_COOLDOWN_SECONDS = 60;

/** Step 2 of "forgot password": the emailed code plus a new password. */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const initialIdentifier = searchParams.get('identifier') ?? '';
  const [identifier, setIdentifier] = useState(initialIdentifier);
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const { secondsLeft, start } = useCooldown(
    searchParams.get('sent') === '1' ? RESEND_COOLDOWN_SECONDS : 0
  );
  // We only know what the user typed to find the account, so check the new password against that.
  const trimmedIdentifier = identifier.trim();
  const passwordContext: PasswordContext = trimmedIdentifier.includes('@')
    ? { email: trimmedIdentifier }
    : { username: trimmedIdentifier };

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setNotice(null);

    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    const policyError = validatePassword(password, passwordContext);
    if (policyError) {
      setError(policyError);
      return;
    }

    setLoading(true);
    try {
      await resetPassword(trimmedIdentifier, code, password);
      navigate('/login', {
        replace: true,
        state: { notice: 'Password updated. Sign in with your new password.' }
      });
    } catch (requestError) {
      // e.g. "New password must be different from your current password." The code stays valid.
      setError(parseApiError(requestError).message ?? 'Unable to reset the password. Try again.');
    } finally {
      setLoading(false);
    }
  }

  async function resend() {
    setError(null);
    try {
      await forgotPassword(trimmedIdentifier);
      setNotice('If the account exists, a new code is on its way.');
      start(RESEND_COOLDOWN_SECONDS);
    } catch (requestError) {
      setError(
        parseApiError(requestError).message ?? 'Could not send a new code. Try again shortly.'
      );
    }
  }

  const masked = maskEmail(identifier);

  return (
    <AuthShell
      icon={<KeyRound />}
      title="Choose a new password"
      description={
        masked
          ? `If ${masked} has an account, we sent it a 6-digit code. It expires in 10 minutes.`
          : 'Enter the 6-digit code we emailed you and choose a new password.'
      }
      below={
        <>
          Remembered it? <AuthLink to="/login">Back to sign in</AuthLink>
        </>
      }
    >
      {error ? (
        <Alert tone="danger" title="Password not updated" className="mb-5">
          {error}
        </Alert>
      ) : null}
      {notice && !error ? <Alert tone="info" title={notice} className="mb-5" /> : null}

      <form className="flex flex-col gap-3" onSubmit={onSubmit}>
        {initialIdentifier ? null : (
          <>
            <SrLabel htmlFor="reset-identifier">Username or email</SrLabel>
            <Input
              id="reset-identifier"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              autoComplete="username"
              placeholder="Username or email"
              required
              className={authFieldClass}
              leading={
                <FieldIcon>
                  <Mail />
                </FieldIcon>
              }
            />
          </>
        )}

        <SrLabel htmlFor="reset-code">6-digit reset code</SrLabel>
        <Input
          id="reset-code"
          value={code}
          onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
          inputMode="numeric"
          autoComplete="one-time-code"
          placeholder="000000"
          pattern="\d{6}"
          maxLength={6}
          required
          autoFocus
          className={`${authFieldClass} h-14 min-h-14`}
          inputClassName="text-center font-mono text-mono-l tabular-nums tracking-[0.5em]"
        />

        <div className="flex flex-col gap-2">
          <SrLabel htmlFor="reset-password">New password</SrLabel>
          <Input
            id="reset-password"
            variant="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="new-password"
            placeholder="New password"
            maxLength={72}
            required
            className={authFieldClass}
            leading={
              <FieldIcon>
                <Lock />
              </FieldIcon>
            }
          />
          <PasswordStrengthMeter password={password} context={passwordContext} />
        </div>

        <div className="flex flex-col gap-2">
          <SrLabel htmlFor="reset-confirm">Confirm new password</SrLabel>
          <Input
            id="reset-confirm"
            variant="password"
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
            autoComplete="new-password"
            placeholder="Confirm new password"
            maxLength={72}
            required
            className={authFieldClass}
            leading={
              <FieldIcon>
                <Lock />
              </FieldIcon>
            }
          />
          <PasswordMatchMeter password={password} confirm={confirm} />
        </div>

        <Button
          type="submit"
          className="mt-3 h-12 w-full rounded-xl shadow-ambient"
          loading={loading}
          loadingLabel="Updating password"
          disabled={code.length !== 6 || !trimmedIdentifier}
        >
          Update password
        </Button>
      </form>

      <p className="mt-6 text-center text-body text-secondary">
        Didn&apos;t get a code?{' '}
        <button
          type="button"
          onClick={() => void resend()}
          disabled={secondsLeft > 0 || !trimmedIdentifier}
          className="rounded font-semibold text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus disabled:cursor-not-allowed disabled:text-muted disabled:no-underline"
        >
          {secondsLeft > 0 ? `Resend code in ${secondsLeft}s` : 'Resend code'}
        </button>
      </p>
    </AuthShell>
  );
}

export default ResetPasswordPage;
