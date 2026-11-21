import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Mail, ShieldCheck } from 'lucide-react';
import { PageHeader, SurfaceCard, Tabs } from '@/design-system';
import { notificationApi, type NotificationDispatchLogResponse } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';
import { NotificationSettings } from './NotificationSettings';
import { NotificationTray, type TrayItem } from './NotificationTray';
import {
  notificationMeta,
  notificationTarget,
  notificationTone,
  resolveNotificationType
} from './notificationTypes';
import { useNotificationInbox } from './useNotificationInbox';

type PageTab = 'inbox' | 'settings';

const SECURITY_TEMPLATES: Record<string, string> = {
  EMAIL_VERIFICATION: 'Email verification code',
  PASSWORD_RESET: 'Password reset code',
  LOGIN_MFA: 'Sign-in code',
  LOGIN_SUCCESS: 'New sign-in alert',
  LOGIN_FAILED: 'Failed sign-in alert'
};

const STATUS_STYLE: Record<string, string> = {
  SENT: 'bg-status-success-bg text-status-success-fg border-status-success-border',
  SUPPRESSED: 'bg-status-warning-bg text-status-warning-fg border-status-warning-border',
  FAILED: 'bg-status-danger-bg text-status-danger-fg border-status-danger-border'
};
const STATUS_LABEL: Record<string, string> = { SENT: 'Sent', SUPPRESSED: 'Not sent', FAILED: 'Failed' };

function formatWhen(value?: string): string {
  if (!value) return '';
  return new Date(value).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

function EmailRow({ log }: { log: NotificationDispatchLogResponse }) {
  const securityLabel = log.templateCode ? SECURITY_TEMPLATES[log.templateCode] : undefined;
  const type = resolveNotificationType(log.templateCode, log.subject);
  const label = securityLabel ?? (type ? notificationMeta(type).label : log.subject ?? 'Email');
  const { Icon } = notificationMeta(type);
  const status = log.status ?? 'SENT';
  return (
    <li className="flex items-center gap-3 px-4 py-3">
      <span
        aria-hidden="true"
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full [&_svg]:size-4',
          securityLabel ? 'bg-brand-subtle text-brand-primary' : notificationTone(type)
        )}
      >
        {securityLabel ? <ShieldCheck /> : <Icon />}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-body-strong text-primary">{label}</p>
        <p className="truncate text-caption text-secondary">
          {log.referenceId ? <span className="font-mono tabular-nums">{log.referenceId} · </span> : null}
          {log.destination}
        </p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <span className={cn('rounded-full border px-2 py-0.5 text-[11px] font-semibold', STATUS_STYLE[status] ?? STATUS_STYLE.SENT)}>
          {STATUS_LABEL[status] ?? status}
        </span>
        <span className="text-[11px] tabular-nums text-muted">{formatWhen(log.createdAt)}</span>
      </div>
    </li>
  );
}

/** Emails SwiftDrop recently sent to the signed-in user (only their own, even for operations staff). */
function RecentEmails() {
  const user = useAuthStore((s) => s.user);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const { data: allLogs = [], isLoading } = useQuery({
    queryKey: ['notifications', 'email-activity', user?.id],
    queryFn: () => notificationApi.listLogs({ recipientId: user?.id, channel: 'EMAIL' }),
    enabled: !!user?.id && !isDevSession,
    staleTime: 30_000,
    retry: false
  });
  // A sign-in from a recognised device is logged as "not sent" on purpose; it is not news to the user.
  const logs = allLogs.filter((log) => !(log.status === 'SUPPRESSED' && log.templateCode === 'LOGIN_SUCCESS'));

  return (
    <section className="flex flex-col gap-3 border-t border-border-subtle pt-6">
      <div className="flex items-center gap-2">
        <Mail aria-hidden="true" className="size-4 text-brand-primary" />
        <h3 className="text-body-strong text-primary">Recent emails</h3>
      </div>
      {isLoading ? (
        <p className="text-caption text-secondary">Loading recent emails…</p>
      ) : logs.length === 0 ? (
        <p className="rounded-xl border border-dashed border-border-subtle px-4 py-6 text-center text-caption text-secondary">
          No emails yet.
        </p>
      ) : (
        <ul className="divide-y divide-border-subtle rounded-xl border border-border-subtle bg-card">
          {logs.slice(0, 10).map((log) => (
            <EmailRow key={log.logId} log={log} />
          ))}
        </ul>
      )}
    </section>
  );
}

/** Notifications page for every workspace: the inbox and the notification settings. */
export function NotificationsPage() {
  const role = useAuthStore((s) => s.role);
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab: PageTab = params.get('tab') === 'settings' ? 'settings' : 'inbox';
  const inbox = useNotificationInbox();

  const setTab = (next: PageTab) => setParams(next === 'settings' ? { tab: 'settings' } : {}, { replace: true });

  const openItem = (item: TrayItem) => {
    if (item.unread) inbox.markRead(item.id);
    const target = notificationTarget(role, item.type, item.trackingNumber);
    if (target) navigate(target);
  };

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="Notifications"
        className="border-0 bg-transparent p-0"
      />

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
              variant="page"
              items={inbox.items}
              unreadCount={inbox.unreadCount}
              loading={inbox.isLoading}
              onItemClick={openItem}
              onMarkAllRead={inbox.markAllRead}
              onOpenSettings={() => setTab('settings')}
            />
          )}
        </section>
      ) : (
        <section role="tabpanel" aria-label="Settings" className="max-w-3xl">
          <SurfaceCard className="flex flex-col gap-8">
            <NotificationSettings />
            <RecentEmails />
          </SurfaceCard>
        </section>
      )}
    </div>
  );
}

export default NotificationsPage;
