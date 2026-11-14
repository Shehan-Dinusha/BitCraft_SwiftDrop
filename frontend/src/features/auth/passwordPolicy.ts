/**
 * Client mirror of PasswordPolicy in auth-iam-service. The server stays the authority; this gives
 * instant feedback and drives the strength meter.
 */

export type PasswordContext = { username?: string; email?: string };

const MIN_LENGTH = 8;
const MAX_LENGTH = 72;
const MIN_IDENTITY_FRAGMENT = 3;

/** Same list as the server: frequently breached passwords that pass the composition rule. */
const COMMON_PASSWORDS = new Set([
  'password1',
  'password12',
  'password123',
  'password1234',
  'passw0rd',
  'p@ssw0rd',
  'p@ssword1',
  'qwerty123',
  'qwerty1234',
  'qwertyuiop1',
  '1q2w3e4r',
  '1q2w3e4r5t',
  'zaq12wsx',
  'abc12345',
  'abcd1234',
  'a1234567',
  'a12345678',
  '123456789a',
  '12345678a',
  '1234567890a',
  'letmein1',
  'welcome1',
  'welcome123',
  'iloveyou1',
  'admin123',
  'admin1234',
  'administrator1',
  'changeme1',
  'default1',
  'test1234',
  'testing123',
  'user1234',
  'login123',
  'master123',
  'monkey123',
  'dragon123',
  'sunshine1',
  'princess1',
  'football1',
  'baseball1',
  'trustno1',
  'shadow123',
  'superman1',
  'starwars1',
  'computer1',
  'secret123',
  'summer2024',
  'winter2024',
  'summer2025',
  'winter2025',
  'summer2026',
  'winter2026',
  'colombo123',
  'srilanka1',
  'srilanka123'
]);

function containsIdentity(lowerPassword: string, identity?: string): boolean {
  const fragment = identity?.trim().toLowerCase() ?? '';
  return fragment.length >= MIN_IDENTITY_FRAGMENT && lowerPassword.includes(fragment);
}

/** Returns the first rule the password breaks, or `null` when the server will accept it. */
export function validatePassword(value: string, context: PasswordContext = {}): string | null {
  if (value.length < MIN_LENGTH) return `Password must be at least ${MIN_LENGTH} characters.`;
  if (value.length > MAX_LENGTH) return `Password must not exceed ${MAX_LENGTH} characters.`;
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return 'Password must contain at least one letter and one digit.';
  }
  const lower = value.toLowerCase();
  if (COMMON_PASSWORDS.has(lower))
    return 'This password is too common. Choose a less predictable one.';
  if (containsIdentity(lower, context.username)) return 'Password must not contain your username.';
  const email = context.email ?? '';
  const localPart = email.includes('@') ? email.slice(0, email.indexOf('@')) : email;
  if (containsIdentity(lower, localPart)) return 'Password must not contain your email address.';
  return null;
}

export type StrengthScore = 0 | 1 | 2 | 3 | 4;

export type PasswordStrength = {
  /** 0 = empty, 1 = not accepted, 2 = fair, 3 = good, 4 = strong. */
  score: StrengthScore;
  label: string;
  /** The rule that blocks the password (score 1), or ideas to make an accepted one stronger. */
  hints: string[];
};

/** Strength for the meter. Anything the server would reject scores 1, whatever its length. */
export function passwordStrength(value: string, context: PasswordContext = {}): PasswordStrength {
  if (!value) return { score: 0, label: '', hints: [] };

  const blocked = validatePassword(value, context);
  if (blocked) return { score: 1, label: 'Too weak', hints: [blocked] };

  const hasLower = /[a-z]/.test(value);
  const hasUpper = /[A-Z]/.test(value);
  const hasSymbol = /[^A-Za-z0-9]/.test(value);
  const variety = [hasLower, hasUpper, /\d/.test(value), hasSymbol].filter(Boolean).length;
  const length = value.length;

  let score: StrengthScore = 2;
  if ((length >= 12 && variety >= 3) || length >= 16) score = 3;
  if ((length >= 14 && variety === 4) || (length >= 20 && variety >= 3)) score = 4;

  const hints: string[] = [];
  if (score < 4) {
    if (length < 12) hints.push('Use 12 or more characters.');
    if (!(hasLower && hasUpper)) hints.push('Mix upper and lower case letters.');
    if (!hasSymbol) hints.push('Add a symbol such as ! or #.');
  }
  const label = score === 4 ? 'Strong' : score === 3 ? 'Good' : 'Fair';
  return { score, label, hints };
}
