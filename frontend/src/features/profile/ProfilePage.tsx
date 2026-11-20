import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
  type ReactNode
} from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  BadgeCheck,
  Bell,
  CalendarDays,
  Camera,
  Clock,
  KeyRound,
  Mail,
  ShieldCheck,
  UserRound
} from 'lucide-react';
import {
  Button,
  Field,
  Input,
  PageHeader,
  SelectDropdown,
  StatusBanner,
  SurfaceCard
} from '@/design-system';
import {
  SRI_LANKAN_DISTRICTS,
  changeMyPassword,
  fetchCurrentUser,
  logoutUser,
  removeMyAvatar,
  updateMyProfile,
  uploadMyAvatar,
  type AuthUserProfile
} from '@/services/api/authApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';
import { NOTIFICATIONS_PATH } from '@/features/notifications/notificationTypes';
import {
  districtLabel,
  displayName,
  formatDate,
  passwordProblem,
  roleLabel,
  toUpdateRequest,
  validateProfile,
  type ProfileErrors,
  type ProfileForm
} from './profileFormat';
import { UserAvatar, sourceProblem, toAvatarImage, useMyAvatarUrl } from './avatar';
import { DeleteAccountCard } from '@/features/customer/components/profile/DeleteAccountCard';

/** Why we ask for a phone number, in each role's terms. */
const PHONE_HINT: Record<string, string> = {
  CUSTOMER: 'Optional. Couriers may use it to reach you about a delivery.',
  COURIER: 'Optional. Dispatch may use it to reach you about a job.',
  MERCHANT: 'Optional. SwiftDrop may use it to reach you about your orders.'
};

const DISTRICT_OPTIONS = SRI_LANKAN_DISTRICTS.map((value) => ({
  value,
  label: districtLabel(value)
}));

function formFrom(user: AuthUserProfile | null | undefined): ProfileForm {
  return {
    firstName: user?.firstName ?? '',
    lastName: user?.lastName ?? '',
    phone: user?.phone ?? '',
    district: user?.district ?? ''
  };
}

function Section({
  icon,
  title,
  description,
  children
}: {
  icon: ReactNode;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <SurfaceCard>
      <div className="flex flex-col gap-5">
        <div className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary [&_svg]:size-[18px]"
          >
            {icon}
          </span>
          <div className="min-w-0">
            <h2 className="text-h3 text-primary">{title}</h2>
            <p className="text-caption text-secondary">{description}</p>
          </div>
        </div>
        {children}
      </div>
    </SurfaceCard>
  );
}

function ReadOnlyRow({ label, value, note }: { label: string; value: ReactNode; note?: string }) {
  return (
    <div className="flex flex-col gap-1 border-t border-border-subtle pt-3 first:border-t-0 first:pt-0 sm:flex-row sm:items-start sm:gap-4">
      <dt className="w-40 shrink-0 text-caption font-semibold text-secondary">{label}</dt>
      <dd className="min-w-0 flex-1">
        <div className="break-words text-body text-primary">{value}</div>
        {note ? <p className="mt-0.5 text-caption text-muted">{note}</p> : null}
      </dd>
    </div>
  );
}

/** The profile picture with upload, change and remove. */
function ProfilePhoto({
  user,
  onSaved
}: {
  user: AuthUserProfile;
  onSaved: (user: AuthUserProfile) => void;
}) {
  const name = displayName(user);
  const src = useMyAvatarUrl();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState<'upload' | 'remove' | null>(null);
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const hasPhoto = !!user.avatarUpdatedAt;

  const choose = () => input.current?.click();

  const onFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = ''; // choosing the same file again still fires a change
    if (!file) return;
    const problem = sourceProblem(file);
    if (problem) {
      setMessage({ tone: 'danger', text: problem });
      return;
    }
    setBusy('upload');
    setMessage(null);
    try {
      onSaved(await uploadMyAvatar(await toAvatarImage(file)));
      setMessage({ tone: 'success', text: 'Profile picture updated.' });
    } catch (error) {
      setMessage({
        tone: 'danger',
        text: apiErrorMessage(error, 'The picture could not be uploaded. Please try again.')
      });
    } finally {
      setBusy(null);
    }
  };

  const remove = async () => {
    setBusy('remove');
    setMessage(null);
    try {
      onSaved(await removeMyAvatar());
      setMessage({ tone: 'success', text: 'Profile picture removed.' });
    } catch (error) {
      setMessage({
        tone: 'danger',
        text: apiErrorMessage(error, 'The picture could not be removed. Please try again.')
      });
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="flex shrink-0 flex-col items-start gap-2 sm:items-center">
      <div className="relative">
        <UserAvatar
          name={name}
          src={src}
          className={cn('size-20 text-h2', busy === 'upload' && 'opacity-60')}
        />
        <button
          type="button"
          onClick={choose}
          disabled={busy !== null}
          aria-label={hasPhoto ? 'Change profile picture' : 'Upload a profile picture'}
          title={hasPhoto ? 'Change photo' : 'Upload photo'}
          className="absolute -bottom-1 -right-1 inline-flex size-8 items-center justify-center rounded-full border-2 border-card bg-brand-primary text-brand-on shadow-sm transition-colors hover:bg-brand-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus disabled:opacity-60"
        >
          <Camera aria-hidden="true" className="size-4" />
        </button>
        <input
          ref={input}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
          onChange={(e) => void onFile(e)}
        />
      </div>
      <div className="flex gap-1">
        <Button
          type="button"
          variant="ghost"
          density="compact"
          onClick={choose}
          loading={busy === 'upload'}
          loadingLabel="Uploading…"
          disabled={busy !== null}
        >
          {hasPhoto ? 'Change photo' : 'Upload photo'}
        </Button>
        {hasPhoto ? (
          <Button
            type="button"
            variant="ghost"
            density="compact"
            onClick={() => void remove()}
            loading={busy === 'remove'}
            loadingLabel="Removing…"
            disabled={busy !== null}
            className="text-status-danger-fg"
          >
            Remove
          </Button>
        ) : null}
      </div>
      {message ? (
        <p
          role="status"
          className={cn(
            'max-w-[14rem] text-caption',
            message.tone === 'success' ? 'text-status-success-fg' : 'text-status-danger-fg'
          )}
        >
          {message.text}
        </p>
      ) : null}
    </div>
  );
}

/** Header card: who is signed in, at a glance. */
function IdentityCard({
  user,
  onSaved
}: {
  user: AuthUserProfile;
  onSaved: (user: AuthUserProfile) => void;
}) {
  const name = displayName(user);
  return (
    <SurfaceCard>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-6">
        <ProfilePhoto user={user} onSaved={onSaved} />
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="truncate text-h2 text-primary">{name}</h2>
            {user.role ? (
              <span className="rounded-full bg-brand-subtle px-2.5 py-0.5 text-caption font-semibold text-brand-primary">
                {roleLabel(user.role)}
              </span>
            ) : null}
          </div>
          <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-caption text-secondary">
            {user.username ? (
              <span className="inline-flex items-center gap-1.5">
                <UserRound aria-hidden="true" className="size-3.5" />@{user.username}
              </span>
            ) : null}
            {user.email ? (
              <span className="inline-flex min-w-0 items-center gap-1.5">
                <Mail aria-hidden="true" className="size-3.5 shrink-0" />
                <span className="truncate">{user.email}</span>
                {user.emailVerified ? (
                  <BadgeCheck
                    aria-label="Verified"
                    className="size-3.5 shrink-0 text-status-success-fg"
                  />
                ) : null}
              </span>
            ) : null}
          </p>
          <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-caption text-muted">
            {user.createdAt ? (
              <span className="inline-flex items-center gap-1.5">
                <CalendarDays aria-hidden="true" className="size-3.5" />
                Member since {formatDate(user.createdAt)}
              </span>
            ) : null}
            {user.lastLoginAt ? (
              <span className="inline-flex items-center gap-1.5">
                <Clock aria-hidden="true" className="size-3.5" />
                Last sign-in {formatDate(user.lastLoginAt, true)}
              </span>
            ) : null}
          </p>
        </div>
      </div>
    </SurfaceCard>
  );
}

function PersonalDetails({
  user,
  onSaved
}: {
  user: AuthUserProfile;
  onSaved: (user: AuthUserProfile) => void;
}) {
  const [form, setForm] = useState<ProfileForm>(() => formFrom(user));
  const [errors, setErrors] = useState<ProfileErrors>({});
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);

  useEffect(() => setForm(formFrom(user)), [user]);

  const original = useMemo(() => formFrom(user), [user]);
  const dirty = (Object.keys(form) as (keyof ProfileForm)[]).some(
    (key) => form[key] !== original[key]
  );

  const set = (key: keyof ProfileForm) => (value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
    setResult(null);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const problems = validateProfile(form);
    setErrors(problems);
    if (Object.keys(problems).length > 0) return;
    setSaving(true);
    setResult(null);
    try {
      const saved = await updateMyProfile(toUpdateRequest(form));
      onSaved(saved);
      setResult({ tone: 'success', text: 'Your details have been saved.' });
    } catch (error) {
      setResult({
        tone: 'danger',
        text: apiErrorMessage(error, 'Your details could not be saved. Please try again.')
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Section
      icon={<UserRound />}
      title="Personal details"
      description="Your name, phone number and home district."
    >
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
          <Field
            label="First name"
            required
            autoComplete="given-name"
            maxLength={100}
            value={form.firstName}
            error={errors.firstName}
            onChange={(e) => set('firstName')(e.target.value)}
          />
          <Field
            label="Last name"
            required
            autoComplete="family-name"
            maxLength={100}
            value={form.lastName}
            error={errors.lastName}
            onChange={(e) => set('lastName')(e.target.value)}
          />
          <Field
            label="Phone number"
            inputMode="tel"
            autoComplete="tel"
            placeholder="+94 77 123 4567"
            maxLength={32}
            value={form.phone}
            error={errors.phone}
            hint={PHONE_HINT[user.role ?? ''] ?? 'Optional.'}
            onChange={(e) => set('phone')(e.target.value)}
          />
          <SelectDropdown
            label="District"
            required
            searchable
            options={DISTRICT_OPTIONS}
            value={form.district}
            error={errors.district}
            placeholder="Choose a district"
            onChange={set('district')}
          />
        </div>

        {result ? (
          <StatusBanner
            tone={result.tone}
            title={result.tone === 'success' ? 'Saved' : 'Not saved'}
          >
            {result.text}
          </StatusBanner>
        ) : null}

        <div className="flex flex-col-reverse gap-3 border-t border-border-subtle pt-4 sm:flex-row sm:justify-end">
          <Button
            type="button"
            variant="ghost"
            disabled={!dirty || saving}
            onClick={() => {
              setForm(original);
              setErrors({});
              setResult(null);
            }}
          >
            Discard changes
          </Button>
          <Button type="submit" disabled={!dirty} loading={saving} loadingLabel="Saving…">
            Save changes
          </Button>
        </div>
      </form>
    </Section>
  );
}

function SignInAndSecurity({ user }: { user: AuthUserProfile }) {
  const signOut = useAuthStore((s) => s.signOut);
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [problem, setProblem] = useState<string>();
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(undefined);
    const issue = !current
      ? 'Enter your current password'
      : passwordProblem(next, current, confirm);
    setProblem(issue);
    if (issue) return;
    setSaving(true);
    try {
      await changeMyPassword(current, next);
      // The server has signed this account out everywhere, so sign in again with the new password.
      try {
        await logoutUser();
      } catch {
        // Session already revoked by the password change.
      }
      signOut();
      navigate('/login', {
        replace: true,
        state: { notice: 'Password changed. Sign in with your new password.' }
      });
    } catch (err) {
      setError(apiErrorMessage(err, 'Your password could not be changed. Please try again.'));
      setSaving(false);
    }
  };

  return (
    <Section
      icon={<ShieldCheck />}
      title="Sign-in and security"
      description="How you sign in to SwiftDrop."
    >
      <dl className="flex flex-col gap-3">
        <ReadOnlyRow
          label="Username"
          value={user.username}
          note="Used to sign in. It cannot be changed."
        />
        <ReadOnlyRow
          label="Email address"
          value={
            <span className="inline-flex flex-wrap items-center gap-2">
              {user.email}
              {user.emailVerified ? (
                <span className="rounded-full border border-status-success-border bg-status-success-bg px-2 py-0.5 text-[11px] font-semibold text-status-success-fg">
                  Verified
                </span>
              ) : null}
            </span>
          }
          note="Sign-in codes and security alerts are sent here. It cannot be changed from this page."
        />
        <ReadOnlyRow label="Account type" value={roleLabel(user.role)} />
      </dl>

      <form
        noValidate
        onSubmit={submit}
        className="flex flex-col gap-4 rounded-xl border border-border-subtle bg-canvas p-4"
      >
        <div className="flex items-center gap-2">
          <KeyRound aria-hidden="true" className="size-4 text-brand-primary" />
          <h3 className="text-body-strong text-primary">Change password</h3>
        </div>
        <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-3">
          <Field label="Current password" required>
            <Input
              variant="password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
            />
          </Field>
          <Field label="New password" required hint="8+ characters with a letter and a number">
            <Input
              variant="password"
              autoComplete="new-password"
              maxLength={72}
              value={next}
              onChange={(e) => setNext(e.target.value)}
            />
          </Field>
          <Field label="Confirm new password" required>
            <Input
              variant="password"
              autoComplete="new-password"
              maxLength={72}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </Field>
        </div>
        {problem || error ? (
          <StatusBanner tone="danger" title="Password not changed">
            {problem ?? error}
          </StatusBanner>
        ) : null}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-caption text-muted">
            You will be signed out on all devices and asked to sign in again.
          </p>
          <Button
            type="submit"
            variant="outline"
            loading={saving}
            loadingLabel="Changing…"
            disabled={!current || !next}
          >
            Change password
          </Button>
        </div>
      </form>
    </Section>
  );
}

/** "Your profile" for every workspace: identity, editable personal details and sign-in security. */
export function ProfilePage() {
  const role = useAuthStore((s) => s.role);
  const storedUser = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const queryClient = useQueryClient();

  const {
    data: user,
    isLoading,
    isError,
    refetch
  } = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: fetchCurrentUser,
    enabled: !isDevSession,
    initialData: storedUser ?? undefined,
    staleTime: 0,
    retry: false
  });

  const onSaved = (saved: AuthUserProfile) => {
    queryClient.setQueryData(['profile', 'me'], saved);
    setUser(saved); // the sidebar name updates straight away
  };

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="Your profile"
        subtitle="Manage your personal details and how you sign in."
        className="border-0 bg-transparent p-0"
      />

      <div className="flex max-w-3xl flex-col gap-5">
        {isDevSession ? (
          <StatusBanner tone="info" title="Preview session">
            Sign in with a real account to see and edit your profile.
          </StatusBanner>
        ) : isError && !user ? (
          <StatusBanner
            tone="danger"
            title="Your profile could not be loaded"
            action={
              <Button variant="outline" onClick={() => void refetch()}>
                Try again
              </Button>
            }
          />
        ) : !user || (isLoading && !storedUser) ? (
          <p className="text-caption text-secondary">Loading your profile…</p>
        ) : (
          <>
            <IdentityCard user={user} onSaved={onSaved} />
            <PersonalDetails user={user} onSaved={onSaved} />
            <SignInAndSecurity user={user} />
            <Link
              to={`${NOTIFICATIONS_PATH[role]}?tab=settings`}
              className="flex items-center gap-3 rounded-2xl border border-border-subtle bg-card px-4 py-3 text-body text-primary transition-colors hover:bg-elevated"
            >
              <Bell aria-hidden="true" className="size-4 text-brand-primary" />
              <span className="flex-1">Notification settings</span>
              <span aria-hidden="true" className="text-muted">
                ›
              </span>
            </Link>
            {/* Only customers can erase their own account; staff accounts are managed by an admin. */}
            {user.role === 'CUSTOMER' ? <DeleteAccountCard /> : null}
          </>
        )}
      </div>
    </div>
  );
}

export default ProfilePage;
