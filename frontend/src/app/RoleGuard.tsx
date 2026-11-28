import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import type { PersonaRole } from '@/types/contracts';
import { SYSTEM_ADMIN_HOME, homeFor } from '@/app/roleHome';

/**
 * Route-level authorization guard.
 *
 * Redirects unauthenticated visitors to /login (preserving `from` so the page
 * can redirect back after sign-in). A signed-in user whose persona is not in
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
  const { token, role, user } = useAuthStore();
  const location = useLocation();

  if (!token) {
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
