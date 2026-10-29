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

/** Figma NavItem: 36px row with icon, label and optional count. State = Default / Hover / Active. */
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
        'flex h-9 items-center gap-2.5 rounded-lg px-3 text-label transition-colors',
        active
          ? 'bg-brand-subtle font-semibold text-brand-pressed'
          : 'text-primary hover:bg-elevated',
        'focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-focus',
        rail && 'justify-center px-0',
        className
      )}
    >
      <Icon aria-hidden="true" className="size-[18px] shrink-0" />
      <span className={cn('min-w-0 flex-1 truncate', rail && 'sr-only')}>{label}</span>
      {count !== undefined && !rail ? (
        <span
          className={cn(
            'rounded-full px-[7px] py-px text-caption tabular-nums',
            active ? 'bg-brand-primary text-brand-on' : 'bg-elevated text-secondary'
          )}
        >
          {count}
        </span>
      ) : null}
    </Component>
  );
}

export default NavItem;
