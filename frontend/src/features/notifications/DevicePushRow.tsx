import { useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BellRing, Share, ShieldAlert } from 'lucide-react';
import { Toggle } from '@/design-system/components/Toggle/Toggle';
import { notificationApi } from '@/services/api/notificationApi';
import { cn } from '@/utils/cn';
import {
  PushSetupError,
  currentSubscription,
  disableDevicePush,
  enableDevicePush,
  notificationPermission,
  pushSupport
} from '@/features/pwa/webPush';

interface DevicePushRowProps {
  /** The account's "push" preference (push_enabled). */
  pushEnabled: boolean;
  /** Saves the preference; the row calls it after this device is (un)subscribed. */
  onPreferenceChange: (pushEnabled: boolean) => Promise<void>;
  /** Couriers get 56px targets (kinetic density). */
  large?: boolean;
}

function Hint({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <p className="flex items-start gap-2 px-4 pb-3.5 text-caption text-secondary [&_svg]:mt-0.5 [&_svg]:size-4 [&_svg]:shrink-0">
      {icon}
      <span>{children}</span>
    </p>
  );
}

/**
 * "Lock-screen notifications" for this device. Asks the browser for permission only when the user
 * taps the switch, never on page load.
 */
export function DevicePushRow({ pushEnabled, onPreferenceChange, large = false }: DevicePushRowProps) {
  const support = pushSupport();
  const [subscribed, setSubscribed] = useState<boolean | null>(null);
  const [permission, setPermission] = useState<NotificationPermission>(() => notificationPermission());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data: server } = useQuery({
    queryKey: ['notifications', 'push-public-key'],
    queryFn: notificationApi.getPushPublicKey,
    enabled: support === 'supported',
    staleTime: 5 * 60_000,
    retry: false
  });

  useEffect(() => {
    if (support !== 'supported') return;
    let alive = true;
    void currentSubscription()
      .then((sub) => alive && setSubscribed(sub !== null))
      .catch(() => alive && setSubscribed(false));
    return () => {
      alive = false;
    };
  }, [support]);

  const on = Boolean(subscribed) && pushEnabled && permission === 'granted';
  const unavailable = server !== undefined && !server.enabled;

  const change = async (next: boolean) => {
    setBusy(true);
    setError(null);
    try {
      if (next) {
        await enableDevicePush();
        setSubscribed(true);
      } else {
        await disableDevicePush();
        setSubscribed(false);
      }
      await onPreferenceChange(next);
    } catch (e) {
      setError(
        e instanceof PushSetupError
          ? e.message
          : 'Could not change notifications on this device. Please try again.'
      );
    } finally {
      setPermission(notificationPermission());
      setBusy(false);
    }
  };

  const title = 'Lock-screen notifications';
  const icon = (
    <span
      aria-hidden="true"
      className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary [&_svg]:size-[18px]"
    >
      <BellRing />
    </span>
  );

  if (support === 'ios-needs-install') {
    return (
      <div>
        <div className={cn('flex items-center gap-3 px-4 py-3.5', large && 'min-h-target')}>
          {icon}
          <div className="min-w-0 flex-1">
            <p className="text-body-strong text-primary">{title}</p>
            <p className="text-caption text-secondary">Needs SwiftDrop on your Home Screen</p>
          </div>
        </div>
        <Hint icon={<Share aria-hidden="true" />}>
          On iPhone and iPad, notifications work only from the Home Screen app (iOS 16.4 or later). Tap
          Share, then Add to Home Screen, open SwiftDrop from there and turn this on.
        </Hint>
      </div>
    );
  }

  if (support === 'unsupported' || unavailable) {
    return (
      <div className={cn('flex items-center gap-3 px-4 py-3.5', large && 'min-h-target')}>
        {icon}
        <div className="min-w-0 flex-1">
          <p className="text-body-strong text-primary">{title}</p>
          <p className="text-caption text-secondary">
            {unavailable
              ? 'Not available yet. Updates still appear on your Notifications page and by email.'
              : 'This browser cannot show notifications. Try Chrome, Edge, Firefox or Safari.'}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <Toggle
        aria-label={title}
        checked={on}
        disabled={busy || subscribed === null}
        onChange={(e) => void change(e.target.checked)}
        className={cn(
          'flex w-full flex-row-reverse items-center justify-between gap-3 px-4 py-3.5',
          large && 'min-h-target'
        )}
        label={
          <span className="flex min-w-0 items-center gap-3">
            {icon}
            <span className="flex min-w-0 flex-col">
              <span className="text-body-strong text-primary">{title}</span>
              <span className="text-caption text-secondary">
                {busy
                  ? 'Updating…'
                  : on
                    ? 'On for this device, even when SwiftDrop is closed'
                    : 'Alerts on this phone or computer, even when SwiftDrop is closed'}
              </span>
            </span>
          </span>
        }
      />
      {permission === 'denied' ? (
        <Hint icon={<ShieldAlert aria-hidden="true" className="text-status-warning-fg" />}>
          Notifications are blocked for SwiftDrop in this browser. Allow them in the site settings
          (the icon next to the address bar), or in Settings, then Apps, then SwiftDrop, then
          Notifications for the installed app. Then turn this on again.
        </Hint>
      ) : error ? (
        <Hint icon={<ShieldAlert aria-hidden="true" className="text-status-danger-fg" />}>{error}</Hint>
      ) : on ? (
        <Hint icon={<BellRing aria-hidden="true" />}>
          Turning this off stops lock-screen notifications on all your devices. Updates still appear on
          your Notifications page.
        </Hint>
      ) : null}
    </div>
  );
}

export default DevicePushRow;
