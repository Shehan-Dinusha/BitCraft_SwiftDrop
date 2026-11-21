import type { AuthUserProfile, UpdateProfileInput } from '@/services/api/authApi';
import type { PersonaRole } from '@/types/contracts';

/** Profile page of each workspace. */
export const PROFILE_PATH: Record<PersonaRole, string> = {
  CUSTOMER: '/customer/profile',
  COURIER: '/courier/profile',
  MERCHANT: '/merchant/profile',
  ADMIN_OPS: '/ops/profile'
};

/** "Nimal Perera", or the username when no name is stored. */
export function displayName(user: Pick<AuthUserProfile, 'firstName' | 'lastName' | 'username'> | null | undefined): string {
  const name = `${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim();
  return name || user?.username || 'Your account';
}

const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: 'Customer',
  COURIER: 'Courier',
  MERCHANT: 'Merchant',
  DISPATCHER: 'Dispatcher',
  SYSTEM_ADMIN: 'System admin'
};

export function roleLabel(role?: string | null): string {
  return role ? ROLE_LABELS[role] ?? role : '';
}

/** `NUWARA_ELIYA` -> `Nuwara Eliya`. */
export function districtLabel(value?: string | null): string {
  return (value ?? '')
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join(' ');
}

/** Spaces, dashes and brackets people type in phone numbers are dropped: "077 123-4567" -> "0771234567". */
export function normalizePhone(value: string): string {
  return value.replace(/[\s\-()]/g, '');
}

export type ProfileForm = { firstName: string; lastName: string; phone: string; district: string };
export type ProfileErrors = Partial<Record<keyof ProfileForm, string>>;

const PHONE = /^\+?[0-9]{7,15}$/;

/** Same rules as the server (UpdateProfileRequest), so most mistakes are caught before saving. */
export function validateProfile(form: ProfileForm): ProfileErrors {
  const errors: ProfileErrors = {};
  if (!form.firstName.trim()) errors.firstName = 'Enter your first name';
  else if (form.firstName.trim().length > 100) errors.firstName = 'Use 100 characters or fewer';
  if (!form.lastName.trim()) errors.lastName = 'Enter your last name';
  else if (form.lastName.trim().length > 100) errors.lastName = 'Use 100 characters or fewer';
  const phone = normalizePhone(form.phone);
  if (phone && !PHONE.test(phone)) errors.phone = 'Enter 7 to 15 digits, optionally starting with +';
  return errors;
}

export function toUpdateRequest(form: ProfileForm): UpdateProfileInput {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    phone: normalizePhone(form.phone),
    // Customers and merchants have no district; staff send their assigned one back unchanged.
    district: (form.district || undefined) as UpdateProfileInput['district']
  };
}

/** Same rules as the server's password policy: 8 to 72 characters with a letter and a digit. */
export function passwordProblem(next: string, current: string, confirm: string): string | undefined {
  if (next.length < 8 || next.length > 72) return 'Use 8 to 72 characters';
  if (!/[A-Za-z]/.test(next) || !/[0-9]/.test(next)) return 'Include at least one letter and one number';
  if (next === current) return 'Choose a password different from your current one';
  if (confirm !== next) return 'The passwords do not match';
  return undefined;
}

export function formatDate(value?: string | null, withTime = false): string {
  if (!value) return '';
  return new Date(value).toLocaleString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    ...(withTime ? { hour: 'numeric', minute: '2-digit' } : {})
  });
}
