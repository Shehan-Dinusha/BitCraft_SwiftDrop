import type {PersonaRole} from '@/types/contracts';

export type DensityMode='compact'|'touch'|'comfortable';

export interface DensityRecipe {
  readonly className:string;
  readonly minTarget:number;
  readonly control:string;
  readonly fieldControl:string;
  readonly target:string;
  readonly icon:string;
  readonly label:string;
  readonly caption:string;
  readonly menuItem:string;
  /** Cell padding for dense operational tables. */
  readonly gridCell:string;
  readonly radius:string;
  readonly gap:string;
  readonly stackGap:string;
  readonly surfacePadding:string;
  readonly surfaceSection:string;
  readonly slideTrack:number;
  /** Hick's Law ceiling for simultaneous actions. */
  readonly maxActions:number;
}

export const densityRecipes:Record<DensityMode,DensityRecipe>={
  touch:{
    className:'density-touch',
    minTarget:56,
    control:'h-14 min-h-14 px-5 py-4',
    fieldControl:'h-14 min-h-14 px-4 py-3',
    target:'min-h-14 min-w-14',
    icon:'size-6',
    label:'text-base leading-6',
    caption:'text-sm leading-5',
    menuItem:'px-3 py-2.5',
    gridCell:'px-4 py-3',
    radius:'rounded-xl',
    gap:'gap-3',
    stackGap:'gap-4',
    surfacePadding:'p-5',
    surfaceSection:'px-5 py-4',
    slideTrack:64,
    maxActions:2
  },
  comfortable:{
    className:'density-comfortable',
    minTarget:44,
    control:'h-11 min-h-11 px-4 py-3',
    fieldControl:'h-11 min-h-11 px-4 py-3',
    target:'min-h-11 min-w-11',
    icon:'size-5',
    label:'text-sm leading-5',
    caption:'text-xs leading-4',
    menuItem:'px-3 py-2',
    gridCell:'px-3 py-2',
    radius:'rounded-lg',
    gap:'gap-2',
    stackGap:'gap-3',
    surfacePadding:'p-4',
    surfaceSection:'px-4 py-3',
    slideTrack:48,
    maxActions:3
  },
  compact:{
    className:'density-compact',
    minTarget:36,
    control:'h-9 min-h-9 px-3 py-2',
    fieldControl:'h-9 min-h-9 px-3 py-2',
    target:'min-h-9 min-w-9',
    icon:'size-[18px]',
    label:'text-xs leading-4',
    caption:'text-xs leading-4',
    menuItem:'px-2 py-1.5',
    gridCell:'px-2 py-1.5',
    radius:'rounded-md',
    gap:'gap-1.5',
    stackGap:'gap-2',
    surfacePadding:'p-3',
    surfaceSection:'px-3 py-2',
    slideTrack:40,
    maxActions:4
  }
};

export const densityForRole:Record<PersonaRole,DensityMode>={ADMIN_OPS:'compact',MERCHANT:'compact',COURIER:'touch',CUSTOMER:'comfortable'};

export const densityModes:DensityMode[]=['touch','comfortable','compact'];
