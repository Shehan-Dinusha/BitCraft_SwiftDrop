import { useState } from 'react';
import { CheckCheck, Inbox } from 'lucide-react';
import { cn } from '@/utils/cn';
import { notificationMeta, notificationTone, type NotificationType } from './notificationTypes';

export interface TrayItem {
  id: number;
  type: NotificationType | null;
  /** Heading shown for the item (the type label, or the stored title for unknown types). */
  title: string;
  /** Message text. Already contains the tracking number, so it is not repeated separately. */
  message: string;
  createdAt?: string;
  unread: boolean;
  trackingNumber?: string;
}

export interface NotificationTrayProps {
  items: TrayItem[];
  unreadCount: number;
  loading?: boolean;
  onItemClick: (item: TrayItem) => void;
  onMarkAllRead: () => void;
}

type Filter = 'all' | 'unread';

function relativeTime(dateString?: string): string {
  if (!dateString) return '';
  const seconds = Math.floor((Date.now() - new Date(dateString).getTime()) / 1000);
  if (seconds < 60) return 'Just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return 'Yesterday';
  if (days < 7) return `${days}d ago`;
  return new Date(dateString).toLocaleDateString();
}

function isToday(dateString?: string): boolean {
  if (!dateString) return false;
  return new Date(dateString).toDateString() === new Date().toDateString();
}

/** The inbox: All/Unread filter, "Mark all as read" and the notifications grouped by day. */
export function NotificationTray({
  items,
  unreadCount,
  loading = false,
  onItemClick,
  onMarkAllRead
}: NotificationTrayProps) {
  const [filter, setFilter] = useState<Filter>('all');
  const visible = filter === 'unread' ? items.filter((i) => i.unread) : items;
  const today = visible.filter((i) => isToday(i.createdAt));
  const earlier = visible.filter((i) => !isToday(i.createdAt));

  const renderItem = (item: TrayItem) => {
    const { Icon } = notificationMeta(item.type);
    return (
      <li key={item.id}>
        <button
          type="button"
          onClick={() => onItemClick(item)}
          className={cn(
            'group flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-border-focus',
            item.unread && 'bg-brand-subtle/60'
          )}
        >
          <span
            aria-hidden="true"
            className={cn(
              'flex size-9 shrink-0 items-center justify-center rounded-full ring-4 ring-card [&_svg]:size-[18px]',
              notificationTone(item.type)
            )}
          >
            <Icon strokeWidth={2} />
          </span>
          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="flex items-baseline justify-between gap-2">
              <span
                className={cn(
                  'truncate text-body-strong',
                  item.unread ? 'text-primary' : 'text-secondary'
                )}
              >
                {item.title}
              </span>
              <span className="shrink-0 text-caption text-muted">
                {relativeTime(item.createdAt)}
              </span>
            </span>
            <span className="line-clamp-2 text-caption text-secondary">{item.message}</span>
          </span>
          {item.unread ? (
            <>
              <span
                aria-hidden="true"
                className="mt-2 size-2 shrink-0 rounded-full bg-brand-primary"
              />
              <span className="sr-only">Unread</span>
            </>
          ) : null}
        </button>
      </li>
    );
  };

  const group = (label: string, list: TrayItem[]) =>
    list.length === 0 ? null : (
      <div>
        <p className="px-4 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-muted">
          {label}
        </p>
        <ul className="divide-y divide-border-subtle">{list.map(renderItem)}</ul>
      </div>
    );

  return (
    <section
      aria-label="Notifications"
      className="w-full overflow-hidden rounded-2xl border border-border-subtle bg-card"
    >
      <header className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
        <div role="tablist" aria-label="Filter notifications" className="flex gap-1">
          {(['all', 'unread'] as const).map((f) => (
            <button
              key={f}
              type="button"
              role="tab"
              aria-selected={filter === f}
              onClick={() => setFilter(f)}
              className={cn(
                'rounded-full px-3 py-1 text-label transition-colors',
                filter === f
                  ? 'bg-brand-subtle text-brand-primary'
                  : 'text-secondary hover:bg-elevated hover:text-primary'
              )}
            >
              {f === 'all' ? 'All' : `Unread${unreadCount > 0 ? ` (${unreadCount})` : ''}`}
            </button>
          ))}
        </div>
        {unreadCount > 0 ? (
          <button
            type="button"
            onClick={onMarkAllRead}
            className="inline-flex items-center gap-2 rounded-lg px-2 py-1.5 text-label text-brand-primary transition-colors hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
          >
            <CheckCheck aria-hidden="true" className="size-4" />
            Mark all as read
          </button>
        ) : null}
      </header>

      <div className="border-t border-border-subtle">
        {loading && items.length === 0 ? (
          <p className="p-8 text-center text-caption text-secondary">Loading notifications…</p>
        ) : visible.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 px-6 py-10 text-center">
            <span className="flex size-11 items-center justify-center rounded-full bg-elevated text-muted">
              <Inbox aria-hidden="true" className="size-5" />
            </span>
            <p className="text-body-strong text-primary">
              {filter === 'unread' ? 'No unread notifications' : "You're all caught up"}
            </p>
            <p className="text-caption text-secondary">
              Parcel updates will appear here as they happen.
            </p>
          </div>
        ) : (
          <>
            {group('Today', today)}
            {group('Earlier', earlier)}
          </>
        )}
      </div>
    </section>
  );
}

export default NotificationTray;
