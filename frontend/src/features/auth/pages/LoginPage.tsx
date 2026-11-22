import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { ChevronRight, Lock, LogIn, Mail, ShieldCheck, Store, User, UserPlus } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { roleHome } from '@/app/roleHome';
import {
  completeMfaLogin,
  isMfaChallenge,
  loginUser,
  oauthErrorMessage,
  type AuthTokenResponse,
  type MfaChallenge
} from '@/services/api/authApi';
import { personaForRole, useAuthStore } from '@/store/useAuthStore';
import { EMAIL_NOT_VERIFIED, parseApiError } from '@/services/api/apiError';
import { useSignedInRedirect } from '@/features/auth/useSignedInRedirect';
import {
  AuthDivider,
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
  // Second sign-in step for admins and dispatchers when MFA is on.
  const [challenge, setChallenge] = useState<MfaChallenge | null>(null);
  const [code, setCode] = useState('');
  const [searchParams] = useSearchParams();
  const [choosingAccountType, setChoosingAccountType] = useState(false);
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
      const result = await loginUser(username.trim(), password);
      if (isMfaChallenge(result)) {
        setChallenge(result);
        setPassword('');
        return;
      }
      signedIn(result);
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

  function signedIn(tokens: AuthTokenResponse) {
    setAuth(tokens);
    // Redirect to the page the user tried to visit, or their role home.
    navigate(from ?? roleHome[personaForRole(tokens.role)], { replace: true });
  }

  async function onSubmitCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!challenge) return;
    setError(null);
    setLoading(true);
    try {
      signedIn(await completeMfaLogin(challenge.challengeId, code));
    } catch (requestError) {
      const parsed = parseApiError(requestError);
      if (parsed.status === 401 || parsed.status === 429) {
        // Challenge expired or too many wrong codes: start again from the password step.
        setChallenge(null);
        setCode('');
      }
      setError(parsed.message ?? 'That code did not work. Check it and try again.');
    } finally {
      setLoading(false);
    }
  }

  if (challenge) {
    return (
      <AuthShell
        icon={<ShieldCheck />}
        title="Enter your sign-in code"
        description={`We emailed you a 6-digit code. It expires in ${challenge.expiresInMinutes} minutes.`}
      >
        {error ? (
          <Alert tone="danger" title="Sign-in failed" className="mb-5">
            {error}
          </Alert>
        ) : null}
        <form className="flex flex-col gap-3" onSubmit={onSubmitCode}>
          <SrLabel htmlFor="login-mfa-code">6-digit sign-in code</SrLabel>
          <Input
            id="login-mfa-code"
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="6-digit code"
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
            disabled={code.length !== 6}
          >
            Verify and sign in
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              setChallenge(null);
              setCode('');
              setError(null);
            }}
          >
            Back to sign in
          </Button>
        </form>
      </AuthShell>
    );
  }

  if (choosingAccountType) {
    return (
      <AuthShell
        icon={<UserPlus />}
        title="Create an account"
        below={
          <>
            Already have an account?{' '}
            <TextButton onClick={() => setChoosingAccountType(false)}>Sign in</TextButton>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <AccountTypeOption
            to="/register"
            icon={<User />}
            title="For myself"
            text="Send parcels and track your orders."
          />
          <AccountTypeOption
            to="/register/business"
            icon={<Store />}
            title="For my business"
            text="Ship your orders in bulk or through the API. Needs approval."
          />
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell
      icon={<LogIn />}
      title="Sign in to SwiftDrop"
      below={
        <>
          Don&apos;t have an account?{' '}
          <TextButton onClick={() => setChoosingAccountType(true)}>Create one</TextButton>
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

/** Brand-coloured text button, styled like `AuthLink`, for switching views on this page. */
function TextButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded font-semibold text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
    >
      {children}
    </button>
  );
}

/** One choice in "Create an account": a full-width tile that opens the matching sign-up form. */
function AccountTypeOption({
  to,
  icon,
  title,
  text
}: {
  to: string;
  icon: ReactNode;
  title: string;
  text: string;
}) {
  return (
    <Link
      to={to}
      className="group flex min-h-target items-center gap-4 rounded-xl border border-border-subtle bg-card p-4 text-left shadow-ambient transition-all hover:-translate-y-px hover:border-brand-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
    >
      <span
        aria-hidden="true"
        className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-brand-subtle text-brand-primary [&_svg]:size-5"
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-body-strong text-primary">{title}</span>
        <span className="block pt-0.5 text-body text-secondary">{text}</span>
      </span>
      <ChevronRight
        aria-hidden="true"
        className="size-5 shrink-0 text-muted transition-colors group-hover:text-brand-primary"
      />
    </Link>
  );
}

export default LoginPage;
