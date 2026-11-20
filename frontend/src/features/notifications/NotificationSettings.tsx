import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, Bell, CheckCircle2, Mail } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { Toggle } from '@/design-system/components/Toggle/Toggle';
import { notificationApi, type RecipientPreferenceResponse } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';

interface FormState {
  emailEnabled: boolean;
  pushEnabled: boolean;
  emailAddress: string;
  languagePreference: string;
}

function toForm(pref: RecipientPreferenceResponse | undefined): FormState {
  return {
    emailEnabled: pref?.emailEnabled ?? true,
    pushEnabled: pref?.pushEnabled ?? true,
    emailAddress: pref?.emailAddress ?? '',
    languagePreference: pref?.languagePreference ?? 'en'
  };
}

function errorMessage(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { message?: string } }; message?: string };
  return e?.response?.data?.message ?? e?.message ?? fallback;
}

function Section({
  title,
  description,
  children
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-3">
      <div>
        <h3 className="text-body-strong text-primary">{title}</h3>
        <p className="text-caption text-secondary">{description}</p>
      </div>
      {children}
    </section>
  );
}

function ChannelRow({
  icon,
  title,
  description,
  checked,
  onChange
}: {
  icon: ReactNode;
  title: string;
  description: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <div className="flex items-center gap-3 px-4 py-3.5">
      <span
        aria-hidden="true"
        className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary [&_svg]:size-[18px]"
      >
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-body-strong text-primary">{title}</p>
        <p className="text-caption text-secondary">{description}</p>
      </div>
      <Toggle aria-label={title} checked={checked} onChange={(e) => onChange(e.target.checked)} />
    </div>
  );
}

/**
 * The signed-in user's notification settings: which delivery channels (email, in-app) are on. Works for every role; the API only returns the caller's own settings.
 */
export function NotificationSettings({ className }: { className?: string }) {
  const user = useAuthStore((s) => s.user);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const queryClient = useQueryClient();
  const userId = user?.id;

  const {
    data: pref,
    isLoading,
    isError
  } = useQuery({
    queryKey: ['notifications', 'preferences', userId],
    queryFn: () => notificationApi.getCurrentPreferences(),
    enabled: !!userId && !isDevSession,
    staleTime: 30_000,
    retry: false
  });

  const saved = useMemo(() => toForm(pref), [pref]);
  const [form, setForm] = useState<FormState>(saved);
  const [status, setStatus] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

  useEffect(() => setForm(saved), [saved]);

  const dirty = form.emailEnabled !== saved.emailEnabled || form.pushEnabled !== saved.pushEnabled;

  const save = useMutation({
    mutationFn: () => {
      if (!userId) throw new Error('Sign in to change notification settings');
      return notificationApi.updatePreferences(userId, {
        emailEnabled: form.emailEnabled,
        pushEnabled: form.pushEnabled,
        // Address and language are no longer set from the UI: whatever is stored is kept as it is.
        emailAddress: saved.emailAddress,
        languagePreference: saved.languagePreference
      });
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(['notifications', 'preferences', userId], updated);
      setStatus({ kind: 'success', text: 'Your notification settings were saved.' });
    },
    onError: (err) =>
      setStatus({
        kind: 'error',
        text: errorMessage(err, 'Could not save your settings. Please try again.')
      })
  });

  const update = (patch: Partial<FormState>) => {
    setStatus(null);
    setForm((current) => ({ ...current, ...patch }));
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
      <p className={cn('text-caption text-secondary', className)}>
        Loading your notification settings…
      </p>
    );
  }

  return (
    <form
      className={cn('flex flex-col gap-7', className)}
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      {isError ? (
        <p className="flex items-center gap-2 rounded-xl border border-status-warning-border bg-status-warning-bg px-3 py-2 text-caption text-status-warning-fg">
          <AlertCircle aria-hidden="true" className="size-4 shrink-0" />
          We couldn't load your saved settings, so the defaults are shown.
        </p>
      ) : null}

      <Section
        title="Delivery channels"
        description="Choose where SwiftDrop sends your parcel updates."
      >
        <div className="divide-y divide-border-subtle rounded-xl border border-border-subtle bg-card">
          <ChannelRow
            icon={<Mail />}
            title="Email"
            description="Parcel updates sent to your email inbox"
            checked={form.emailEnabled}
            onChange={(emailEnabled) => update({ emailEnabled })}
          />
          <ChannelRow
            icon={<Bell />}
            title="In-app"
            description="Updates in the notification bell while you use SwiftDrop"
            checked={form.pushEnabled}
            onChange={(pushEnabled) => update({ pushEnabled })}
          />
        </div>
      </Section>

      <div className="flex flex-col-reverse items-stretch gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:items-center sm:justify-end">
        {status ? (
          <p
            role="status"
            className={cn(
              'flex items-center gap-2 text-caption sm:mr-auto',
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
        <Button
          type="button"
          variant="ghost"
          disabled={!dirty || save.isPending}
          onClick={() => update(saved)}
        >
          Discard
        </Button>
        <Button
          type="submit"
          variant="primary"
          loading={save.isPending}
          disabled={!dirty || save.isPending}
        >
          Save settings
        </Button>
      </div>
    </form>
  );
}

export default NotificationSettings;
