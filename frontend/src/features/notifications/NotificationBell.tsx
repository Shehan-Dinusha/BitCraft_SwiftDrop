import { Bell, BellRing } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ModalDrawerShell } from '@/design-system';
import { notificationApi, type InAppNotificationResponse } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';
import { NotificationSettings } from './NotificationSettings';
import { NotificationTray, type TrayItem } from './NotificationTray';
import { notificationMeta, resolveNotificationType } from './notificationTypes';

const TRACKING_PATTERN = /SD-[A-Z0-9]{4}-[A-Z0-9]{4}/;

export function NotificationBell() {
  const [isOpen, setIsOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const token = useAuthStore((state) => state.token);
  const role = useAuthStore((state) => state.role);
  const isDevSession = useAuthStore((state) => state.isDevSession);

  // Poll the unread count every 20 seconds (Member 9 spec).
  const { data: unreadCount = 0 } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: notificationApi.getUnreadCount,
    enabled: !!token && !isDevSession,
    refetchInterval: 20_000,
    staleTime: 10_000,
    retry: false
  });

  const { data: rawNotifications = [], isLoading } = useQuery({
    queryKey: ['notifications', 'my-list'],
    queryFn: notificationApi.listMyNotifications,
    enabled: !!token && !isDevSession && isOpen,
    staleTime: 5_000
  });

  const markReadMutation = useMutation({
    mutationFn: (id: number) => notificationApi.markAsRead(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['notifications'] })
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => notificationApi.markAllAsRead(),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['notifications'] })
  });

  useEffect(() => {
    if (!isOpen) return;
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) setIsOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const hasUnread = unreadCount > 0;

  // The body already contains the tracking number, so it is shown once (no "SD-… ·" prefix).
  const items: TrayItem[] = useMemo(
    () =>
      rawNotifications
        .filter((n: InAppNotificationResponse) => n.id != null)
        .map((n: InAppNotificationResponse) => {
          const type = resolveNotificationType(n.notificationType, n.title);
          return {
            id: n.id as number,
            type,
            title: type ? notificationMeta(type).label : n.title ?? 'Notification',
            message: n.body ?? '',
            createdAt: n.createdAt,
            unread: !n.read
          };
        }),
    [rawNotifications]
  );

  const handleItemClick = (item: TrayItem) => {
    const target = rawNotifications.find((n) => n.id === item.id);
    if (item.unread) markReadMutation.mutate(item.id);

    const trackingNumber = target?.trackingNumber || target?.body?.match(TRACKING_PATTERN)?.[0];
    if (trackingNumber) {
      setIsOpen(false);
      navigate(role === 'CUSTOMER' ? `/customer/tracking/${trackingNumber}` : `/track/${trackingNumber}`);
    }
  };

  const openSettings = () => {
    setIsOpen(false);
    setSettingsOpen(true);
  };

  const BellIcon = hasUnread ? BellRing : Bell;

  return (
    <div ref={dropdownRef} className="relative inline-block">
      <button
        type="button"
        aria-label={hasUnread ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((prev) => !prev)}
        className={cn(
          'relative inline-flex size-10 items-center justify-center rounded-full border transition-all duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
          isOpen
            ? 'border-brand-primary/30 bg-brand-subtle text-brand-primary'
            : 'border-transparent text-secondary hover:border-border-subtle hover:bg-elevated hover:text-primary'
        )}
      >
        <BellIcon aria-hidden="true" strokeWidth={2} className={cn('size-5', hasUnread && !isOpen && 'text-primary')} />
        {hasUnread ? (
          <span
            aria-hidden="true"
            className="absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-status-danger px-1 text-[10px] font-bold tabular-nums leading-none text-white ring-2 ring-card"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        ) : null}
      </button>

      {isOpen ? (
        <div className="fixed right-2 top-14 z-50 w-[392px] max-w-[calc(100vw-1rem)] animate-in fade-in slide-in-from-top-2 shadow-2xl transition-all duration-150 sm:absolute sm:right-0 sm:top-full sm:mt-2">
          <NotificationTray
            items={items}
            unreadCount={unreadCount}
            loading={isLoading}
            onItemClick={handleItemClick}
            onMarkAllRead={() => markAllReadMutation.mutate()}
            onOpenSettings={openSettings}
          />
        </div>
      ) : null}

      <ModalDrawerShell
        mode="drawer"
        open={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        title="Notification settings"
        description="Choose how SwiftDrop keeps you updated about your parcels."
        size="md"
      >
        {settingsOpen ? <NotificationSettings /> : null}
      </ModalDrawerShell>
    </div>
  );
}

export default NotificationBell;
