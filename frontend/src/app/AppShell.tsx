import { useMemo } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  BottomNav,
  NavItem,
  SideNav,
  TopBar,
  roleShellSpec,
  useMediaQuery,
  type NavEntry,
  type NavGroup,
  type ShellRole
} from '@/design-system';
import { PersonaSwitcherDevBar } from '@/features/auth/components/PersonaSwitcherDevBar';
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
      items: [{ id: 'tower', label: 'Control tower', icon: 'map', to: '/ops' }]
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

export function AppShell() {
  const role = useAuthStore((state) => state.role);
  const user = useAuthStore((state) => state.user);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const signOut = useAuthStore((state) => state.signOut);
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const isWide = useMediaQuery('(min-width: 1280px)');

  const shellRole = shellRoleFor(role, user?.role);
  const spec = roleShellSpec[shellRole];
  const hasTabs = shellRole === 'customer' || shellRole === 'courier';

  const groups = useMemo<NavGroup[]>(
    () => [...routedNav[shellRole], { label: 'Design system', items: [catalogEntry] }],
    [shellRole]
  );
  const entries = groups.flatMap((group) => group.items);
  const activeEntry = entries.find((entry) => entry.to === pathname);
  const pageTitle = activeEntry?.label ?? spec.label;

  const handleSignOut = () => {
    void (async () => {
      try {
        await logoutUser(refreshToken);
      } finally {
        signOut();
        navigate('/login', { replace: true });
      }
    })();
  };

  return (
    <div className="flex min-h-screen bg-canvas text-primary">
      <div className={hasTabs ? 'hidden md:block' : undefined}>
        <SideNav
          role={shellRole}
          groups={groups}
          workspace={user?.district ? `${spec.label} · ${user.district}` : undefined}
          activeId={activeEntry?.id}
          rail={!isWide}
          account={{ name: user?.username ?? spec.label, email: user?.email }}
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
            />
          )}
        />
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <PersonaSwitcherDevBar onSwitch={(nextRole) => navigate(roleHome[nextRole])} />
        <TopBar
          role={shellRole}
          section={spec.label}
          page={pageTitle}
          district={shellRole === 'dispatcher' ? user?.district : undefined}
        />
        <main className={hasTabs ? 'flex-1 pb-[68px] md:pb-0' : 'flex-1'}>
          <Outlet />
        </main>
      </div>

      {hasTabs ? (
        <BottomNav
          role={shellRole}
          tabs={groups[0].items}
          activeId={activeEntry?.id}
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
    </div>
  );
}

export default AppShell;
