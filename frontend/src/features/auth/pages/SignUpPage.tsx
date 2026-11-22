import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { AtSign, Lock, Mail, UserPlus, UserRound } from 'lucide-react';
import { Alert } from '@/design-system/components/Alert/Alert';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input } from '@/design-system/primitives/Input';
import { registerUser } from '@/services/api/authApi';
import { parseApiError } from '@/services/api/apiError';
import { validatePassword } from '@/features/auth/passwordPolicy';
import {
  PasswordMatchMeter,
  PasswordStrengthMeter
} from '@/features/auth/components/PasswordStrengthMeter';
import { USERNAME_PATTERN, useUsernameAvailability } from '@/features/auth/useUsernameAvailability';
import { useSignedInRedirect } from '@/features/auth/useSignedInRedirect';
import {
  AuthDivider,
  AuthLink,
  AuthShell,
  authFieldClass,
  FieldIcon,
  GoogleTile
} from '@/features/auth/components/AuthShell';
import { cn } from '@/utils/cn';

/** Shape check only; the server is the authority. */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

type FieldName = 'firstName' | 'lastName' | 'email' | 'username' | 'password' | 'confirm';

/**
 * Self-service sign-up. With {@code business}, it is step 1 of 2 for a merchant: the account is created
 * with the MERCHANT role, and after email verification the business details are submitted for the
 * district dispatcher to approve.
 */
export function SignUpPage({ business = false }: { business?: boolean }) {
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // A field shows its error once the user has left it, or after they press "Create account".
  const [touched, setTouched] = useState<Partial<Record<FieldName, boolean>>>({});
  const [submitted, setSubmitted] = useState(false);
  const usernameStatus = useUsernameAvailability(username);
  const signedInHome = useSignedInRedirect();

  if (signedInHome) return <Navigate to={signedInHome} replace />;

  /** The current problem with each field, or undefined when it is fine. Recomputed on every change. */
  const problems: Record<FieldName, string | undefined> = {
    firstName: firstName.trim() ? undefined : 'First name is required.',
    lastName: lastName.trim() ? undefined : 'Last name is required.',
    email: !email.trim()
      ? 'Email address is required.'
      : EMAIL_PATTERN.test(email.trim())
        ? undefined
        : 'Enter a valid email address, like name@example.com.',
    username: !username.trim()
      ? 'Username is required.'
      : !USERNAME_PATTERN.test(username.trim())
        ? 'Use 3-50 letters, digits, dots, underscores or hyphens.'
        : usernameStatus === 'taken'
          ? 'This username is already taken.'
          : undefined,
    password: !password
      ? 'Password is required.'
      : (validatePassword(password, { username: username.trim(), email: email.trim() }) ??
        undefined),
    confirm: !confirm
      ? 'Please confirm your password.'
      : confirm !== password
        ? 'Passwords do not match.'
        : undefined
  };

  // "Taken" is shown live as soon as the check answers; everything else waits for blur or submit.
  const errorFor = (name: FieldName) =>
    submitted || touched[name] || (name === 'username' && usernameStatus === 'taken')
      ? problems[name]
      : undefined;
  // The filled style hides the border; a field with an error keeps the Input's red error border.
  const fieldClass = (name: FieldName) =>
    errorFor(name) ? 'bg-elevated focus-within:bg-card' : authFieldClass;
  const touch = (name: FieldName) => () => setTouched((current) => ({ ...current, [name]: true }));

  const usernameHint =
    usernameStatus === 'checking'
      ? 'Checking availability…'
      : usernameStatus === 'available'
        ? 'This username is available.'
        : undefined;

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitted(true);

    const firstProblem = (Object.keys(problems) as FieldName[]).find((name) => problems[name]);
    if (firstProblem) {
      // Take the user straight to the first field that needs attention.
      document.getElementById(`signup-${firstProblem}`)?.focus();
      return;
    }

    setLoading(true);
    try {
      const normalizedEmail = email.trim().toLowerCase();
      await registerUser(
        {
          username: username.trim(),
          email: normalizedEmail,
          password,
          firstName: firstName.trim(),
          lastName: lastName.trim()
          // No district: each order's pickup district decides who handles it, and a business gives
          // its pickup district on the application in step 2.
        },
        business ? 'MERCHANT' : 'CUSTOMER'
      );
      // The account is unverified until the emailed code is confirmed; after that the user signs in.
      navigate(`/verify-email?identifier=${encodeURIComponent(normalizedEmail)}`, {
        replace: true
      });
    } catch (requestError) {
      const parsed = parseApiError(requestError);
      setError(
        parsed.message ??
          (parsed.status
            ? 'Registration failed. Please try again.'
            : 'Unable to reach the server. Check your connection and try again.')
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthShell
      icon={<UserPlus />}
      width="lg"
      title={business ? 'Create your business account' : 'Create your account'}
      description={business ? 'Step 1 of 2: your sign-in details.' : undefined}
      below={
        <>
          Already have an account? <AuthLink to="/login">Sign in</AuthLink>
        </>
      }
    >
      {error ? (
        <Alert tone="danger" title="We couldn't create your account" className="mb-5">
          {error}
        </Alert>
      ) : null}

      <form className="flex flex-col gap-4" onSubmit={onSubmit} noValidate>
        <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
          <Field label="First name" required error={errorFor('firstName')}>
            <Input
              id="signup-firstName"
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
              onBlur={touch('firstName')}
              autoComplete="given-name"
              maxLength={100}
              required
              autoFocus
              className={fieldClass('firstName')}
              leading={
                <FieldIcon>
                  <UserRound />
                </FieldIcon>
              }
            />
          </Field>
          <Field label="Last name" required error={errorFor('lastName')}>
            <Input
              id="signup-lastName"
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
              onBlur={touch('lastName')}
              autoComplete="family-name"
              maxLength={100}
              required
              className={fieldClass('lastName')}
              leading={
                <FieldIcon>
                  <UserRound />
                </FieldIcon>
              }
            />
          </Field>
        </div>

        <Field label="Email address" required error={errorFor('email')}>
          <Input
            id="signup-email"
            inputMode="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            onBlur={touch('email')}
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={320}
            required
            className={fieldClass('email')}
            leading={
              <FieldIcon>
                <Mail />
              </FieldIcon>
            }
          />
        </Field>

        <div className="grid grid-cols-1 items-start gap-4">
          <Field
            label="Username"
            required
            error={errorFor('username')}
            hint={usernameHint}
            className={cn(
              usernameStatus === 'available' && '[&>span:last-child]:text-status-success'
            )}
          >
            <Input
              id="signup-username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              onBlur={touch('username')}
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={50}
              required
              className={fieldClass('username')}
              leading={
                <FieldIcon>
                  <AtSign />
                </FieldIcon>
              }
            />
          </Field>

        </div>

        <div className="flex flex-col gap-2">
          <Field label="Password" required error={errorFor('password')}>
            <Input
              id="signup-password"
              variant="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              onBlur={touch('password')}
              autoComplete="new-password"
              placeholder="At least 8 characters"
              maxLength={72}
              required
              className={fieldClass('password')}
              leading={
                <FieldIcon>
                  <Lock />
                </FieldIcon>
              }
            />
          </Field>
          <PasswordStrengthMeter
            password={password}
            context={{ username: username.trim(), email: email.trim() }}
          />
        </div>

        <div className="flex flex-col gap-2">
          <Field label="Confirm password" required error={errorFor('confirm')}>
            <Input
              id="signup-confirm"
              variant="password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              onBlur={touch('confirm')}
              autoComplete="new-password"
              placeholder="Repeat your password"
              maxLength={72}
              required
              className={fieldClass('confirm')}
              leading={
                <FieldIcon>
                  <Lock />
                </FieldIcon>
              }
            />
          </Field>
          {errorFor('confirm') ? null : (
            <PasswordMatchMeter password={password} confirm={confirm} />
          )}
        </div>

        <Button
          type="submit"
          className="mt-2 h-12 w-full rounded-xl shadow-ambient"
          loading={loading}
          loadingLabel="Creating account"
        >
          Create account
        </Button>
      </form>

      {/* Google sign-up always creates a customer account, so businesses sign up with the form only. */}
      {!business && (
        <>
          <AuthDivider label="Or sign up with" />
          <GoogleTile label="Sign up with Google" />
        </>
      )}
    </AuthShell>
  );
}

export default SignUpPage;
