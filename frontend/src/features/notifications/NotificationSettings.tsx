import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, Bell, CheckCircle2, Mail, ShieldCheck } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input } from '@/design-system/primitives/Input';
import { Toggle } from '@/design-system/components/Toggle/Toggle';
import { notificationApi, type RecipientPreferenceResponse } from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';
import { NOTIFICATION_TYPES, NOTIFICATION_TYPES_BY_ROLE, notificationTone } from './notificationTypes';

interface FormState {
  emailEnabled: boolean;
  pushEnabled: boolean;
  emailAddress: string;
  languagePreference: string;
}

const LANGUAGES = [
  { value: 'en', label: 'English' },
  { value: 'si', label: 'Sinhala (සිංහල)' },
  { value: 'ta', label: 'Tamil (தமிழ்)' }
];

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

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
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
 * The signed-in user's notification settings: delivery channels, email address and language,
 * and what they are notified about. Works for every role; the API only returns the caller's own settings.
 */
export function NotificationSettings({ className }: { className?: string }) {
  const user = useAuthStore((s) => s.user);
  const role = useAuthStore((s) => s.role);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const queryClient = useQueryClient();
  const userId = user?.id;

  const { data: pref, isLoading, isError } = useQuery({
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

  const dirty =
    form.emailEnabled !== saved.emailEnabled ||
    form.pushEnabled !== saved.pushEnabled ||
    form.emailAddress.trim() !== saved.emailAddress.trim() ||
    form.languagePreference !== saved.languagePreference;

  const save = useMutation({
    mutationFn: () => {
      if (!userId) throw new Error('Sign in to change notification settings');
      return notificationApi.updatePreferences(userId, {
        emailEnabled: form.emailEnabled,
        pushEnabled: form.pushEnabled,
        // An empty value clears a custom address, so emails go to the account email again.
        emailAddress: form.emailAddress.trim(),
        languagePreference: form.languagePreference
      });
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(['notifications', 'preferences', userId], updated);
      setStatus({ kind: 'success', text: 'Your notification settings were saved.' });
    },
    onError: (err) => setStatus({ kind: 'error', text: errorMessage(err, 'Could not save your settings. Please try again.') })
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
    return <p className={cn('text-caption text-secondary', className)}>Loading your notification settings…</p>;
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

      <Section title="Delivery channels" description="Choose where SwiftDrop sends your parcel updates.">
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

      <Section title="Email delivery" description="Where parcel emails go and which language they use.">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field
            label="Notification email"
            hint={user?.email ? `Leave empty to use your account email (${user.email}).` : 'Address for parcel emails.'}
          >
            <Input
              variant="text"
              inputMode="email"
              autoComplete="email"
              value={form.emailAddress}
              disabled={!form.emailEnabled}
              onChange={(e) => update({ emailAddress: e.target.value })}
              placeholder={user?.email ?? 'you@example.com'}
            />
          </Field>
          <Field label="Language" hint="Language for notification messages.">
            <select
              value={form.languagePreference}
              onChange={(e) => update({ languagePreference: e.target.value })}
              className="h-10 w-full rounded-xl border border-border-subtle bg-card px-3 text-body text-primary focus:outline-none focus:ring-2 focus:ring-border-focus"
            >
              {LANGUAGES.map((l) => (
                <option key={l.value} value={l.value}>
                  {l.label}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </Section>

      <Section
        title="What you'll be notified about"
        description={role === 'CUSTOMER' ? "Every stage of your parcel's journey." : 'Updates for your work on SwiftDrop.'}
      >
        <ul className="divide-y divide-border-subtle rounded-xl border border-border-subtle bg-card">
          {NOTIFICATION_TYPES_BY_ROLE[role].map((type) => {
            const { label, description, Icon, emailed } = NOTIFICATION_TYPES[type];
            return (
              <li key={type} className="flex items-center gap-3 px-4 py-3">
                <span
                  aria-hidden="true"
                  className={cn('flex size-8 shrink-0 items-center justify-center rounded-full [&_svg]:size-4', notificationTone(type))}
                >
                  <Icon />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-body-strong text-primary">{label}</p>
                  <p className="text-caption text-secondary">{description}</p>
                </div>
                <div className="flex shrink-0 gap-1.5" aria-label={`${label} channels`}>
                  {emailed ? <ChannelChip on={form.emailEnabled}>Email</ChannelChip> : null}
                  <ChannelChip on={form.pushEnabled || !emailed}>In-app</ChannelChip>
                </div>
              </li>
            );
          })}
        </ul>
        <p className="flex items-start gap-2 rounded-xl bg-elevated px-3 py-2.5 text-caption text-secondary">
          <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-primary" />
          Security emails (verification and sign-in codes, password resets and sign-in alerts) are always
          sent to your account email, even when email notifications are off.
        </p>
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
        <Button type="button" variant="ghost" disabled={!dirty || save.isPending} onClick={() => update(saved)}>
          Discard
        </Button>
        <Button type="submit" variant="primary" loading={save.isPending} disabled={!dirty || save.isPending}>
          Save settings
        </Button>
      </div>
    </form>
  );
}

function ChannelChip({ on, children }: { on: boolean; children: ReactNode }) {
  return (
    <span
      className={cn(
        'rounded-full border px-2 py-0.5 text-[11px] font-semibold',
        on ? 'border-brand-primary/30 bg-brand-subtle text-brand-primary' : 'border-border-subtle text-muted line-through'
      )}
    >
      {children}
    </span>
  );
}

export default NotificationSettings;
