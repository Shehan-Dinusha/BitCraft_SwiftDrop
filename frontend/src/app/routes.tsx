import { Navigate, RouteObject } from 'react-router-dom';
import { AppShell } from '@/app/AppShell';
import { RoleGuard } from '@/app/RoleGuard';
import { catalogRoutes } from '@/features/design-system-catalog/catalogRoutes';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { SignUpPage } from '@/features/auth/pages/SignUpPage';
import { OAuthCallbackPage } from '@/features/auth/pages/OAuthCallbackPage';
import { VerifyEmailPage } from '@/features/auth/pages/VerifyEmailPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { RegistrationSuccessPage } from '@/features/auth/pages/RegistrationSuccessPage';
import { InviteAcceptPage } from '@/features/auth/pages/InviteAcceptPage';
import { CourierVehicleSetupPage } from '@/features/courier/pages/CourierVehicleSetupPage';
import { CourierManagementPage } from '@/features/admin-ops/pages/CourierManagementPage';
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
import { LandingPage } from '@/features/landing/pages/LandingPage';
import { ProfilePage } from '@/features/profile/ProfilePage';
import { NotificationsPage } from '@/features/notifications/NotificationsPage';

export const routes: RouteObject[] = [
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <SignUpPage /> },
  { path: '/register/business', element: <SignUpPage business /> },
  { path: '/auth/callback', element: <OAuthCallbackPage /> },
  { path: '/verify-email', element: <VerifyEmailPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/registered', element: <RegistrationSuccessPage /> },
  // Staff invite link from the email; the token travels in the URL fragment.
  { path: '/invite', element: <InviteAcceptPage /> },
  {
    element: <AppShell />,
    children: [
      // Notifications inbox and settings, one per workspace (left navigation).
      {
        path: '/customer/notifications',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <NotificationsPage />
          </RoleGuard>
        )
      },
      {
        path: '/courier/notifications',
        element: (
          <RoleGuard roles={['COURIER']}>
            <NotificationsPage />
          </RoleGuard>
        )
      },
      {
        path: '/merchant/notifications',
        element: (
          <RoleGuard roles={['MERCHANT']}>
            <NotificationsPage />
          </RoleGuard>
        )
      },
      {
        path: '/customer/profile',
        element: (
          <RoleGuard roles={['CUSTOMER']}>
            <ProfilePage />
          </RoleGuard>
        )
      },
      {
        path: '/courier/profile',
        element: (
          <RoleGuard roles={['COURIER']}>
            <ProfilePage />
          </RoleGuard>
        )
      },
      {
        path: '/merchant/profile',
        element: (
          <RoleGuard roles={['MERCHANT']}>
            <ProfilePage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/profile',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <ProfilePage />
          </RoleGuard>
        )
      },
      {
        path: '/ops/inbox',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <NotificationsPage />
          </RoleGuard>
        )
      },
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
        path: '/courier/vehicle',
        element: (
          <RoleGuard roles={['COURIER']}>
            <CourierVehicleSetupPage />
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
        path: '/ops/couriers',
        element: (
          <RoleGuard roles={['ADMIN_OPS']}>
            <CourierManagementPage />
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
