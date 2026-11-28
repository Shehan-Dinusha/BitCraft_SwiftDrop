import type { PersonaRole } from '@/types/contracts';

/** Landing route for each persona after sign-in or a persona switch. */
export const roleHome: Record<PersonaRole, string> = {
  CUSTOMER: '/customer',
  COURIER: '/courier',
  MERCHANT: '/merchant',
  ADMIN_OPS: '/ops'
};

/**
 * A system admin's home. The admin oversees the platform (analytics, users, cash, calendar, prices)
 * and does not do a dispatcher's daily work, so the control tower is not theirs.
 */
export const SYSTEM_ADMIN_HOME = '/ops/analytics';

/** Home route for a persona, using the backend role to tell a system admin from a dispatcher. */
export function homeFor(persona: PersonaRole, backendRole?: string | null): string {
  return persona === 'ADMIN_OPS' && backendRole === 'SYSTEM_ADMIN'
    ? SYSTEM_ADMIN_HOME
    : roleHome[persona];
}
