import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { exchangeOAuthCode, oauthErrorMessage } from '@/services/api/authApi';
import { personaForRole, useAuthStore } from '@/store/useAuthStore';
import { roleHome } from '@/app/roleHome';
import axios from 'axios';

/**
 * Landing page for the Google OAuth 2.0 callback redirect.
 *
 * The backend redirects here after a successful Google login:
 *   /auth/callback?code=<one-time-exchange-code>
 *
 * This page immediately exchanges the code for a SwiftDrop token pair and
 * navigates the user to their role home.  The code is single-use and expires
 * in 2 minutes, so the exchange happens on mount with no user interaction.
 *
 * Error states:
 *   - `oauth_error=access_denied` — user denied Google consent.
 *   - Missing or expired code — network/timing issue; offer a retry link.
 */
export function OAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);

  const [error, setError] = useState<string | null>(null);
  // Prevent double-exchange in React Strict Mode (double-invoke in dev).
  const exchanged = useRef(false);

  useEffect(() => {
    // Surface a backend-reported access-denied error (user cancelled consent).
    const oauthError = searchParams.get('oauth_error');
    if (oauthError) {
      setError(oauthErrorMessage(oauthError));
      return;
    }

    const code = searchParams.get('code');
    if (!code) {
      setError('No exchange code received from the server. Please try signing in again.');
      return;
    }

    if (exchanged.current) return;
    exchanged.current = true;

    (async () => {
      try {
        const tokens = await exchangeOAuthCode(code);
        setAuth(tokens);
        navigate(roleHome[personaForRole(tokens.role)], { replace: true });
      } catch (err) {
        if (axios.isAxiosError(err)) {
          const message = (err.response?.data as { message?: string } | undefined)?.message;
          setError(message ?? 'Sign-in failed. The link may have expired — please try again.');
        } else {
          setError('Unable to complete sign-in. Check your connection and try again.');
        }
      }
    })();
  }, [searchParams, navigate, setAuth]);

  // ── Loading state ──────────────────────────────────────────────────────────
  if (!error) {
    return (
      <main className="grid min-h-screen place-items-center bg-canvas p-6 text-primary">
        <section className="flex flex-col items-center gap-4 text-center">
          {/* Spinner */}
          <svg
            className="h-10 w-10 animate-spin text-brand-primary"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
          <p className="text-sm font-medium text-secondary">Completing Google sign-in…</p>
        </section>
      </main>
    );
  }

  // ── Error state ────────────────────────────────────────────────────────────
  return (
    <main className="grid min-h-screen place-items-center bg-canvas p-6 text-primary">
      <section className="w-full max-w-sm rounded-2xl border border-border-subtle bg-card p-8 shadow-ambient text-center">
        <p className="text-xs font-bold uppercase tracking-wider text-brand-primary">SwiftDrop</p>
        <h1 className="mt-3 text-xl font-bold tracking-tight">Sign-in failed</h1>
        <p role="alert" className="mt-3 text-sm text-status-danger">
          {error}
        </p>
        <a
          href="/login"
          className="mt-6 inline-flex w-full items-center justify-center rounded-lg bg-brand-primary px-4 py-2.5 text-sm font-semibold text-brand-on transition-colors hover:bg-brand-hover"
        >
          Back to sign-in
        </a>
      </section>
    </main>
  );
}

export default OAuthCallbackPage;

