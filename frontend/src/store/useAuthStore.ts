import { create } from 'zustand';
import type { PersonaRole } from '@/types/contracts';
type S = {
  token: string | null;
  role: PersonaRole;
  setSession: (t: string, r: PersonaRole) => void;
  signOut: () => void;
};
export const useAuthStore = create<S>((set) => ({
  token: null,
  role: 'CUSTOMER',
  setSession: (token, role) => set({ token, role }),
  signOut: () => set({ token: null })
}));
