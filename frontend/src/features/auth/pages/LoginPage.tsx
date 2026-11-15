import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { Lock, LogIn, Mail } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { roleHome } from '@/app/roleHome';
import { loginUser, oauthErrorMessage } from '@/services/api/authApi';
import { personaForRole, useAuthStore } from '@/store/useAuthStore';
import { EMAIL_NOT_VERIFIED, parseApiError } from '@/services/api/apiError';
import { useSignedInRedirect } from '@/features/auth/useSignedInRedirect';
import {
  AuthDivider,
  AuthLink,
  AuthShell,
  authFieldClass,
  FieldIcon,
  GoogleTile,
  SrLabel
} from '@/features/auth/components/AuthShell';

export function LoginPage() {
  const setAuth = useAuthStore((state) => state.setAuth);
  const setSession = useAuthStore((state) => state.setSession);
  const navigate = useNavigate();
  const location = useLocation();
  const locationState = location.state as { from?: string; notice?: string } | null;
  const from = locationState?.from;
  const notice = locationState?.notice;

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [searchParams] = useSearchParams();
  const oauthError = searchParams.get('oauth_error');
  const [error, setError] = useState<string | null>(
    oauthError ? oauthErrorMessage(oauthError) : null
  );
  const signedInHome = useSignedInRedirect();

  if (signedInHome) return <Navigate to={signedInHome} replace />;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const tokens = await loginUser(username.trim(), password);
      setAuth(tokens);
      // Redirect to the page the user tried to visit, or their role home.
      navigate(from ?? roleHome[personaForRole(tokens.role)], { replace: true });
    } catch (requestError) {
      const parsed = parseApiError(requestError);
      if (parsed.code === EMAIL_NOT_VERIFIED) {
        // Right password, unverified email: continue on the code screen and send a fresh code.
        navigate(`/verify-email?identifier=${encodeURIComponent(username.trim())}&resend=1`);
        return;
      }
      setError(
        parsed.message ??
          (parsed.status
            ? 'Unable to sign in. Check your credentials and try again.'
            : 'Unable to sign in. Check your connection and try again.')
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      icon={<LogIn />}
      title="Sign in to SwiftDrop"
      description="Send, track and manage your parcels across all 25 districts."
      below={
        <>
          Don&apos;t have an account? <AuthLink to="/register">Create one</AuthLink>
        </>
      }
    >
      {notice && !error ? <Alert tone="success" title={notice} className="mb-5" /> : null}
      {error ? (
        <Alert tone="danger" title="Sign-in failed" className="mb-5">
          {error}
        </Alert>
      ) : null}

      <form className="flex flex-col gap-3" onSubmit={onSubmit}>
        <SrLabel htmlFor="login-username">Username or email</SrLabel>
        <Input
          id="login-username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
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

        <SrLabel htmlFor="login-password">Password</SrLabel>
        <Input
          id="login-password"
          variant="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          placeholder="Password"
          required
          className={authFieldClass}
          leading={
            <FieldIcon>
              <Lock />
            </FieldIcon>
          }
        />

        <Link
          to="/forgot-password"
          className="self-end rounded text-label text-primary hover:text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
        >
          Forgot password?
        </Link>

        <Button
          type="submit"
          className="mt-3 h-12 w-full rounded-xl shadow-ambient"
          loading={loading}
          loadingLabel="Signing in"
        >
          Sign in
        </Button>
      </form>

      <AuthDivider label="Or sign in with" />
      <GoogleTile />

      {/* Developer shortcut: only in local dev builds, never in production. */}
      {import.meta.env.DEV ? (
        <Button
          variant="ghost"
          className="mt-4 w-full text-caption"
          onClick={() => {
            setSession('dev-token', 'CUSTOMER');
            navigate(roleHome.CUSTOMER, { replace: true });
          }}
        >
          Continue in development mode
        </Button>
      ) : null}
    </AuthShell>
  );
}

export default LoginPage;
