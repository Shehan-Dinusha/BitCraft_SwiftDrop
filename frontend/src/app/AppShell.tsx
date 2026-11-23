import { useMemo, useState } from 'react';
import { UserRound } from 'lucide-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  AccountMenu,
  BottomNav,
  BrandMark,
  NavItem,
  SideNav,
  TopBar,
  roleShellSpec,
  useMediaQuery,
  type NavEntry,
  type ShellRole
} from '@/design-system';
import { SignOutDialog } from '@/features/auth/components/SignOutDialog';
import { leaveToLogin } from '@/features/auth/leaveToLogin';
import { NOTIFICATIONS_PATH } from '@/features/notifications/notificationTypes';
import { PROFILE_PATH, displayName, districtLabel } from '@/features/profile/profileFormat';
import { useMyAvatarUrl, useSyncCurrentUser } from '@/features/profile/avatar';
import { useUnreadNotificationCount } from '@/features/notifications/useNotificationInbox';
import { AiAssistantSlot } from '@/design-system/components/AiAssistantDock/AiAssistantSlot';
import { useAuthStore } from '@/store/useAuthStore';
import { logoutUser } from '@/services/api/authApi';
import { forgetDeviceOnSignOut } from '@/features/pwa/webPush';
import { roleHome } from '@/app/roleHome';
import type { PersonaRole } from '@/types/contracts';

/**
 * Persona -> Figma shell role. `ADMIN_OPS` covers two Figma roles; the signed-in user's backend role
 * decides which one (a dispatcher by default).
 */
function shellRoleFor(persona: PersonaRole, backendRole?: string): ShellRole {
  switch (persona) {
    case 'CUSTOMER':
      return 'customer';
    case 'COURIER':
      return 'courier';
    case 'MERCHANT':
      return 'merchant';
    default:
      return backendRole === 'SYSTEM_ADMIN' ? 'system-admin' : 'dispatcher';
  }
}

/**
 * Menu items that have a route today, as one flat list per role: the menus are short enough to scan
 * without headings. The full Figma menus live in `roleShellSpec`.
 */
const routedNav: Record<ShellRole, NavEntry[]> = {
  customer: [
    { id: 'book', label: 'Book a parcel', shortLabel: 'Book', icon: 'plus', to: '/customer' },
    {
      id: 'parcels',
      label: 'My parcels',
      shortLabel: 'Parcels',
      icon: 'package',
      to: '/customer/orders'
    },
    {
      id: 'track',
      label: 'Track a parcel',
      shortLabel: 'Track',
      icon: 'route',
      to: '/customer/tracking'
    }
  ],
  courier: [
    { id: 'shift', label: 'Shift', icon: 'home', to: '/courier' },
    {
      id: 'delivery',
      label: 'Active delivery',
      shortLabel: 'Active',
      icon: 'route',
      to: '/courier/delivery'
    },
    {
      id: 'vehicle',
      label: 'My vehicle',
      shortLabel: 'Vehicle',
      icon: 'truck',
      to: '/courier/vehicle'
    },
    {
      id: 'today-deliveries',
      label: "Today's deliveries",
      shortLabel: 'Today',
      icon: 'package',
      to: '/courier/today-deliveries'
    },
    {
      id: 'cash',
      label: 'Cash',
      icon: 'wallet',
      to: '/courier/cash'
    }
  ],
  merchant: [
    { id: 'portal', label: 'Dashboard', icon: 'grid', to: '/merchant' },
    { id: 'webhooks', label: 'Webhooks', icon: 'webhook', to: '/merchant/webhooks' },
    { id: 'api-guide', label: 'API guide', icon: 'book', to: '/merchant/api-guide' }
  ],
  dispatcher: [
    { id: 'tower', label: 'Control tower', icon: 'map', to: '/ops' },
    { id: 'dispatch', label: 'Dispatch', icon: 'inbox', to: '/ops/dispatch' },
    { id: 'couriers', label: 'Couriers', icon: 'truck', to: '/ops/couriers' },
    { id: 'cash', label: 'Cash', icon: 'wallet', to: '/ops/cash' },
    { id: 'merchants', label: 'Merchant applications', icon: 'store', to: '/ops/merchants' },
    { id: 'analytics', label: 'Analytics', icon: 'chart', to: '/ops/analytics' },
    { id: 'zones', label: 'Zone pricing', icon: 'calc', to: '/ops/zones' }
  ],
  'system-admin': [
    { id: 'tower', label: 'Control tower', icon: 'map', to: '/ops' },
    { id: 'dispatch', label: 'Dispatch', icon: 'inbox', to: '/ops/dispatch' },
    { id: 'couriers', label: 'Couriers', icon: 'truck', to: '/ops/couriers' },
    { id: 'cash', label: 'Cash', icon: 'wallet', to: '/ops/cash' },
    { id: 'merchants', label: 'Merchants', icon: 'store', to: '/ops/merchants' },
    { id: 'users', label: 'User management', icon: 'users', to: '/ops/users' },
    { id: 'working-days', label: 'Working days', icon: 'calendar', to: '/ops/working-days' },
    { id: 'analytics', label: 'Analytics', icon: 'chart', to: '/ops/analytics' },
    { id: 'zones', label: 'Zone pricing', icon: 'calc', to: '/ops/zones' }
  ]
};

const catalogEntry: NavEntry = {
  id: 'catalog',
  label: 'Design catalog',
  icon: 'grid',
  to: '/catalog'
};

/** The SwiftDrop mark for phones, where the sidebar that normally carries it is hidden. */
function MobileBrand({ to }: { to: string }) {
  return (
    <Link
      to={to}
      aria-label="SwiftDrop home"
      className="mr-1 flex shrink-0 items-center md:hidden"
    >
      <BrandMark size={32} />
    </Link>
  );
}

export function AppShell() {
  const role = useAuthStore((state) => state.role);
  const user = useAuthStore((state) => state.user);
  const signOut = useAuthStore((state) => state.signOut);
  const isDevSession = useAuthStore((state) => state.isDevSession);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const isWide = useMediaQuery('(min-width: 1280px)');

  const shellRole = shellRoleFor(role, user?.role);
  const spec = roleShellSpec[shellRole];
  const hasTabs = shellRole === 'customer' || shellRole === 'courier';

  const unreadCount = useUnreadNotificationCount();
  useSyncCurrentUser();
  const avatarUrl = useMyAvatarUrl();
  // Every workspace ends with its Notifications page, with the unread count.
  const notificationsEntry = useMemo<NavEntry>(
    () => ({
      id: 'notifications',
      label: 'Notifications',
      shortLabel: 'Alerts',
      icon: 'bell',
      to: NOTIFICATIONS_PATH[role],
      count: unreadCount > 0 ? unreadCount : undefined
    }),
    [role, unreadCount]
  );

  const tabs = useMemo<NavEntry[]>(
    () => [...routedNav[shellRole], notificationsEntry],
    [shellRole, notificationsEntry]
  );
  // The design catalog is a developer tool, so real users never see it in their menu.
  const entries = import.meta.env.DEV && isDevSession ? [...tabs, catalogEntry] : tabs;
  const activeEntry = entries
    .filter((entry) => entry.to && (pathname === entry.to || pathname.startsWith(`${entry.to}/`)))
    .sort((left, right) => (right.to?.length ?? 0) - (left.to?.length ?? 0))[0];
  const profilePath = PROFILE_PATH[role];
  const onProfile = pathname === profilePath;
  const pageTitle = onProfile ? 'Your profile' : activeEntry?.label ?? spec.label;

  const [confirmingSignOut, setConfirmingSignOut] = useState(false);
  const [signingOut, setSigningOut] = useState(false);

  // Sign-out asks first; the dialog's confirm button does the actual logout.
  const handleSignOut = () => setConfirmingSignOut(true);

  const confirmSignOut = () => {
    setSigningOut(true);
    void (async () => {
      // While the token is still valid: stop lock-screen notifications on this device, so the next
      // person on a shared phone never gets this user's alerts. Never throws, waits at most 3 s.
      await forgetDeviceOnSignOut();
      try {
        // Revokes the refresh token and blacklists the access token on the server.
        await logoutUser();
      } catch {
        // Already expired or the API is unreachable: the local session is still cleared below.
      } finally {
        leaveToLogin(signOut, navigate, 'You have been signed out.');
      }
    })();
  };

  return (
    <div className="flex min-h-screen bg-canvas text-primary">
      <div className={hasTabs ? 'hidden md:block' : undefined}>
        <SideNav
          role={shellRole}
          items={entries}
          workspace={spec.label}
          activeId={onProfile ? undefined : activeEntry?.id}
          rail={!isWide}
          account={{
            name: user ? displayName(user) : spec.label,
            email: user?.email,
            onOpen: () => navigate(profilePath),
            active: onProfile,
            avatarUrl
          }}
          onSignOut={handleSignOut}
          renderItem={(entry, active, rail) => (
            <NavItem
              as={Link}
              to={entry.to ?? pathname}
              icon={entry.icon}
              label={entry.label}
              count={entry.count}
              active={active}
              rail={rail}
              className={shellRole === 'courier' ? 'min-h-target' : undefined}
            />
          )}
        />
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar
          role={shellRole}
          section={spec.label}
          page={pageTitle}
          showUtilities={role !== 'ADMIN_OPS'}
          // Nothing handles search or language yet, and the customer assistant has its own button,
          // so none of them are shown rather than left as controls that do nothing.
          showSearch={false}
          showLanguage={false}
          showAssistant={false}
          district={
            shellRole === 'dispatcher' && user?.district ? districtLabel(user.district) : undefined
          }
          // Notifications live in the left navigation; an empty slot hides the top bar's default bell.
          notificationSlot={<></>}
          leading={hasTabs ? <MobileBrand to={roleHome[role]} /> : undefined}
          trailing={
            // Phones hide the sidebar and its account card, so the menu carries profile and sign-out.
            hasTabs ? (
              <AccountMenu
                className="md:hidden"
                avatarUrl={avatarUrl}
                name={user ? displayName(user) : 'Account'}
                email={user?.email}
                onSignOut={handleSignOut}
                items={[
                  {
                    id: 'profile',
                    label: 'Profile and security',
                    icon: <UserRound aria-hidden="true" className="size-4" />,
                    render: ({ className, onClick, children }) => (
                      <Link
                        to={profilePath}
                        role="menuitem"
                        className={className}
                        onClick={onClick}
                      >
                        {children}
                      </Link>
                    )
                  }
                ]}
              />
            ) : undefined
          }
        >
          {/* The customer assistant lives in the header; it renders nothing for other roles. */}
          <AiAssistantSlot />
        </TopBar>
        <main className={hasTabs ? 'flex-1 pb-[68px] md:pb-0' : 'flex-1'}>
          <Outlet />
        </main>
      </div>

      {hasTabs ? (
        <BottomNav
          role={shellRole}
          tabs={tabs}
          activeId={onProfile ? undefined : activeEntry?.id}
          renderTab={(entry, active, content, tabClassName) => (
            <Link
              to={entry.to ?? pathname}
              aria-current={active ? 'page' : undefined}
              className={tabClassName}
            >
              {content}
            </Link>
          )}
        />
      ) : null}

      <SignOutDialog
        open={confirmingSignOut}
        accountLabel={user?.email ?? user?.username}
        signingOut={signingOut}
        onCancel={() => setConfirmingSignOut(false)}
        onConfirm={confirmSignOut}
      />
    </div>
  );
}

export default AppShell;
