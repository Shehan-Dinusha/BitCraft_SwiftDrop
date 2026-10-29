import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Boxes, LogOut } from 'lucide-react';
import { cn } from '@/utils/cn';
import { Button } from '@/design-system';
import { PersonaSwitcherDevBar } from '@/features/auth/components/PersonaSwitcherDevBar';
import { useAuthStore } from '@/store/useAuthStore';
import { logoutUser } from '@/services/api/authApi';
import { roleHome } from '@/app/roleHome';
import type { PersonaRole } from '@/types/contracts';

const roleNav: Record<PersonaRole, Array<{ to: string; label: string }>> = {
  CUSTOMER: [
    { to: '/customer', label: 'Book a delivery' },
    { to: '/customer/tracking', label: 'Track parcel' }
  ],
  COURIER: [
    { to: '/courier', label: 'Shift' },
    { to: '/courier/delivery', label: 'Active delivery' }
  ],
  MERCHANT: [
    { to: '/merchant', label: 'Portal' },
    { to: '/merchant/webhooks', label: 'Webhooks' }
  ],
  ADMIN_OPS: [
    { to: '/ops', label: 'Control tower' },
    { to: '/ops/webhooks', label: 'Webhooks DLQ' }
  ]
};

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'shrink-0 rounded-lg px-3 py-1.5 text-sm font-semibold transition-colors',
    isActive
      ? 'bg-brand-subtle text-brand-primary'
      : 'text-secondary hover:bg-elevated hover:text-primary'
  );

export function AppShell() {
  const role = useAuthStore((state) => state.role);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const signOut = useAuthStore((state) => state.signOut);
  const navigate = useNavigate();
  const links = roleNav[role];

  return (
    <div className="flex min-h-screen flex-col bg-canvas text-primary">
      <PersonaSwitcherDevBar onSwitch={(nextRole) => navigate(roleHome[nextRole])} />

      <header className="sticky top-0 z-40 border-b border-border-subtle bg-card">
        <div className="mx-auto flex max-w-7xl items-center gap-4 px-4 py-2 sm:px-6">
          <Link to={roleHome[role]} className="flex shrink-0 items-center gap-2">
            <span className="grid size-8 place-items-center rounded-lg bg-brand-primary text-brand-on">
              <Boxes className="size-4" aria-hidden="true" />
            </span>
            <span className="hidden text-sm font-bold tracking-tight text-primary sm:inline">
              SwiftDrop <span className="text-brand-primary">Velocity</span>
            </span>
          </Link>

          <nav
            aria-label="Primary"
            className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto"
          >
            {links.map((link) => (
              <NavLink key={link.to} to={link.to} end className={navLinkClass}>
                {link.label}
              </NavLink>
            ))}
            <NavLink to="/catalog" className={navLinkClass}>
              Catalog
            </NavLink>
          </nav>

          <div className="flex shrink-0 items-center gap-1">
            <Button
              variant="ghost"
              className="text-secondary"
              leftIcon={<LogOut />}
              aria-label="Sign out"
              onClick={() => {
                void (async () => {
                  try {
                    await logoutUser(refreshToken);
                  } finally {
                    signOut();
                    navigate('/login', { replace: true });
                  }
                })();
              }}
            >
              <span className="hidden sm:inline">Sign out</span>
            </Button>
          </div>
        </div>
      </header>

      <div className="flex-1">
        <Outlet />
      </div>
    </div>
  );
}

export default AppShell;
