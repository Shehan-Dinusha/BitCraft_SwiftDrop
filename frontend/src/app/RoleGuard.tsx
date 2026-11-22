import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import type { PersonaRole } from '@/types/contracts';
import { roleHome } from '@/app/roleHome';

/**
 * Route-level authorization guard.
 *
 * Redirects unauthenticated visitors to /login (preserving `from` so the page
 * can redirect back after sign-in). A signed-in user whose persona is not in
 * `roles` is sent to their own home page: sending them to /login would look
 * like they had been signed out.
 *
 * Usage:
 *   <RoleGuard roles={['ADMIN_OPS']}>
 *     <FleetControlTowerPage />
 *   </RoleGuard>
 */
export function RoleGuard({ roles, children }: { roles: PersonaRole[]; children: ReactNode }) {
  const { token, role } = useAuthStore();
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return roles.includes(role) ? <>{children}</> : <Navigate to={roleHome[role]} replace />;
}

export default RoleGuard;

