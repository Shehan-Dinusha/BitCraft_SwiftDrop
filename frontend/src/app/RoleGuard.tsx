import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import type { PersonaRole } from '@/types/contracts';

/**
 * Route-level authorization guard.
 *
 * Redirects unauthenticated visitors to /login (preserving `from` so the page
 * can redirect back after sign-in).  Redirects authenticated users whose
 * persona is not in `roles` back to /login rather than showing an empty page.
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

  return roles.includes(role) ? <>{children}</> : <Navigate to="/login" replace />;
}

export default RoleGuard;

