import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/useAuthStore';
import type { AuthTokenResponse } from '@/services/api/authApi';

type RetryableRequest = InternalAxiosRequestConfig & { _retry?: boolean };

export const apiClient = axios.create({ baseURL: '/api' });

// ─── Request interceptor ────────────────────────────────────────────────────
// Attach the in-memory Bearer token to every outgoing request.
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  const user = useAuthStore.getState().user;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  if (user?.id) config.headers['X-User-Id'] = user.id;
  if (user?.role) config.headers['X-User-Role'] = user.role;
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

    const refreshToken = useAuthStore.getState().refreshToken;
    if (!refreshToken) {
      useAuthStore.getState().signOut();
      return Promise.reject(error);
    }

    // Mark so we don't retry again if the refresh itself fails.
    request._retry = true;
    try {
      // Use plain axios (not apiClient) to avoid the interceptor re-triggering.
      const { data } = await axios.post<AuthTokenResponse>('/api/v1/auth/refresh', {
        refreshToken
      });
      useAuthStore.getState().setAuth(data);
      request.headers.Authorization = `Bearer ${data.accessToken ?? ''}`;
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
