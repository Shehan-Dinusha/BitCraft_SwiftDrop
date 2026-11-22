import { useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, CheckCircle2, Mail } from 'lucide-react';
import { Toggle } from '@/design-system/components/Toggle/Toggle';
import {
  notificationApi,
  type RecipientPreferenceResponse,
  type UpdatePreferenceRequest
} from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';
import { DevicePushRow } from './DevicePushRow';

function errorMessage(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { message?: string } }; message?: string };
  return e?.response?.data?.message ?? e?.message ?? fallback;
}

function RowIcon({ children }: { children: ReactNode }) {
  return (
    <span
      aria-hidden="true"
      className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary [&_svg]:size-[18px]"
    >
      {children}
    </span>
  );
}

/**
 * The signed-in user's notification settings. Every switch saves at once:
 * - Email: parcel and job updates by email (security email is always sent).
 * - Lock-screen notifications (customers and couriers): Web Push on this device.
 * The in-app inbox on the Notifications page is always on.
 */
export function NotificationSettings({ className }: { className?: string }) {
  const user = useAuthStore((s) => s.user);
  const role = useAuthStore((s) => s.role);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const queryClient = useQueryClient();
  const userId = user?.id;
  const queryKey = ['notifications', 'preferences', userId];

  const { data: pref, isLoading, isError } = useQuery({
    queryKey,
    queryFn: () => notificationApi.getCurrentPreferences(),
    enabled: !!userId && !isDevSession,
    staleTime: 30_000,
    retry: false
  });

  const [savingEmail, setSavingEmail] = useState(false);
  const [status, setStatus] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

  const emailEnabled = pref?.emailEnabled ?? true;
  const pushEnabled = pref?.pushEnabled ?? true;
  const devicePush = role === 'CUSTOMER' || role === 'COURIER';

  /** Saves one change; address and language are kept as stored. */
  const save = async (patch: Partial<Pick<UpdatePreferenceRequest, 'emailEnabled' | 'pushEnabled'>>) => {
    if (!userId) throw new Error('Sign in to change notification settings');
    const updated: RecipientPreferenceResponse = await notificationApi.updatePreferences(userId, {
      emailEnabled,
      pushEnabled,
      emailAddress: pref?.emailAddress ?? '',
      languagePreference: pref?.languagePreference ?? 'en',
      ...patch
    });
    queryClient.setQueryData(queryKey, updated);
  };

  const changeEmail = async (next: boolean) => {
    setSavingEmail(true);
    setStatus(null);
    try {
      await save({ emailEnabled: next });
      setStatus({ kind: 'success', text: next ? 'Email updates are on.' : 'Email updates are off.' });
    } catch (err) {
      setStatus({ kind: 'error', text: errorMessage(err, 'Could not save. Please try again.') });
    } finally {
      setSavingEmail(false);
    }
  };

  if (isDevSession || !userId) {
    return (
      <p className={cn('text-caption text-secondary', className)}>
        Sign in with a real account to manage notification settings.
      </p>
    );
  }

  if (isLoading) {
    return (
      <p className={cn('text-caption text-secondary', className)}>Loading your notification settings…</p>
    );
  }

  const large = role === 'COURIER';

  return (
    <section className={cn('flex flex-col gap-3', className)} aria-label="Notification settings">
      <h2 className="text-body-strong text-primary">How you get notified</h2>

      {isError ? (
        <p className="flex items-center gap-2 rounded-xl border border-status-warning-border bg-status-warning-bg px-3 py-2 text-caption text-status-warning-fg">
          <AlertCircle aria-hidden="true" className="size-4 shrink-0" />
          We couldn't load your saved settings, so the defaults are shown.
        </p>
      ) : null}

      <div className="divide-y divide-border-subtle rounded-xl border border-border-subtle bg-card">
        <Toggle
          aria-label="Email"
          checked={emailEnabled}
          disabled={savingEmail}
          onChange={(e) => void changeEmail(e.target.checked)}
          className={cn(
            'flex w-full flex-row-reverse items-center justify-between gap-3 px-4 py-3.5',
            large && 'min-h-target'
          )}
          label={
            <span className="flex min-w-0 items-center gap-3">
              <RowIcon>
                <Mail />
              </RowIcon>
              <span className="flex min-w-0 flex-col">
                <span className="text-body-strong text-primary">Email</span>
                <span className="text-caption text-secondary">
                  {savingEmail ? 'Saving…' : 'Updates sent to your email inbox'}
                </span>
              </span>
            </span>
          }
        />

        {devicePush ? (
          <DevicePushRow
            large={large}
            pushEnabled={pushEnabled}
            onPreferenceChange={async (next) => {
              await save({ pushEnabled: next });
              setStatus({
                kind: 'success',
                text: next ? 'Lock-screen notifications are on.' : 'Lock-screen notifications are off.'
              });
            }}
          />
        ) : null}

      </div>

      {status ? (
        <p
          role="status"
          className={cn(
            'flex items-center gap-2 text-caption',
            status.kind === 'success' ? 'text-status-success-fg' : 'text-status-danger-fg'
          )}
        >
          {status.kind === 'success' ? (
            <CheckCircle2 aria-hidden="true" className="size-4" />
          ) : (
            <AlertCircle aria-hidden="true" className="size-4" />
          )}
          {status.text}
        </p>
      ) : null}
    </section>
  );
}

export default NotificationSettings;
