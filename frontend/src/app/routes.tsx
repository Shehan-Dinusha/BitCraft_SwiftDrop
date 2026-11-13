import { Navigate, RouteObject } from 'react-router-dom';
import { AppShell } from '@/app/AppShell';
import { RoleGuard } from '@/app/RoleGuard';
import { catalogRoutes } from '@/features/design-system-catalog/catalogRoutes';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { SignUpPage } from '@/features/auth/pages/SignUpPage';
import { OAuthCallbackPage } from '@/features/auth/pages/OAuthCallbackPage';
import { CustomerBookingPage } from '@/features/customer/pages/CustomerBookingPage';
import { LiveTrackingPage } from '@/features/customer/pages/LiveTrackingPage';
import { OrderHistoryPage } from '@/features/customer/pages/OrderHistoryPage';
import { OrderDetailPage } from '@/features/customer/pages/OrderDetailPage';
import { CourierShiftDashboard } from '@/features/courier/pages/CourierShiftDashboard';
import { ActiveDeliveryExecutionPage } from '@/features/courier/pages/ActiveDeliveryExecutionPage';
import { MerchantPortalDashboard } from '@/features/merchant/pages/MerchantPortalDashboard';
import { MerchantRegistrationPage } from '@/features/merchant/pages/MerchantRegistrationPage';
import { WebhookResiliencyConsolePage } from '@/features/merchant/pages/WebhookResiliencyConsolePage';
import { FleetControlTowerPage } from '@/features/admin-ops/pages/FleetControlTowerPage';
import { ZonePricingEditorPage } from '@/features/admin-ops/pages/ZonePricingEditorPage';
import { DispatchBoardPage } from '@/features/admin-ops/pages/DispatchBoardPage';
import { MerchantApprovalsPage } from '@/features/admin-ops/pages/MerchantApprovalsPage';
import { NotificationConsolePage } from '@/features/admin-ops/pages/NotificationConsolePage';
import { AnalyticsDashboardPage } from '@/features/admin-ops/pages/AnalyticsDashboardPage';
import { UserManagementPage } from '@/features/admin-ops/pages/UserManagementPage';
import { PublicTrackingPage } from '@/features/public-tracking/pages/PublicTrackingPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <SignUpPage /> },
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
        path: '/customer/tracking/:trackingNumber',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <LiveTrackingPage />
          </RoleGuard>
        )
      },
      {
        path: '/customer/book',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <CustomerBookingPage />
          </RoleGuard>
        )
      },
      {
        path: '/customer/orders',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <OrderHistoryPage />
          </RoleGuard>
        )
      },
      {
        path: '/customer/orders/:orderId',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <OrderDetailPage />
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
        path: '/courier/delivery/:id',
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
        path: '/merchant/register',
        element: (
          <RoleGuard roles={['MERCHANT']}>
            <MerchantRegistrationPage />
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
        path: '/ops/zones',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <ZonePricingEditorPage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/dispatch',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <DispatchBoardPage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/merchants',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <MerchantApprovalsPage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/notifications',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <NotificationConsolePage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/analytics',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <AnalyticsDashboardPage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/users',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <UserManagementPage />
          </RoleGuard>
        )
      }
    ]
  },
  {
    path: '/track',
    element: <PublicTrackingPage />
  },
  {
    path: '/track/:trackingNumber',
    element: <PublicTrackingPage />
  },
  // The design catalog is public: no login, no role, no app shell.
  ...catalogRoutes,
  { path: '*', element: <Navigate to="/login" replace /> }
];
