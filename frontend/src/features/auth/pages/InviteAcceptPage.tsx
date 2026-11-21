import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, UserPlus } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { acceptInvite, previewInvite, type InvitePreview } from '@/services/api/authApi';
import { parseApiError } from '@/services/api/apiError';
import { useAuthStore, personaForRole } from '@/store/useAuthStore';
import { roleHome } from '@/app/roleHome';
import { validatePassword } from '@/features/auth/passwordPolicy';
import {
  PasswordMatchMeter,
  PasswordStrengthMeter
} from '@/features/auth/components/PasswordStrengthMeter';
import {
  AuthLink,
  AuthShell,
  authFieldClass,
  FieldIcon,
  SrLabel
} from '@/features/auth/components/AuthShell';

const ROLE_NAMES: Record<string, string> = {
  COURIER: 'courier',
  DISPATCHER: 'dispatcher',
  SYSTEM_ADMIN: 'administrator'
};

/** Reads the invite token from the link's fragment (#token=...), which never reaches a server. */
function readToken(): string {
  return new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token') ?? '';
}

function districtName(value?: string | null) {
  return value
    ? value
        .toLowerCase()
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ')
    : '';
}

/**
 * Landing page of the staff invite email. The person chooses their password and is signed in;
 * couriers continue to the vehicle form.
 */
export function InviteAcceptPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [token] = useState(readToken);
  const [invite, setInvite] = useState<InvitePreview | null>(null);
  const [checking, setChecking] = useState(true);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Keep the secret out of the address bar and browser history once it has been read.
    if (window.location.hash) {
      window.history.replaceState(null, '', window.location.pathname);
    }
    if (!token) {
      setLinkError('This page needs the link from your invite email. Open the email and tap the button again.');
      setChecking(false);
      return;
    }
    let active = true;
    previewInvite(token)
      .then((preview) => active && setInvite(preview))
      .catch((requestError) => {
        if (!active) return;
        setLinkError(
          parseApiError(requestError).message ??
            'We could not check this invite. Check your connection and try again.'
        );
      })
      .finally(() => active && setChecking(false));
    return () => {
      active = false;
    };
  }, [token]);

  const passwordContext = { username: invite?.username ?? undefined };

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    const policyError = validatePassword(password, passwordContext);
    if (policyError) {
      setError(policyError);
      return;
    }
    setSaving(true);
    try {
      const tokens = await acceptInvite(token, password);
      setAuth(tokens);
      navigate(tokens.role === 'COURIER' ? '/courier/vehicle' : roleHome[personaForRole(tokens.role)], {
        replace: true
      });
    } catch (requestError) {
      setError(parseApiError(requestError).message ?? 'Could not set your password. Try again.');
    } finally {
      setSaving(false);
    }
  }

  const roleName = ROLE_NAMES[invite?.role ?? ''] ?? 'team member';
  const district = districtName(invite?.district);

  return (
    <AuthShell
      icon={<UserPlus />}
      title={invite?.firstName ? `Welcome, ${invite.firstName}` : 'Set up your account'}
      description={
        invite
          ? `You are joining SwiftDrop as a ${roleName}${district ? ` in ${district}` : ''}. Choose a password to finish.`
          : 'Choose a password to start using SwiftDrop.'
      }
      below={
        <>
          Already set up? <AuthLink to="/login">Sign in</AuthLink>
        </>
      }
    >
      {checking ? (
        <p role="status" className="py-6 text-center text-body text-secondary">
          Checking your invite…
        </p>
      ) : linkError ? (
        <Alert tone="danger" title="This invite link cannot be used">
          {linkError} Ask your dispatcher to send a new invite.
        </Alert>
      ) : (
        <>
          {error ? (
            <Alert tone="danger" title="Password not set" className="mb-5">
              {error}
            </Alert>
          ) : null}
          <div className="mb-4 rounded-xl border border-border-subtle bg-canvas px-4 py-3">
            <p className="text-caption text-secondary">Your username</p>
            <p className="font-mono text-body-strong text-primary">{invite?.username}</p>
          </div>
          <form className="flex flex-col gap-3" onSubmit={onSubmit}>
            {/* Lets password managers save the new password against the right username. */}
            <input type="text" name="username" autoComplete="username" value={invite?.username ?? ''} readOnly hidden />
            <div className="flex flex-col gap-2">
              <SrLabel htmlFor="invite-password">New password</SrLabel>
              <Input
                id="invite-password"
                variant="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="new-password"
                placeholder="New password"
                maxLength={72}
                required
                autoFocus
                className={authFieldClass}
                leading={
                  <FieldIcon>
                    <Lock />
                  </FieldIcon>
                }
              />
              <PasswordStrengthMeter password={password} context={passwordContext} />
            </div>
            <div className="flex flex-col gap-2">
              <SrLabel htmlFor="invite-confirm">Confirm new password</SrLabel>
              <Input
                id="invite-confirm"
                variant="password"
                value={confirm}
                onChange={(event) => setConfirm(event.target.value)}
                autoComplete="new-password"
                placeholder="Confirm new password"
                maxLength={72}
                required
                className={authFieldClass}
                leading={
                  <FieldIcon>
                    <Lock />
                  </FieldIcon>
                }
              />
              <PasswordMatchMeter password={password} confirm={confirm} />
            </div>
            <Button
              type="submit"
              className="mt-3 h-12 w-full rounded-xl shadow-ambient"
              loading={saving}
              loadingLabel="Setting up your account"
            >
              Set password and continue
            </Button>
          </form>
        </>
      )}
    </AuthShell>
  );
}

export default InviteAcceptPage;
