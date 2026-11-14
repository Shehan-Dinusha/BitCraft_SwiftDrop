import { useAuthStore } from '@/store/useAuthStore';

/**
 * Local webhook development only. When the dev server runs with `VITE_DEV_AUTH_BYPASS=true`
 * (set by `.env.webhook-dev`, i.e. `npm run dev:webhook`) and nobody is signed in, start a fake
 * MERCHANT dev session so RoleGuard does not redirect to /login.
 *
 * This only satisfies the browser route guards. The fake token is never verified by anything:
 * webhook API calls get their identity from the Vite dev proxy (see vite.config.ts). A production
 * build compiles this to a no-op because `import.meta.env.DEV` is false there.
 */
export function applyDevAuthBypass(): void {
  if (!import.meta.env.DEV || import.meta.env.VITE_DEV_AUTH_BYPASS !== 'true') return;
  const session = useAuthStore.getState();
  if (!session.token) session.setSession('dev-token', 'MERCHANT');
}
