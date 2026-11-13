import { Bell } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { NotificationTray, type NotificationData } from '@/design-system';
import type { PublicOrderStatus } from '@/types/contracts';
import { notificationApi, type InAppNotificationResponse } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';

function inferStatus(title?: string, body?: string): PublicOrderStatus {
  const text = `${title ?? ''} ${body ?? ''}`.toUpperCase();
  if (text.includes('DELIVERED')) return 'DELIVERED';
  if (text.includes('OUT_FOR_DELIVERY') || text.includes('OUT FOR DELIVERY')) return 'OUT_FOR_DELIVERY';
  if (text.includes('PICKED_UP') || text.includes('PICKED UP')) return 'PICKED_UP';
  if (text.includes('FAILED')) return 'FAILED';
  if (text.includes('CANCEL')) return 'CANCELLED';
  if (text.includes('TRANSIT')) return 'IN_TRANSIT';
  return 'CREATED';
}

function formatRelativeTime(dateString?: string): string {
  if (!dateString) return '';
  const date = new Date(dateString);
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 60) return 'Just now';
  const diffInMinutes = Math.floor(diffInSeconds / 60);
  if (diffInMinutes < 60) return `${diffInMinutes} min ago`;
  const diffInHours = Math.floor(diffInMinutes / 60);
  if (diffInHours < 24) return `${diffInHours}h ago`;
  const diffInDays = Math.floor(diffInHours / 24);
  if (diffInDays === 1) return 'Yesterday';
  if (diffInDays < 7) return `${diffInDays}d ago`;
  return date.toLocaleDateString();
}

export function NotificationBell() {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const token = useAuthStore((state) => state.token);
  const role = useAuthStore((state) => state.role);
  const isDevSession = useAuthStore((state) => state.isDevSession);

  // Poll unread count every 20 seconds as required by Member 9 spec
  const { data: unreadCount = 0 } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.getUnreadCount,
    enabled: !!token && !isDevSession,
    refetchInterval: 20_000,
    staleTime: 10_000,
    retry: false
  });

  // Fetch full in-app notifications when popover is open
  const { data: rawNotifications = [] } = useQuery({
    queryKey: ['notifications', 'my-list'],
    queryFn: notificationApi.listMyNotifications,
    enabled: !!token && !isDevSession && isOpen,
    staleTime: 5_000
  });

  // Mutation to mark single item read
  const markReadMutation = useMutation({
    mutationFn: (id: number) => notificationApi.markAsRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    }
  });

  // Mutation to mark all items read
  const markAllReadMutation = useMutation({
    mutationFn: () => notificationApi.markAllAsRead(),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    }
  });

  // Close dropdown on click outside or Escape
  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const hasUnread = unreadCount > 0;

  // Map backend notifications to design system NotificationData items
  const items: NotificationData[] = useMemo(() => {
    return rawNotifications.map((notif: InAppNotificationResponse) => {
      const trackingPrefix = notif.trackingNumber ? `${notif.trackingNumber} · ` : '';
      return {
        id: String(notif.id ?? Math.random()),
        title: notif.title ?? 'Notification',
        message: `${trackingPrefix}${notif.body ?? ''}`,
        time: formatRelativeTime(notif.createdAt),
        unread: !notif.read,
        status: inferStatus(notif.title, notif.body)
      };
    });
  }, [rawNotifications]);

  const handleItemClick = (idString: string) => {
    const id = Number(idString);
    const target = rawNotifications.find((n) => n.id === id);

    if (target && !target.read && !Number.isNaN(id)) {
      markReadMutation.mutate(id);
    }

    // Extract tracking number if present directly or in body
    const trackingNumber =
      target?.trackingNumber || target?.body?.match(/SD-[A-Z0-9]{4}-[A-Z0-9]{4}/)?.[0];

    if (trackingNumber) {
      setIsOpen(false);
      const trackingPath =
        role === 'CUSTOMER' ? `/customer/tracking/${trackingNumber}` : `/track/${trackingNumber}`;
      navigate(trackingPath);
    }
  };

  const handleMarkAllRead = () => {
    markAllReadMutation.mutate();
  };

  return (
    <div ref={dropdownRef} className="relative inline-block">
      <button
        type="button"
        aria-label={hasUnread ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        aria-expanded={isOpen}
        onClick={() => setIsOpen((prev) => !prev)}
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

      {isOpen ? (
        <div className="fixed right-2 top-14 z-50 w-[382px] max-w-[calc(100vw-1rem)] sm:absolute sm:right-0 sm:top-full sm:mt-2 shadow-2xl transition-all duration-150 animate-in fade-in slide-in-from-top-2">
          <NotificationTray
            items={items}
            onItemClick={handleItemClick}
            onMarkAllRead={hasUnread ? handleMarkAllRead : undefined}
          />
        </div>
      ) : null}
    </div>
  );
}

export default NotificationBell;
