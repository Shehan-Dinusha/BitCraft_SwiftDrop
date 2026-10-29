import { Navigate, RouteObject, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AppShell } from '@/app/AppShell';
import { CatalogLayout } from '@/features/design-system-catalog/CatalogLayout';
import { CatalogHome } from '@/features/design-system-catalog/CatalogHome';
import { CatalogGroupPage } from '@/features/design-system-catalog/CatalogGroupPage';
import { ShellPreviewPage } from '@/features/design-system-catalog/ShellPreviewPage';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { CustomerBookingPage } from '@/features/customer/pages/CustomerBookingPage';
import { LiveTrackingPage } from '@/features/customer/pages/LiveTrackingPage';
import { CourierShiftDashboard } from '@/features/courier/pages/CourierShiftDashboard';
import { ActiveDeliveryExecutionPage } from '@/features/courier/pages/ActiveDeliveryExecutionPage';
import { MerchantPortalDashboard } from '@/features/merchant/pages/MerchantPortalDashboard';
import { WebhookResiliencyConsolePage } from '@/features/merchant/pages/WebhookResiliencyConsolePage';
import { FleetControlTowerPage } from '@/features/admin-ops/pages/FleetControlTowerPage';
import { useAuthStore } from '@/store/useAuthStore';
import type { PersonaRole } from '@/types/contracts';

function RoleGuard({ roles, children }: { roles: PersonaRole[]; children: ReactNode }) {
  const { token, role } = useAuthStore();
  const location = useLocation();
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return roles.includes(role) ? <>{children}</> : <Navigate to="/login" replace />;
}

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <AppShell />,
    children: [
      {
        path: '/customer',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <CustomerBookingPage />
          </RoleGuard>
        )
      },
      {
        path: '/customer/tracking',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <LiveTrackingPage />
          </RoleGuard>
        )
      },
      {
        path: '/courier',
        element: (
          <RoleGuard roles={['COURIER']}>
            <CourierShiftDashboard />
          </RoleGuard>
        )
      },
      {
        path: '/courier/delivery',
        element: (
          <RoleGuard roles={['COURIER']}>
            <ActiveDeliveryExecutionPage />
          </RoleGuard>
        )
      },
      {
        path: '/courier/deliveries',
        element: (
          <RoleGuard roles={['COURIER']}>
            <ActiveDeliveryExecutionPage />
          </RoleGuard>
        )
      },
      {
        path: '/merchant',
        element: (
          <RoleGuard roles={['MERCHANT']}>
            <MerchantPortalDashboard />
          </RoleGuard>
        )
      },
      {
        path: '/merchant/webhooks',
        element: (
          <RoleGuard roles={['MERCHANT', 'ADMIN_OPS']}>
            <WebhookResiliencyConsolePage />
          </RoleGuard>
        )
      },
      {
        path: '/ops',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <FleetControlTowerPage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/webhooks',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <WebhookResiliencyConsolePage />
          </RoleGuard>
        )
      }
    ]
  },
  // The design catalog is public: no login, no role, no app shell.
  {
    path: '/catalog',
    element: <CatalogLayout />,
    children: [
      { index: true, element: <CatalogHome /> },
      { path: ':slug', element: <CatalogGroupPage /> }
    ]
  },
  { path: '/catalog/preview/:role', element: <ShellPreviewPage /> },
  { path: '*', element: <Navigate to="/login" replace /> }
];
