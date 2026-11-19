import { useQuery } from '@tanstack/react-query';
import { Bell, Mail, ShieldCheck } from 'lucide-react';
import { notificationApi, type NotificationDispatchLogResponse } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';
import { NotificationSettings } from '@/features/notifications/NotificationSettings';
import {
  notificationMeta,
  notificationTone,
  resolveNotificationType
} from '@/features/notifications/notificationTypes';

const SECURITY_TEMPLATES: Record<string, string> = {
  EMAIL_VERIFICATION: 'Email verification code',
  PASSWORD_RESET: 'Password reset code',
  LOGIN_MFA: 'Sign-in code'
};

const STATUS_STYLE: Record<string, string> = {
  SENT: 'bg-status-success-bg text-status-success-fg border-status-success-border',
  SUPPRESSED: 'bg-status-warning-bg text-status-warning-fg border-status-warning-border',
  FAILED: 'bg-status-danger-bg text-status-danger-fg border-status-danger-border'
};

const STATUS_LABEL: Record<string, string> = { SENT: 'Sent', SUPPRESSED: 'Turned off', FAILED: 'Failed' };

function formatWhen(value?: string): string {
  if (!value) return '';
  return new Date(value).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

function EmailActivityRow({ log }: { log: NotificationDispatchLogResponse }) {
  const securityLabel = log.templateCode ? SECURITY_TEMPLATES[log.templateCode] : undefined;
  const type = resolveNotificationType(log.templateCode, log.subject);
  const label = securityLabel ?? (type ? notificationMeta(type).label : log.subject ?? log.templateCode ?? 'Email');
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
          {log.referenceId ? <span className="font-mono tabular-nums">{log.referenceId}</span> : null}
          {log.referenceId && log.destination ? ' · ' : null}
          {log.destination}
        </p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <span
          className={cn(
            'rounded-full border px-2 py-0.5 text-[11px] font-semibold',
            STATUS_STYLE[status] ?? STATUS_STYLE.SENT
          )}
        >
          {STATUS_LABEL[status] ?? status}
        </span>
        <span className="text-[11px] tabular-nums text-muted">{formatWhen(log.createdAt)}</span>
      </div>
    </li>
  );
}

/** Customer "Notifications" tab: notification settings plus the emails SwiftDrop recently sent. */
export function NotificationPreferencesCard() {
  const user = useAuthStore((s) => s.user);
  const isDevSession = useAuthStore((s) => s.isDevSession);

  const { data: logs = [], isLoading } = useQuery({
    queryKey: ['notifications', 'email-activity', user?.id],
    queryFn: () => notificationApi.listLogs({ recipientId: user?.id, channel: 'EMAIL' }),
    enabled: !!user?.id && !isDevSession,
    staleTime: 30_000,
    retry: false
  });

  return (
    <div className="flex flex-col gap-8">
      <header className="flex items-start gap-3 border-b border-border-subtle pb-5">
        <span
          aria-hidden="true"
          className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-subtle text-brand-primary"
        >
          <Bell className="size-5" />
        </span>
        <div>
          <h2 className="text-h3 text-primary">Notification settings</h2>
          <p className="text-caption text-secondary">
            Choose how SwiftDrop keeps you updated, from booking to delivery.
          </p>
        </div>
      </header>

      <NotificationSettings />

      <section className="flex flex-col gap-3 border-t border-border-subtle pt-6">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Mail aria-hidden="true" className="size-4 text-brand-primary" />
            <h3 className="text-body-strong text-primary">Recent emails</h3>
          </div>
          {logs.length > 0 ? (
            <span className="text-caption tabular-nums text-muted">
              {logs.length} {logs.length === 1 ? 'email' : 'emails'}
            </span>
          ) : null}
        </div>
        {isLoading ? (
          <p className="text-caption text-secondary">Loading recent emails…</p>
        ) : logs.length === 0 ? (
          <p className="rounded-xl border border-dashed border-border-subtle px-4 py-6 text-center text-caption text-secondary">
            No emails yet. Updates will appear here after you book a parcel.
          </p>
        ) : (
          <ul className="divide-y divide-border-subtle rounded-xl border border-border-subtle bg-card">
            {logs.slice(0, 10).map((log) => (
              <EmailActivityRow key={log.logId} log={log} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

export default NotificationPreferencesCard;
