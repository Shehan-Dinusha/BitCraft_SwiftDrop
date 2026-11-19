import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationApi, type InAppNotificationResponse } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import type { TrayItem } from './NotificationTray';
import { notificationMeta, resolveNotificationType } from './notificationTypes';

/** Unread count for the navigation badge, polled every 20 seconds (Member 9 spec). */
export function useUnreadNotificationCount(): number {
  const token = useAuthStore((s) => s.token);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const { data = 0 } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.getUnreadCount,
    enabled: !!token && !isDevSession,
    refetchInterval: 20_000,
    staleTime: 10_000,
    retry: false
  });
  return data;
}

/** The signed-in user's inbox (their own and their teams' notifications) with read actions. */
export function useNotificationInbox() {
  const token = useAuthStore((s) => s.token);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const queryClient = useQueryClient();
  const unreadCount = useUnreadNotificationCount();

  const { data: raw = [], isLoading, isError } = useQuery({
    queryKey: ['notifications', 'my-list'],
    queryFn: notificationApi.listMyNotifications,
    enabled: !!token && !isDevSession,
    refetchInterval: 30_000,
    staleTime: 5_000
  });

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const markRead = useMutation({ mutationFn: (id: number) => notificationApi.markAsRead(id), onSuccess: refresh });
  const markAllRead = useMutation({ mutationFn: () => notificationApi.markAllAsRead(), onSuccess: refresh });

  // The message already contains the tracking number, so it is not repeated separately.
  const items: TrayItem[] = useMemo(
    () =>
      raw
        .filter((n: InAppNotificationResponse) => n.id != null)
        .map((n: InAppNotificationResponse) => {
          const type = resolveNotificationType(n.notificationType, n.title);
          return {
            id: n.id as number,
            type,
            title: type ? notificationMeta(type).label : n.title ?? 'Notification',
            message: n.body ?? '',
            createdAt: n.createdAt,
            unread: !n.read,
            trackingNumber: n.trackingNumber ?? undefined
          };
        }),
    [raw]
  );

  return {
    items,
    unreadCount,
    isLoading,
    isError,
    unavailable: !token || isDevSession,
    markRead: (id: number) => markRead.mutate(id),
    markAllRead: () => markAllRead.mutate()
  };
}
