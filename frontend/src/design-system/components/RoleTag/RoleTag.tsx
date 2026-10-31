import { cn } from '@/utils/cn';

export type RoleTagRole = 'customer' | 'merchant' | 'courier' | 'dispatcher' | 'system-admin';
export type RoleTagStyle = 'outline' | 'solid';

export interface RoleTagProps {
  role: RoleTagRole;
  /** `outline` shows a coloured dot; `solid` fills with the role accent. */
  variant?: RoleTagStyle;
  className?: string;
}

const roles: Record<RoleTagRole, { label: string; dot: string; solid: string }> = {
  customer: { label: 'Customer', dot: 'bg-role-customer', solid: 'bg-role-customer' },
  merchant: { label: 'Merchant', dot: 'bg-role-merchant', solid: 'bg-role-merchant' },
  courier: { label: 'Courier', dot: 'bg-role-courier', solid: 'bg-role-courier' },
  dispatcher: { label: 'Dispatcher', dot: 'bg-role-dispatcher', solid: 'bg-role-dispatcher' },
  'system-admin': {
    label: 'System Admin',
    dot: 'bg-role-system-admin',
    solid: 'bg-role-system-admin'
  }
};

/** Figma RoleTag: one of the five role accents, as an outlined tag with a dot or a solid pill. */
export function RoleTag({ role, variant = 'outline', className }: RoleTagProps) {
  const config = roles[role];
  return (
    <span
      data-role={role}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md px-2 py-[3px] text-label',
        variant === 'solid'
          ? cn(config.solid, 'text-brand-on')
          : 'border border-border-subtle bg-card text-primary',
        className
      )}
    >
      {variant === 'outline' ? (
        <span aria-hidden="true" className={cn('size-2 rounded-full', config.dot)} />
      ) : null}
      {config.label}
    </span>
  );
}

export default RoleTag;
