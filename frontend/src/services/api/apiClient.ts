import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/useAuthStore';
import type { AuthTokenResponse } from '@/services/api/authApi';

type RetryableRequest = InternalAxiosRequestConfig & { _retry?: boolean };

export const apiClient = axios.create({ baseURL: '/api' });

// Refresh tokens are single use, and the API treats a reused one as theft (it revokes every session of
// the user). A page fires several requests at once, so when the access token is stale they all get a
// 401 together. They must share ONE refresh instead of each spending the same refresh token.
// The refresh token itself is the HttpOnly `swiftdrop_refresh` cookie; the browser attaches it.
let inFlightRefresh: Promise<AuthTokenResponse> | null = null;

// The cookie is shared by every tab, so two tabs refreshing at the same moment would also spend the same
// token. A Web Lock makes them take turns: each refresh then sends the cookie the previous one set.
const REFRESH_LOCK = 'swiftdrop-auth-refresh';

function postRefresh(): Promise<AuthTokenResponse> {
  return axios
    .post<AuthTokenResponse>('/api/v1/auth/refresh', {}, { withCredentials: true })
    .then(({ data }) => data);
}

async function postRefreshInTurn(): Promise<AuthTokenResponse> {
  if (!navigator.locks) return postRefresh();
  return await navigator.locks.request(REFRESH_LOCK, postRefresh);
}

/**
 * Rotates the session with the refresh cookie and stores the new tokens. The only way to call
 * `/v1/auth/refresh`: requests in this tab share one call, and the lock makes other tabs wait their turn.
 */
export function refreshSession(): Promise<AuthTokenResponse> {
  inFlightRefresh ??= postRefreshInTurn()
    .then((data) => {
      useAuthStore.getState().setAuth(data);
      return data;
    })
    .finally(() => {
      inFlightRefresh = null;
    });
  return inFlightRefresh;
}

/**
 * Signs a new tab in from the refresh cookie. The access token is kept per tab (sessionStorage), so a tab
 * opened by hand starts without one even though the user is signed in. Tried once per tab; without a valid
 * cookie the tab simply stays signed out.
 */
export async function restoreSession(): Promise<void> {
  const state = useAuthStore.getState();
  if (state.sessionRestore !== 'idle' || state.token || state.isDevSession) return;
  state.setSessionRestore('running');
  try {
    await refreshSession();
  } catch {
    // No cookie, or it expired or was revoked: the guard sends the user to sign in.
  } finally {
    useAuthStore.getState().setSessionRestore('done');
  }
}

// ─── Request interceptor ────────────────────────────────────────────────────
// Attach the in-memory Bearer token to every outgoing request.
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ─── Response interceptor ───────────────────────────────────────────────────
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetryableRequest | undefined;

    // ── 403 Forbidden ──────────────────────────────────────────────────────
    // The user is authenticated but lacks the required role for this resource.
    // Show a non-intrusive toast and reject so the caller can decide what to do.
    if (error.response?.status === 403) {
      showForbiddenToast();
      return Promise.reject(error);
    }

    // ── 401 Unauthorized ───────────────────────────────────────────────────
    // Don't retry auth endpoints themselves (login/register/refresh) — that
    // creates infinite loops.  Also skip retries for dev-mode fake tokens.
    const isAuthEndpoint =
      request?.url?.includes('/v1/auth/login') ||
      request?.url?.includes('/v1/auth/register') ||
      request?.url?.includes('/v1/auth/refresh');

    if (
      error.response?.status !== 401 ||
      !request ||
      request._retry ||
      isAuthEndpoint
    ) {
      return Promise.reject(error);
    }

    // Developer persona has a fake token — every real API call 401s.
    // Leave them signed in; the page shows its own error state.
    if (useAuthStore.getState().isDevSession) {
      return Promise.reject(error);
    }

    // Another request may have refreshed the session while this one was in flight. Retry once with the
    // newer token instead of spending the single-use refresh token a second time.
    const sentWith = String(request.headers.Authorization ?? '').replace(/^Bearer\s+/i, '');
    const currentToken = useAuthStore.getState().token;
    request._retry = true; // never retry the same request twice
    if (currentToken && sentWith !== currentToken) {
      request.headers.Authorization = `Bearer ${currentToken}`;
      return apiClient(request);
    }

    // No signed-in user means there is no session cookie worth trying.
    if (!useAuthStore.getState().user) {
      useAuthStore.getState().signOut();
      return Promise.reject(error);
    }

    try {
      const { accessToken } = await refreshSession();
      request.headers.Authorization = `Bearer ${accessToken ?? ''}`;
      return apiClient(request);
    } catch {
      // Refresh token is expired or revoked — force a clean sign-out.
      useAuthStore.getState().signOut();
      return Promise.reject(error);
    }
  }
);

// ─── Forbidden toast helper ─────────────────────────────────────────────────
// A lightweight, dependency-free toast.  Replace with your design-system
// notification primitive once one is available.
function showForbiddenToast() {
  const existing = document.getElementById('swiftdrop-forbidden-toast');
  if (existing) return; // Don't stack duplicates.

  const toast = document.createElement('div');
  toast.id = 'swiftdrop-forbidden-toast';
  toast.setAttribute('role', 'alert');
  toast.setAttribute('aria-live', 'assertive');
  toast.style.cssText = [
    'position:fixed',
    'bottom:1.5rem',
    'right:1.5rem',
    'z-index:9999',
    'display:flex',
    'align-items:center',
    'gap:0.5rem',
    'padding:0.75rem 1.25rem',
    'border-radius:0.75rem',
    'background:var(--color-surface-elevated,#1e293b)',
    'color:var(--color-status-danger,#f87171)',
    'font-size:0.875rem',
    'font-weight:600',
    'box-shadow:0 4px 24px rgba(0,0,0,0.3)',
    'pointer-events:none'
  ].join(';');
  toast.textContent = '⛔  You don\'t have permission to perform this action.';

  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 4000);
}
