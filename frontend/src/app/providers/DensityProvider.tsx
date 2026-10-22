import { createContext, useContext, useMemo, ReactNode } from 'react';
import { densityForRole, densityTokens } from '@/design-system/tokens/density';
import { useAuthStore } from '@/store/useAuthStore';

type DensityValue = (typeof densityTokens)[keyof typeof densityTokens];
const C = createContext<DensityValue>(densityTokens.comfortable);

export function DensityProvider({ children }: { children: ReactNode }) {
  const role = useAuthStore(s => s.role);
  const value = useMemo(() => densityTokens[densityForRole[role]], [role]);
  return <C.Provider value={value}>{children}</C.Provider>;
}

export const useDensity = () => useContext(C);
