import { useMemo, useState } from 'react';
import { LogOut, Package, UserRound } from 'lucide-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  BottomNav,
  IconButton,
  NavItem,
  SideNav,
  TopBar,
  roleShellSpec,
  useMediaQuery,
  type NavEntry,
  type NavGroup,
  type ShellRole
} from '@/design-system';
import { SignOutDialog } from '@/features/auth/components/SignOutDialog';
import { NOTIFICATIONS_PATH } from '@/features/notifications/notificationTypes';
import { PROFILE_PATH, displayName } from '@/features/profile/profileFormat';
import { useUnreadNotificationCount } from '@/features/notifications/useNotificationInbox';
import { AiAssistantSlot } from '@/design-system/components/AiAssistantDock/AiAssistantSlot';
import { useAuthStore } from '@/store/useAuthStore';
import { logoutUser } from '@/services/api/authApi';
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
 * Menu items that have a route today, placed in the Figma groups. The full Figma menus live in
 * `roleShellSpec`; pages missing from the router are left out until they exist.
 */
const routedNav: Record<ShellRole, NavGroup[]> = {
  customer: [
    {
      label: 'Send & track',
      items: [
        { id: 'book', label: 'Book a parcel', icon: 'plus', to: '/customer' },
        { id: 'parcels', label: 'My parcels', icon: 'package', to: '/customer/orders' },
        { id: 'track', label: 'Track a parcel', icon: 'route', to: '/customer/tracking' }
      ]
    }
  ],
  courier: [
    {
      label: 'Today',
      items: [
        { id: 'shift', label: 'Shift', icon: 'home', to: '/courier' },
        { id: 'delivery', label: 'Active delivery', icon: 'truck', to: '/courier/delivery' }
      ]
    }
  ],
  merchant: [
    {
      label: 'Shipping',
      items: [{ id: 'portal', label: 'Dashboard', icon: 'grid', to: '/merchant' }]
    },
    {
      label: 'Developers',
      items: [{ id: 'webhooks', label: 'Webhook logs', icon: 'webhook', to: '/merchant/webhooks' }]
    }
  ],
  dispatcher: [
    {
      label: 'Operations',
      items: [
        { id: 'tower', label: 'Control tower', icon: 'map', to: '/ops' },
        { id: 'analytics', label: 'Analytics', icon: 'grid', to: '/ops/analytics' },
        { id: 'zones', label: 'Zone pricing', icon: 'map', to: '/ops/zones' }
      ]
    },
    {
      label: 'Platform',
      items: [{ id: 'dlq', label: 'Webhooks DLQ', icon: 'webhook', to: '/ops/webhooks' }]
    }
  ],
  'system-admin': [
    {
      label: 'Platform',
      items: [
        { id: 'tower', label: 'Control tower', icon: 'map', to: '/ops' },
        { id: 'analytics', label: 'Analytics', icon: 'grid', to: '/ops/analytics' },
        { id: 'zones', label: 'Zone pricing', icon: 'map', to: '/ops/zones' },
        { id: 'users', label: 'User management', icon: 'users', to: '/ops/users' },
        { id: 'dlq', label: 'Webhooks DLQ', icon: 'webhook', to: '/ops/webhooks' }
      ]
    }
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
      className="mr-1 flex shrink-0 items-center gap-2 md:hidden"
    >
      <span
        aria-hidden="true"
        className="flex size-8 items-center justify-center rounded-lg bg-brand-primary text-brand-on"
      >
        <Package className="size-[18px]" />
      </span>
      <span className="text-body-strong text-primary">SwiftDrop</span>
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
  // Every workspace has a Notifications page in its own "Updates" section, with the unread count.
  const notificationsEntry = useMemo<NavEntry>(
    () => ({
      id: 'notifications',
      label: 'Notifications',
      icon: 'bell',
      to: NOTIFICATIONS_PATH[role],
      count: unreadCount > 0 ? unreadCount : undefined
    }),
    [role, unreadCount]
  );

  const groups = useMemo<NavGroup[]>(() => {
    const withUpdates = [...routedNav[shellRole], { label: 'Updates', items: [notificationsEntry] }];
    // The design catalog is a developer tool, so real users never see it in their menu.
    return import.meta.env.DEV && isDevSession
      ? [...withUpdates, { label: 'Design system', items: [catalogEntry] }]
      : withUpdates;
  }, [shellRole, isDevSession, notificationsEntry]);
  const entries = groups.flatMap((group) => group.items);
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
      try {
        // Revokes the refresh token and blacklists the access token on the server.
        await logoutUser();
      } catch {
        // Already expired or the API is unreachable: the local session is still cleared below.
      } finally {
        signOut();
        navigate('/login', {
          replace: true,
          state: { notice: 'You have been signed out.' }
        });
      }
    })();
  };

  return (
    <div className="flex min-h-screen bg-canvas text-primary">
      <div className={hasTabs ? 'hidden md:block' : undefined}>
        <SideNav
          role={shellRole}
          groups={groups}
          workspace={spec.label}
          activeId={onProfile ? undefined : activeEntry?.id}
          rail={!isWide}
          account={{
            name: user ? displayName(user) : spec.label,
            email: user?.email,
            onOpen: () => navigate(profilePath),
            active: onProfile
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
              className={
                role === 'ADMIN_OPS'
                  ? `h-11 rounded-xl text-sm ${active ? 'bg-brand-primary text-brand-on shadow-sm hover:bg-brand-hover' : 'text-secondary hover:bg-brand-subtle hover:text-brand-primary'}`
                  : undefined
              }
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
          district={shellRole === 'dispatcher' ? user?.district : undefined}
          // Notifications live in the left navigation; an empty slot hides the top bar's default bell.
          notificationSlot={<></>}
          leading={hasTabs ? <MobileBrand to={roleHome[role]} /> : undefined}
        >
          {hasTabs ? (
            // Phones hide the sidebar (and its account footer), so the profile and sign-out live in the top bar there.
            <IconButton
              variant="ghost"
              density="compact"
              aria-label="Your profile"
              title="Your profile"
              icon={<UserRound aria-hidden="true" />}
              onClick={() => navigate(profilePath)}
              className="size-9 text-secondary md:hidden"
            />
          ) : null}
          {hasTabs ? (
            <IconButton
              variant="ghost"
              density="compact"
              aria-label="Sign out"
              title="Sign out"
              icon={<LogOut aria-hidden="true" />}
              onClick={handleSignOut}
              className="size-9 text-secondary md:hidden"
            />
          ) : null}
        </TopBar>
        <main className={hasTabs ? 'flex-1 pb-[68px] md:pb-0' : 'flex-1'}>
          <Outlet />
        </main>
      </div>

      {hasTabs ? (
        <BottomNav
          role={shellRole}
          tabs={[...groups[0].items, notificationsEntry]}
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

      <div className="fixed bottom-6 right-6 z-40">
        <AiAssistantSlot />
      </div>

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
