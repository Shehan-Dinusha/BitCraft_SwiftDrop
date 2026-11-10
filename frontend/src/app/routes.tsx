import { Navigate, RouteObject } from 'react-router-dom';
import { AppShell } from '@/app/AppShell';
import { RoleGuard } from '@/app/RoleGuard';
import { catalogRoutes } from '@/features/design-system-catalog/catalogRoutes';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { SignUpPage } from '@/features/auth/pages/SignUpPage';
import { OAuthCallbackPage } from '@/features/auth/pages/OAuthCallbackPage';
import { CustomerBookingPage } from '@/features/customer/pages/CustomerBookingPage';
import { LiveTrackingPage } from '@/features/customer/pages/LiveTrackingPage';
import { CourierShiftDashboard } from '@/features/courier/pages/CourierShiftDashboard';
import { ActiveDeliveryExecutionPage } from '@/features/courier/pages/ActiveDeliveryExecutionPage';
import { MerchantPortalDashboard } from '@/features/merchant/pages/MerchantPortalDashboard';
import { WebhookResiliencyConsolePage } from '@/features/merchant/pages/WebhookResiliencyConsolePage';
import { FleetControlTowerPage } from '@/features/admin-ops/pages/FleetControlTowerPage';
import { UserManagementPage } from '@/features/admin-ops/pages/UserManagementPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <SignUpPage /> },
  // Google OAuth 2.0 callback — public, no AppShell, no role guard.
  { path: '/auth/callback', element: <OAuthCallbackPage /> },
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
      },
      {
        // User management is ADMIN_OPS-guarded; only SYSTEM_ADMIN can mutate roles/statuses
        // (enforced server-side via @PreAuthorize). The DISPATCHER role maps to ADMIN_OPS
        // in the frontend but cannot call /api/v1/users — the page will show a 403 error.
        path: '/ops/users',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <UserManagementPage />
          </RoleGuard>
        )
      }
    ]
  },
  // The design catalog is public: no login, no role, no app shell.
  ...catalogRoutes,
  { path: '*', element: <Navigate to="/login" replace /> }
];
