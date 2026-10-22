import {useMemo,type ReactNode} from 'react';
import {DensityProvider as DesignSystemDensityProvider,useDensity} from '@/design-system/providers/DensityProvider';
import {densityForRole} from '@/design-system/tokens/density';
import {useAuthStore} from '@/store/useAuthStore';

export function DensityProvider({children}:{children:ReactNode}){
  const role=useAuthStore(s=>s.role);
  const density=useMemo(()=>densityForRole[role],[role]);
  return <DesignSystemDensityProvider density={density}>{children}</DesignSystemDensityProvider>;
}

export {useDensity};
