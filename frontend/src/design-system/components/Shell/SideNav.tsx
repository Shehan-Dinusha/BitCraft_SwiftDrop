import { LogOut } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { Button } from '@/design-system/primitives/Button';
import { Avatar } from '../Avatar/Avatar';
import { BrandMark } from '../BrandMark/BrandMark';
import { navIcons } from './navIcons';
import { NavItem } from './NavItem';
import { roleShellSpec, type NavEntry, type ShellRole } from './roleNav';

const roleDot: Record<ShellRole, string> = {
  customer: 'bg-role-customer',
  merchant: 'bg-role-merchant',
  courier: 'bg-role-courier',
  dispatcher: 'bg-role-dispatcher',
  'system-admin': 'bg-role-system-admin'
};

export interface SideNavAccount {
  name: string;
  email?: string;
  /** Opens the user's profile; makes the avatar and name a button. */
  onOpen?: () => void;
  /** The profile page is the current page. */
  active?: boolean;
  /** Profile picture URL; initials are shown when absent. */
  avatarUrl?: string | null;
}

export interface SideNavProps {
  role: ShellRole;
  /** Defaults to the role's menu from the Figma spec. */
  items?: NavEntry[];
  /** Overrides the workspace line ("Personal account", a merchant name, a district…). */
  workspace?: string;
  /** Primary action above the menu. */
  cta?: { label: string; onClick: () => void };
  /** Marks one item as active. */
  activeId?: string;
  /** Render each item yourself, e.g. with a router link. */
  renderItem?: (entry: NavEntry, active: boolean, rail: boolean) => ReactNode;
  account?: SideNavAccount;
  onSignOut?: () => void;
  /** 72px icon rail (used below 1280px). */
  rail?: boolean;
  className?: string;
}

const cardButton =
  'flex w-full items-center transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-focus';

/** The account card at the bottom of the side nav: who is signed in, and Sign out. */
function AccountCard({
  account,
  onSignOut,
  rail
}: {
  account: SideNavAccount;
  onSignOut?: () => void;
  rail: boolean;
}) {
  const identity = (
    <>
      <Avatar name={account.name} src={account.avatarUrl} />
      {rail ? null : (
        <span className="flex min-w-0 flex-1 flex-col text-left">
          <span className="truncate text-body-strong">{account.name}</span>
          {account.email ? (
            <span className="text-secondary">
              <span className="block truncate text-caption">{account.email}</span>
            </span>
          ) : null}
        </span>
      )}
    </>
  );
  const identityClass = cn('gap-3 p-3', rail && 'justify-center px-0');

  return (
    <div
      className={cn(
        'm-3 mt-0 shrink-0 overflow-hidden rounded-xl border border-border-strong bg-card shadow-raised',
        rail && 'mx-2',
        account.active && 'border-border-selected'
      )}
    >
      {account.onOpen ? (
        <button
          type="button"
          onClick={account.onOpen}
          aria-label={`Your profile: ${account.name}`}
          aria-current={account.active ? 'page' : undefined}
          title="Your profile"
          className={cn(
            cardButton,
            identityClass,
            account.active ? 'bg-brand-subtle' : 'hover:bg-elevated'
          )}
        >
          {identity}
        </button>
      ) : (
        <div className={cn('flex items-center', identityClass)}>{identity}</div>
      )}
      {onSignOut ? (
        <button
          type="button"
          onClick={onSignOut}
          aria-label="Sign out"
          title={rail ? 'Sign out' : undefined}
          className={cn(
            cardButton,
            'h-10 justify-center gap-2 border-t border-border-strong text-status-danger-fg hover:bg-status-danger-bg'
          )}
        >
          <LogOut aria-hidden="true" className="size-4" />
          {rail ? null : <span className="text-label">Sign out</span>}
        </button>
      ) : null}
    </div>
  );
}

/**
 * SideNav: 256px (72px rail) tinted panel with the brand strip, one flat menu and the account card.
 * The brand strip is as tall as the TopBar, so the two read as one line across the screen.
 */
export function SideNav({
  role,
  items,
  workspace,
  cta,
  activeId,
  renderItem,
  account,
  onSignOut,
  rail = false,
  className
}: SideNavProps) {
  const spec = roleShellSpec[role];
  const menu = items ?? spec.items;

  return (
    <aside
      aria-label={`${spec.label} navigation`}
      className={cn(
        'sticky top-0 z-30 flex h-screen shrink-0 flex-col border-r border-border-strong bg-nav shadow-nav',
        rail ? 'w-[72px]' : 'w-64',
        className
      )}
    >
      <div
        className={cn(
          'flex h-14 shrink-0 items-center gap-3 border-b border-border-strong bg-card px-5',
          rail && 'justify-center px-0'
        )}
      >
        <BrandMark size={36} />
        {rail ? null : (
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="text-body-strong font-bold">SwiftDrop</span>
            <span className="flex items-center gap-1.5 text-secondary">
              <span aria-hidden="true" className={cn('size-1.5 rounded-full', roleDot[role])} />
              <span className="truncate text-caption">{workspace ?? spec.workspace}</span>
            </span>
          </div>
        )}
      </div>

      {cta ? (
        <div className={cn('px-3 pt-3', rail && 'px-2')}>
          {rail ? (
            <Button
              density="comfortable"
              aria-label={cta.label}
              onClick={cta.onClick}
              className="w-full px-0"
            >
              <navIcons.plus aria-hidden="true" className="size-5" />
            </Button>
          ) : (
            <Button density="comfortable" onClick={cta.onClick} className="w-full">
              + {cta.label}
            </Button>
          )}
        </div>
      ) : null}

      <nav
        aria-label={`${spec.label} menu`}
        className={cn('min-h-0 flex-1 overflow-y-auto p-3', rail && 'px-2')}
      >
        <ul className="flex flex-col gap-1">
          {menu.map((entry) => {
            const active = entry.id === activeId;
            return (
              <li key={entry.id}>
                {renderItem ? (
                  renderItem(entry, active, rail)
                ) : (
                  <NavItem
                    href={entry.to ?? '#'}
                    icon={entry.icon}
                    label={entry.label}
                    count={entry.count}
                    active={active}
                    rail={rail}
                  />
                )}
              </li>
            );
          })}
        </ul>
      </nav>

      {account ? <AccountCard account={account} onSignOut={onSignOut} rail={rail} /> : null}
    </aside>
  );
}

export default SideNav;
