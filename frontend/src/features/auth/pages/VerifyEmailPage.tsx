import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Mail, MailCheck } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { resendVerification, verifyEmail } from '@/services/api/authApi';
import { parseApiError } from '@/services/api/apiError';
import { maskEmail } from '@/features/auth/authStyles';
import { useCooldown } from '@/features/auth/useCooldown';
import {
  AuthLink,
  AuthShell,
  authFieldClass,
  FieldIcon,
  SrLabel
} from '@/features/auth/components/AuthShell';

const RESEND_COOLDOWN_SECONDS = 60;

/**
 * Step 2 of sign-up: the user types the 6-digit code we emailed. A correct code verifies the
 * address and leads to the "registered" page, from where the user signs in.
 *
 *   /verify-email?identifier=<email or username>            right after registration (code already sent)
 *   /verify-email?identifier=<...>&resend=1                 redirected from login (sends a fresh code)
 */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const initialIdentifier = searchParams.get('identifier') ?? '';
  const [identifier, setIdentifier] = useState(initialIdentifier);
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const { secondsLeft, start } = useCooldown(
    searchParams.get('resend') === '1' ? 0 : RESEND_COOLDOWN_SECONDS
  );
  const autoResent = useRef(false);

  async function sendCode(target: string) {
    setError(null);
    try {
      await resendVerification(target);
      setNotice('A new code is on its way. Check your inbox (and spam folder).');
      start(RESEND_COOLDOWN_SECONDS);
    } catch (requestError) {
      setError(
        parseApiError(requestError).message ?? 'Could not send a new code. Try again shortly.'
      );
    }
  }

  // Coming from a blocked login: no code was sent yet, so send one automatically (once).
  useEffect(() => {
    if (searchParams.get('resend') !== '1' || !initialIdentifier || autoResent.current) return;
    autoResent.current = true;
    void sendCode(initialIdentifier);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setLoading(true);
    try {
      await verifyEmail(identifier.trim(), code);
      navigate('/registered', { replace: true });
    } catch (requestError) {
      const { status, message } = parseApiError(requestError);
      if (status === 409) {
        navigate('/login', {
          replace: true,
          state: { notice: 'Your email is already verified. Please sign in.' }
        });
        return;
      }
      setError(message ?? 'Unable to verify the code. Check your connection and try again.');
    } finally {
      setLoading(false);
    }
  }

  const masked = maskEmail(identifier);

  return (
    <AuthShell
      icon={<MailCheck />}
      title="Verify your email"
      description={
        masked
          ? `We sent a 6-digit code to ${masked}. It expires in 10 minutes.`
          : 'Enter the 6-digit code we sent to your email. It expires in 10 minutes.'
      }
      below={
        <>
          Wrong account? <AuthLink to="/login">Back to sign in</AuthLink>
        </>
      }
    >
      {error ? (
        <Alert tone="danger" title="Verification failed" className="mb-5">
          {error}
        </Alert>
      ) : null}
      {notice && !error ? <Alert tone="info" title={notice} className="mb-5" /> : null}

      <form className="flex flex-col gap-3" onSubmit={onSubmit}>
        {initialIdentifier ? null : (
          <>
            <SrLabel htmlFor="verify-identifier">Username or email</SrLabel>
            <Input
              id="verify-identifier"
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

        <SrLabel htmlFor="verify-code">6-digit verification code</SrLabel>
        <Input
          id="verify-code"
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

        <Button
          type="submit"
          className="mt-3 h-12 w-full rounded-xl shadow-ambient"
          loading={loading}
          loadingLabel="Verifying"
          disabled={code.length !== 6 || !identifier.trim()}
        >
          Verify email
        </Button>
      </form>

      <p className="mt-6 text-center text-body text-secondary">
        Didn&apos;t get it?{' '}
        <button
          type="button"
          onClick={() => void sendCode(identifier.trim())}
          disabled={secondsLeft > 0 || !identifier.trim()}
          className="rounded font-semibold text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus disabled:cursor-not-allowed disabled:text-muted disabled:no-underline"
        >
          {secondsLeft > 0 ? `Resend code in ${secondsLeft}s` : 'Resend code'}
        </button>
      </p>
    </AuthShell>
  );
}

export default VerifyEmailPage;
