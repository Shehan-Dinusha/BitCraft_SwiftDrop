import { Truck } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import type { PublicOrderStatus } from '@/types/contracts';
import { publicStatusColors } from '@/design-system/tokens/colors';

export interface NotificationData {
  id: string;
  title: string;
  /** e.g. `SD-7K4M-9QX2 · Arriving today in Colombo`. */
  message: string;
  /** Relative time, e.g. `2 min ago`. */
  time: string;
  unread?: boolean;
  /** Status whose colour tints the icon circle. Defaults to Out for Delivery. */
  status?: PublicOrderStatus;
  icon?: ReactNode;
}

export interface NotificationItemProps extends NotificationData {
  onClick?: () => void;
  className?: string;
}

/** Figma NotificationItem: State = Unread (brand tint and dot) / Read. */
export function NotificationItem({
  title,
  message,
  time,
  unread = false,
  status = 'OUT_FOR_DELIVERY',
  icon,
  onClick,
  className
}: NotificationItemProps) {
  const tone = publicStatusColors[status];
  const Wrapper = onClick ? 'button' : 'div';

  return (
    <Wrapper
      {...(onClick ? { type: 'button' as const, onClick } : {})}
      data-unread={unread || undefined}
      className={cn(
        'flex w-full items-start gap-3 border-b border-border-subtle px-4 py-3 text-left',
        unread ? 'bg-brand-subtle' : 'bg-card',
        onClick &&
          'transition-colors hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-focus',
        className
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full [&_svg]:size-4',
          tone.background,
          tone.foreground
        )}
      >
        {icon ?? <Truck />}
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-body-strong text-primary">{title}</span>
        <span className="text-caption text-secondary">{message}</span>
        <span className="text-caption text-muted">{time}</span>
      </span>
      {unread ? (
        <>
          <span
            aria-hidden="true"
            className="mt-1.5 size-2 shrink-0 rounded-full bg-brand-primary"
          />
          <span className="sr-only">Unread</span>
        </>
      ) : null}
    </Wrapper>
  );
}

export interface NotificationTrayProps {
  items: NotificationData[];
  onItemClick?: (id: string) => void;
  onMarkAllRead?: () => void;
  onViewAll?: () => void;
  className?: string;
}

/** Figma NotificationTray: popover card with a "Mark all as read" header and a "View all" footer. */
export function NotificationTray({
  items,
  onItemClick,
  onMarkAllRead,
  onViewAll,
  className
}: NotificationTrayProps) {
  return (
    <section
      aria-label="Notifications"
      className={cn(
        'w-full max-w-[382px] overflow-hidden rounded-xl border border-border-subtle bg-card shadow-ambient',
        className
      )}
    >
      <header className="flex items-center justify-between gap-2 border-b border-border-subtle px-4 py-3.5">
        <h2 className="text-h3 text-primary">Notifications</h2>
        {onMarkAllRead ? (
          <button
            type="button"
            onClick={onMarkAllRead}
            className="text-label text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
          >
            Mark all as read
          </button>
        ) : null}
      </header>
      <div>
        {items.map((item) => (
          <NotificationItem
            key={item.id}
            {...item}
            onClick={onItemClick ? () => onItemClick(item.id) : undefined}
          />
        ))}
      </div>
      {onViewAll ? (
        <footer className="px-4 py-3">
          <button
            type="button"
            onClick={onViewAll}
            className="text-label text-brand-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
          >
            View all notifications
          </button>
        </footer>
      ) : null}
    </section>
  );
}

export default NotificationTray;
