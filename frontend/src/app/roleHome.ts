import type { PersonaRole } from '@/types/contracts';

/** Landing route for each persona after sign-in or a persona switch. */
export const roleHome: Record<PersonaRole, string> = {
  CUSTOMER: '/customer',
  COURIER: '/courier',
  MERCHANT: '/merchant',
  ADMIN_OPS: '/ops'
};
