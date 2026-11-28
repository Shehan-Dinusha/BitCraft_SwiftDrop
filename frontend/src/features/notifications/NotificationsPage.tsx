import { useNavigate, useSearchParams } from 'react-router-dom';
import { SurfaceCard, Tabs } from '@/design-system';
import { useAuthStore } from '@/store/useAuthStore';
import { NotificationSettings } from './NotificationSettings';
import { NotificationTray, type TrayItem } from './NotificationTray';
import { notificationTarget } from './notificationTypes';
import { useNotificationInbox } from './useNotificationInbox';

type PageTab = 'inbox' | 'settings';

/** Notifications page for every workspace: the inbox and the notification settings. */
export function NotificationsPage() {
  const role = useAuthStore((s) => s.role);
  const backendRole = useAuthStore((s) => s.user?.role);
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab: PageTab = params.get('tab') === 'settings' ? 'settings' : 'inbox';
  const inbox = useNotificationInbox();

  const setTab = (next: PageTab) => setParams(next === 'settings' ? { tab: 'settings' } : {}, { replace: true });

  const openItem = (item: TrayItem) => {
    if (item.unread) inbox.markRead(item.id);
    const target = notificationTarget(role, item.type, item.trackingNumber, backendRole);
    if (target) navigate(target);
  };

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      {/* The header bar and the sidebar already show the page name. */}
      <h1 className="sr-only">Notifications</h1>

      <Tabs
        aria-label="Notifications"
        tabs={[
          { id: 'inbox', label: inbox.unreadCount > 0 ? `Inbox (${inbox.unreadCount})` : 'Inbox' },
          { id: 'settings', label: 'Settings' }
        ]}
        value={tab}
        onChange={(id) => setTab(id as PageTab)}
        className="overflow-y-hidden"
      />

      {tab === 'inbox' ? (
        <section role="tabpanel" aria-label="Inbox" className="max-w-3xl">
          {inbox.unavailable ? (
            <p className="text-caption text-secondary">Sign in with a real account to see your notifications.</p>
          ) : (
            <NotificationTray
              items={inbox.items}
              unreadCount={inbox.unreadCount}
              loading={inbox.isLoading}
              onItemClick={openItem}
              onMarkAllRead={inbox.markAllRead}
            />
          )}
        </section>
      ) : (
        <section role="tabpanel" aria-label="Settings" className="max-w-3xl">
          <SurfaceCard>
            <NotificationSettings />
          </SurfaceCard>
        </section>
      )}
    </div>
  );
}

export default NotificationsPage;
