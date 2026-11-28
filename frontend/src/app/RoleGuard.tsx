import { Navigate, useLocation } from 'react-router-dom';
import { useEffect, type ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { useAuthStore } from '@/store/useAuthStore';
import { restoreSession } from '@/services/api/apiClient';
import type { PersonaRole } from '@/types/contracts';
import { SYSTEM_ADMIN_HOME, homeFor } from '@/app/roleHome';

/**
 * Route-level authorization guard.
 *
 * Redirects unauthenticated visitors to /login (preserving `from` so the page
 * can redirect back after sign-in). A tab without a token first tries to sign in
 * from the refresh cookie, which every tab shares: a tab opened by hand would
 * otherwise ask a signed-in user to sign in again. A signed-in user whose persona is not in
 * `roles` is sent to their own home page: sending them to /login would look
 * like they had been signed out.
 *
 * `dispatcherOnly` marks a dispatcher's daily-work page (control tower, dispatch, couriers,
 * merchant applications): a system admin is sent to their own home instead.
 *
 * Usage:
 *   <RoleGuard roles={['ADMIN_OPS']} dispatcherOnly>
 *     <FleetControlTowerPage />
 *   </RoleGuard>
 */
export function RoleGuard({
  roles,
  dispatcherOnly = false,
  children
}: {
  roles: PersonaRole[];
  dispatcherOnly?: boolean;
  children: ReactNode;
}) {
  const { token, role, user, sessionRestore } = useAuthStore();
  const location = useLocation();

  useEffect(() => {
    if (!token && sessionRestore === 'idle') void restoreSession();
  }, [token, sessionRestore]);

  if (!token) {
    if (sessionRestore !== 'done') {
      return (
        <div role="status" className="flex min-h-screen items-center justify-center bg-canvas">
          <Loader2 aria-hidden="true" className="size-6 animate-spin text-brand-primary" />
          <span className="sr-only">Signing you in</span>
        </div>
      );
    }
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (!roles.includes(role)) {
    return <Navigate to={homeFor(role, user?.role)} replace />;
  }
  if (dispatcherOnly && user?.role === 'SYSTEM_ADMIN') {
    return <Navigate to={SYSTEM_ADMIN_HOME} replace />;
  }
  return <>{children}</>;
}

export default RoleGuard;
