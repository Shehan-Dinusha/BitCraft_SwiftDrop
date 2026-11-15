import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { KeyRound, Mail } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { forgotPassword } from '@/services/api/authApi';
import { parseApiError } from '@/services/api/apiError';
import {
  AuthLink,
  AuthShell,
  authFieldClass,
  FieldIcon,
  SrLabel
} from '@/features/auth/components/AuthShell';

/** Step 1 of "forgot password": ask for a reset code by username or email. */
export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await forgotPassword(identifier.trim());
      // The API answers the same way whether or not the account exists, so always continue.
      navigate(`/reset-password?identifier=${encodeURIComponent(identifier.trim())}&sent=1`);
    } catch (requestError) {
      setError(
        parseApiError(requestError).message ?? 'Unable to send a reset code. Try again shortly.'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      icon={<KeyRound />}
      title="Forgot your password?"
      description="Enter your username or email and we'll send you a 6-digit reset code."
      below={
        <>
          Remembered it? <AuthLink to="/login">Back to sign in</AuthLink>
        </>
      }
    >
      {error ? (
        <Alert tone="danger" title="We couldn't send a code" className="mb-5">
          {error}
        </Alert>
      ) : null}

      <form className="flex flex-col gap-3" onSubmit={onSubmit}>
        <SrLabel htmlFor="forgot-identifier">Username or email</SrLabel>
        <Input
          id="forgot-identifier"
          value={identifier}
          onChange={(event) => setIdentifier(event.target.value)}
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          placeholder="Username or email"
          required
          autoFocus
          className={authFieldClass}
          leading={
            <FieldIcon>
              <Mail />
            </FieldIcon>
          }
        />

        <Button
          type="submit"
          className="mt-3 h-12 w-full rounded-xl shadow-ambient"
          loading={loading}
          loadingLabel="Sending code"
        >
          Send reset code
        </Button>
      </form>
    </AuthShell>
  );
}

export default ForgotPasswordPage;
