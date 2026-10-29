import { ChevronDown, LogOut, Package } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { Button } from '@/design-system/primitives/Button';
import { navIcons } from './navIcons';
import { NavItem } from './NavItem';
import { roleShellSpec, type NavEntry, type NavGroup, type ShellRole } from './roleNav';

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
}

export interface SideNavProps {
  role: ShellRole;
  /** Defaults to the role's menu from the Figma spec. */
  groups?: NavGroup[];
  /** Overrides the workspace line ("Personal account", a merchant name, a district…). */
  workspace?: string;
  /** Primary action under the switcher. Defaults to the role's CTA label when `onCta` is given. */
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

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

/** Figma SideNav: 256px (72px rail) with workspace switcher, primary action, grouped menu and account footer. */
export function SideNav({
  role,
  groups,
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
  const menu = groups ?? spec.groups;
  const lastGroup = menu[menu.length - 1];
  const bodyGroups = lastGroup && !lastGroup.label && menu.length > 1 ? menu.slice(0, -1) : menu;
  const footerGroup = bodyGroups === menu ? undefined : lastGroup;

  const renderEntry = (entry: NavEntry) => {
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
  };

  return (
    <aside
      aria-label={`${spec.label} navigation`}
      className={cn(
        'sticky top-0 flex h-screen shrink-0 flex-col gap-0.5 border-r border-border-subtle bg-card px-3 pb-3 pt-3.5',
        rail ? 'w-[72px]' : 'w-64',
        className
      )}
    >
      <div
        className={cn('flex items-center gap-2.5 rounded-[10px] p-2', rail && 'justify-center p-1')}
      >
        <span
          aria-hidden="true"
          className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-brand-primary text-brand-on"
        >
          <Package className="size-[18px]" />
        </span>
        {rail ? null : (
          <>
            <div className="flex min-w-0 flex-1 flex-col">
              <span className="text-body-strong text-primary">SwiftDrop</span>
              <span className="flex items-center gap-1.5 text-caption text-secondary">
                <span aria-hidden="true" className={cn('size-1.5 rounded-full', roleDot[role])} />
                <span className="truncate">{workspace ?? spec.workspace}</span>
              </span>
            </div>
            <ChevronDown aria-hidden="true" className="size-3.5 shrink-0 text-secondary" />
          </>
        )}
      </div>

      {cta ? (
        <div className="py-1.5 pr-0">
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
        className="flex min-h-0 flex-1 flex-col overflow-y-auto"
      >
        {bodyGroups.map((group, index) => (
          <div key={group.label ?? index} className="flex flex-col">
            {group.label && !rail ? (
              <p className="px-3 pb-1 pt-3 text-overline uppercase text-muted">{group.label}</p>
            ) : (
              <div className={rail && index > 0 ? 'my-2 h-px bg-border-subtle' : undefined} />
            )}
            <ul className="flex flex-col gap-0.5">{group.items.map(renderEntry)}</ul>
          </div>
        ))}
        {footerGroup ? (
          <ul className="mt-auto flex flex-col gap-0.5 pt-2">
            {footerGroup.items.map(renderEntry)}
          </ul>
        ) : null}
      </nav>

      {account ? (
        <div className="mt-1 border-t border-border-subtle pt-2.5">
          <div className={cn('flex items-center gap-2.5 px-2', rail && 'flex-col px-0')}>
            <span
              aria-hidden="true"
              className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-subtle text-caption text-brand-primary"
            >
              {initials(account.name)}
            </span>
            {rail ? null : (
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-body-strong text-primary">{account.name}</span>
                {account.email ? (
                  <span className="truncate text-caption text-muted">{account.email}</span>
                ) : null}
              </div>
            )}
            {onSignOut ? (
              <button
                type="button"
                onClick={onSignOut}
                aria-label="Sign out"
                title="Sign out"
                className="inline-flex size-8 shrink-0 items-center justify-center rounded-lg text-secondary hover:bg-elevated hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
              >
                <LogOut aria-hidden="true" className="size-4" />
              </button>
            ) : null}
          </div>
        </div>
      ) : null}
    </aside>
  );
}

export default SideNav;
