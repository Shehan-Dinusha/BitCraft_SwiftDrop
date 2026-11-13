import { Bell } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { notificationApi } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';

export function NotificationBell() {
  const token = useAuthStore((state) => state.token);
  const isDevSession = useAuthStore((state) => state.isDevSession);

  const { data: unreadCount = 0 } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.getUnreadCount,
    enabled: !!token && !isDevSession,
    refetchInterval: 20_000, // 20s polling as required by Member 9 spec
    staleTime: 10_000,
    retry: false
  });

  const hasUnread = unreadCount > 0;

  return (
    <div className="relative inline-block">
      <button
        type="button"
        aria-label={hasUnread ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        className="relative inline-flex size-9 items-center justify-center rounded-lg text-secondary transition-colors hover:bg-elevated hover:text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
      >
        <Bell aria-hidden="true" className="size-[18px]" />
        {hasUnread ? (
          <span
            aria-hidden="true"
            className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-status-danger px-1 text-[10px] font-semibold text-white shadow-sm"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        ) : null}
      </button>
    </div>
  );
}

export default NotificationBell;
