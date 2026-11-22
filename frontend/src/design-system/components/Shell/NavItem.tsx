import type { ComponentPropsWithoutRef, ElementType } from 'react';
import { cn } from '@/utils/cn';
import { navIcons, type NavIconName } from './navIcons';

export type NavItemProps<T extends ElementType = 'a'> = {
  icon: NavIconName;
  label: string;
  /** Count badge shown after the label. */
  count?: number;
  /** Active state (Figma State = Active). Also sets `aria-current="page"`. */
  active?: boolean;
  /** Icon-only rail: the label becomes a visually hidden name and a tooltip. */
  rail?: boolean;
  /** Render as another element or router link, e.g. `as={Link}`. */
  as?: T;
  className?: string;
} & Omit<ComponentPropsWithoutRef<T>, 'className' | 'children'>;

/** NavItem: 44px row with icon, label and optional count. State = Default / Hover / Active. */
export function NavItem<T extends ElementType = 'a'>({
  icon,
  label,
  count,
  active = false,
  rail = false,
  as,
  className,
  ...props
}: NavItemProps<T>) {
  const Component: ElementType = as ?? 'a';
  const Icon = navIcons[icon];

  return (
    <Component
      {...props}
      aria-current={active ? 'page' : undefined}
      title={rail ? label : undefined}
      className={cn(
        'flex h-11 items-center gap-3 rounded-lg px-3 transition-colors',
        active
          ? 'bg-brand-primary text-brand-on shadow-raised hover:bg-brand-hover'
          : 'text-primary hover:bg-card hover:shadow-raised',
        'focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-focus',
        rail && 'justify-center px-0',
        className
      )}
    >
      {/* On the rail the count has no room, so a dot on the icon says there is something new. */}
      <span className="relative shrink-0">
        <Icon aria-hidden="true" className="size-[18px]" />
        {count !== undefined && rail ? (
          <span
            aria-hidden="true"
            className="absolute -right-1 -top-1 size-2 rounded-full bg-status-danger"
          />
        ) : null}
      </span>
      <span
        className={cn(
          'min-w-0 flex-1 truncate text-body',
          active ? 'font-semibold' : 'font-medium',
          rail && 'sr-only'
        )}
      >
        {label}
      </span>
      {count !== undefined && !rail ? (
        <span
          className={cn(
            'rounded-full px-[7px] py-px tabular-nums',
            active ? 'bg-card text-brand-primary' : 'bg-brand-primary text-brand-on'
          )}
        >
          <span className="block text-caption font-semibold">{count}</span>
        </span>
      ) : null}
    </Component>
  );
}

export default NavItem;
