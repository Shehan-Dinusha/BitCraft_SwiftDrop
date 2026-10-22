import { Navigate, RouteObject } from 'react-router-dom';
import type { ReactNode } from 'react';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { CustomerBookingPage } from '@/features/customer/pages/CustomerBookingPage';
import { LiveTrackingPage } from '@/features/customer/pages/LiveTrackingPage';
import { CourierShiftDashboard } from '@/features/courier/pages/CourierShiftDashboard';
import { ActiveDeliveryExecutionPage } from '@/features/courier/pages/ActiveDeliveryExecutionPage';
import { MerchantPortalDashboard } from '@/features/merchant/pages/MerchantPortalDashboard';
import { FleetControlTowerPage } from '@/features/admin-ops/pages/FleetControlTowerPage';
import { useAuthStore } from '@/store/useAuthStore';
import type { PersonaRole } from '@/types/contracts';

function RoleGuard({ roles, children }: { roles: PersonaRole[]; children: ReactNode }) {
  const { token, role } = useAuthStore();
  if (!token) return <Navigate to="/login" replace />;
  return roles.includes(role) ? <>{children}</> : <Navigate to="/login" replace />;
}

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/customer', element: <RoleGuard roles={['CUSTOMER']}><CustomerBookingPage /></RoleGuard> },
  { path: '/customer/tracking', element: <RoleGuard roles={['CUSTOMER']}><LiveTrackingPage /></RoleGuard> },
  { path: '/courier', element: <RoleGuard roles={['COURIER']}><CourierShiftDashboard /></RoleGuard> },
  { path: '/courier/delivery', element: <RoleGuard roles={['COURIER']}><ActiveDeliveryExecutionPage /></RoleGuard> },
  { path: '/merchant', element: <RoleGuard roles={['MERCHANT']}><MerchantPortalDashboard /></RoleGuard> },
  { path: '/ops', element: <RoleGuard roles={['ADMIN_OPS']}><FleetControlTowerPage /></RoleGuard> },
  { path: '*', element: <Navigate to="/login" replace /> }
];
