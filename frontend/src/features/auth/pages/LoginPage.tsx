import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { roleHome } from '@/app/roleHome';
import { loginUser } from '@/services/api/authApi';
import { personaForRole, useAuthStore } from '@/store/useAuthStore';

export function LoginPage() {
  const setAuth = useAuthStore((state) => state.setAuth);
  const setSession = useAuthStore((state) => state.setSession);
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const tokens = await loginUser(username.trim(), password);
      setAuth(tokens);
      navigate(roleHome[personaForRole(tokens.role)], { replace: true });
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
      </section>
    </main>
  );
}

export default LoginPage;
