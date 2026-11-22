import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { navIcons } from './navIcons';
import { roleShellSpec, type NavEntry, type ShellRole } from './roleNav';

export interface BottomNavProps {
  /** `customer` (Book, Parcels, Track, Profile) or `courier` (Today, Scan, Cash, Profile). */
  role: Extract<ShellRole, 'customer' | 'courier'>;
  tabs?: NavEntry[];
  activeId?: string;
  /** Render each tab yourself, e.g. with a router link. Receives the tab content to wrap. */
  renderTab?: (
    entry: NavEntry,
    active: boolean,
    content: ReactNode,
    tabClassName: string
  ) => ReactNode;
  className?: string;
}

/** BottomNav: 68px mobile tab bar with an icon pill for the active tab and short labels. Hidden from `md` up. */
export function BottomNav({ role, tabs, activeId, renderTab, className }: BottomNavProps) {
  const items = tabs ?? roleShellSpec[role].tabs ?? [];

  return (
    <nav
      aria-label={`${roleShellSpec[role].label} tabs`}
      className={cn(
        'fixed inset-x-0 bottom-0 z-30 flex h-[68px] items-center border-t border-border-strong bg-card px-2 py-1.5 shadow-bar-up md:hidden',
        className
      )}
    >
      {items.map((entry) => {
        const active = entry.id === activeId;
        const Icon = navIcons[entry.icon];
        const content = (
          <>
            <span
              className={cn(
                'relative flex h-[30px] w-[54px] items-center justify-center rounded-full',
                active && 'bg-brand-primary text-brand-on'
              )}
            >
              <Icon aria-hidden="true" className="size-[22px]" />
              {entry.count !== undefined ? (
                <span
                  aria-hidden="true"
                  className="absolute right-3 top-0.5 size-2.5 rounded-full border-2 border-card bg-status-danger"
                />
              ) : null}
            </span>
            <span className={cn('max-w-full truncate text-caption', active && 'font-semibold')}>
              {entry.shortLabel ?? entry.label}
              {entry.count !== undefined ? (
                <span className="sr-only">, {entry.count} unread</span>
              ) : null}
            </span>
          </>
        );
        const tabClassName = cn(
          'flex min-h-target min-w-0 flex-1 flex-col items-center justify-center gap-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus',
          active ? 'text-brand-pressed' : 'text-secondary'
        );
        return renderTab ? (
          <div key={entry.id} className="flex min-w-0 flex-1">
            {renderTab(entry, active, content, tabClassName)}
          </div>
        ) : (
          <a
            key={entry.id}
            href={entry.to ?? '#'}
            aria-current={active ? 'page' : undefined}
            className={tabClassName}
          >
            {content}
          </a>
        );
      })}
    </nav>
  );
}

export default BottomNav;
