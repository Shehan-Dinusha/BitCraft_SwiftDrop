import {createContext,useContext,type ReactNode} from 'react';
import {densityRecipes,type DensityMode} from '@/design-system/tokens/density';

const DensityModeContext=createContext<DensityMode>('comfortable');

export function DensityProvider({density,children}:{density:DensityMode;children:ReactNode}){
  return <DensityModeContext.Provider value={density}>{children}</DensityModeContext.Provider>;
}

export const useDensityMode=()=>useContext(DensityModeContext);
export const useDensity=()=>densityRecipes[useDensityMode()];
