import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { roleHome } from '@/app/roleHome';
import { loginUser, googleAuthorizeUrl, oauthErrorMessage } from '@/services/api/authApi';
import { personaForRole, useAuthStore } from '@/store/useAuthStore';

export function LoginPage() {
  const setAuth = useAuthStore((state) => state.setAuth);
  const setSession = useAuthStore((state) => state.setSession);
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [searchParams] = useSearchParams();
  const oauthError = searchParams.get('oauth_error');
  const [error, setError] = useState<string | null>(
    oauthError ? oauthErrorMessage(oauthError) : null
  );

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
      if (axios.isAxiosError(requestError)) {
        const message = (requestError.response?.data as { message?: string } | undefined)?.message;
        setError(message ?? 'Unable to sign in. Check your credentials and try again.');
      } else {
        setError('Unable to sign in. Check your connection and try again.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-canvas p-6 text-primary">
      <section className="w-full max-w-md rounded-2xl border border-border-subtle bg-card p-8 shadow-ambient">
        <p className="text-xs font-bold uppercase tracking-wider text-brand-primary">
          SwiftDrop / Velocity
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Move every delivery forward.</h1>
        <p className="mt-2 text-sm text-secondary">
          Sign in with your SwiftDrop account. Your role determines which operational console opens.
        </p>

        <form className="mt-8 space-y-4" onSubmit={onSubmit}>
          <label className="block space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-secondary">
              Username or email
            </span>
            <Input
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              placeholder="customer.one"
              required
            />
          </label>

          <label className="block space-y-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-secondary">
              Password
            </span>
            <Input
              variant="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              placeholder="Your password"
              required
            />
          </label>

          {error ? (
            <p role="alert" className="text-sm font-medium text-status-danger">
              {error}
            </p>
          ) : null}

          <Button type="submit" className="w-full" loading={loading} loadingLabel="Signing in">
            Sign in
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-secondary">
          No account?{' '}
          <Link to="/register" className="font-semibold text-brand-primary hover:underline">
            Create one
          </Link>
        </p>

        {/* ── Google OAuth sign-in ─────────────────────────────────────── */}
        <div className="mt-5 flex items-center gap-3">
          <span className="h-px flex-1 bg-border-subtle" />
          <span className="text-xs text-muted">or</span>
          <span className="h-px flex-1 bg-border-subtle" />
        </div>

        {/*
          Use an <a> tag for a full browser navigation — not fetch/axios.
          The backend starts the Google OAuth redirect from this URL.
        */}
        <a
          href={googleAuthorizeUrl()}
          className="mt-4 inline-flex w-full items-center justify-center gap-3 rounded-lg border border-border-subtle bg-card px-4 py-2.5 text-sm font-semibold text-primary transition-colors hover:bg-surface-hover"
        >
          {/* Official Google "G" logo */}
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 48 48"
            className="h-5 w-5 shrink-0"
            aria-hidden="true"
          >
            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.18 1.48-4.97 2.31-8.16 2.31-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
            <path fill="none" d="M0 0h48v48H0z"/>
          </svg>
          Continue with Google
        </a>

        {/* Developer shortcut — only visible in local dev builds, never in production. */}
        {import.meta.env.DEV ? (
          <div className="mt-6 border-t border-border-subtle pt-5">
            <p className="text-xs text-muted">
              Local UI work without the backend still uses a development persona token.
            </p>
            <Button
              variant="outline"
              className="mt-3 w-full"
              onClick={() => {
                setSession('dev-token', 'CUSTOMER');
                navigate(roleHome.CUSTOMER, { replace: true });
              }}
            >
              Continue in development mode
            </Button>
          </div>
        ) : null}
      </section>
    </main>
  );
}

export default LoginPage;
