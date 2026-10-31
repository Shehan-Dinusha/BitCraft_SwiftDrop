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

/** Figma BottomNav: 68px mobile tab bar with an icon pill for the active tab. Hidden from `md` up. */
export function BottomNav({ role, tabs, activeId, renderTab, className }: BottomNavProps) {
  const items = tabs ?? roleShellSpec[role].tabs ?? [];

  return (
    <nav
      aria-label={`${roleShellSpec[role].label} tabs`}
      className={cn(
        'fixed inset-x-0 bottom-0 z-30 flex h-[68px] items-center border-t border-border-subtle bg-card px-2 py-1.5 md:hidden',
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
                'flex h-[30px] w-[54px] items-center justify-center rounded-full',
                active && 'bg-brand-subtle text-brand-pressed'
              )}
            >
              <Icon aria-hidden="true" className="size-[22px]" />
            </span>
            <span
              className={cn(
                'text-caption',
                active ? 'font-semibold text-brand-pressed' : 'text-secondary'
              )}
            >
              {entry.label}
            </span>
          </>
        );
        const tabClassName =
          'flex flex-1 flex-col items-center gap-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus';
        return renderTab ? (
          <div key={entry.id} className="flex flex-1">
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
