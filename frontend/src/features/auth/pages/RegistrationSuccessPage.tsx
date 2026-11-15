import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { AuthShell } from '@/features/auth/components/AuthShell';

/**
 * Shown once a new account is ready: after the emailed code is confirmed (password sign-up) or after a
 * first-time "Continue with Google". Registration never signs the user in; they sign in from here.
 *
 *   /registered                 password sign-up
 *   /registered?method=google   Google sign-up
 */
export function RegistrationSuccessPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const viaGoogle = searchParams.get('method') === 'google';

  return (
    <AuthShell
      icon={<CheckCircle2 />}
      iconTone="success"
      title="Successfully registered"
      description="Your SwiftDrop customer account is ready to use."
    >
      <Alert tone="success" title="You're all set">
        {viaGoogle
          ? 'Your account was created with your Google account. Sign in with Google to continue.'
          : 'Your email address is verified. Sign in with your username or email and password to continue.'}
      </Alert>

      <Button
        type="button"
        className="mt-6 h-12 w-full rounded-xl shadow-ambient"
        onClick={() => navigate('/login', { replace: true })}
        autoFocus
      >
        Go to sign in
      </Button>
    </AuthShell>
  );
}

export default RegistrationSuccessPage;
