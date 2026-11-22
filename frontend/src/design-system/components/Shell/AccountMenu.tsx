import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { LogOut } from 'lucide-react';
import { cn } from '@/utils/cn';
import { Avatar } from '../Avatar/Avatar';

export interface AccountMenuItem {
  id: string;
  label: string;
  icon?: ReactNode;
  /** Renders the item itself, e.g. as a router link. The menu closes when it is chosen. */
  render?: (props: { className: string; onClick: () => void; children: ReactNode }) => ReactNode;
  onSelect?: () => void;
}

export interface AccountMenuProps {
  name: string;
  email?: string;
  /** Profile picture URL; initials are shown when absent. */
  avatarUrl?: string | null;
  items: AccountMenuItem[];
  onSignOut: () => void;
  className?: string;
}

const itemClass =
  'flex w-full items-center gap-2 rounded-lg px-3 py-2 max-md:min-h-target text-left text-primary transition-colors hover:bg-elevated focus-visible:bg-elevated focus-visible:outline-none';

/**
 * The signed-in person's avatar in the top bar. Opens a small menu with their account pages and
 * Sign out. Closes on Escape, on a click outside and when an item is chosen.
 */
export function AccountMenu({ name, email, avatarUrl, items, onSignOut, className }: AccountMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return undefined;

    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };

    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const close = () => setOpen(false);

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        ref={triggerRef}
        type="button"
        aria-label={`Account menu for ${name}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => setOpen((current) => !current)}
        className="grid size-11 place-items-center rounded-full transition-colors hover:bg-brand-subtle focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus max-md:size-14"
      >
        <Avatar name={name} src={avatarUrl} />
      </button>

      {open ? (
        <div
          id={menuId}
          role="menu"
          aria-label="Account"
          className="absolute right-0 top-full z-50 mt-2 w-64 rounded-xl border border-border-strong bg-card p-1.5 shadow-lg"
        >
          <div className="border-b border-border-strong px-3 pb-2 pt-1.5">
            <p className="truncate text-body-strong text-primary">{name}</p>
            {email ? <p className="truncate text-caption text-muted">{email}</p> : null}
          </div>
          <div className="pt-1.5">
            {items.map((item) => {
              const content = (
                <>
                  {item.icon}
                  <span className="text-body">{item.label}</span>
                </>
              );
              if (item.render) {
                return (
                  <div key={item.id} role="none">
                    {item.render({ className: itemClass, onClick: close, children: content })}
                  </div>
                );
              }
              return (
                <button
                  key={item.id}
                  type="button"
                  role="menuitem"
                  className={itemClass}
                  onClick={() => {
                    close();
                    item.onSelect?.();
                  }}
                >
                  {content}
                </button>
              );
            })}
            <button
              type="button"
              role="menuitem"
              className={cn(itemClass, 'text-status-danger-fg hover:bg-status-danger-bg')}
              onClick={() => {
                close();
                onSignOut();
              }}
            >
              <LogOut aria-hidden="true" className="size-4" />
              <span className="text-body">Sign out</span>
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export default AccountMenu;
