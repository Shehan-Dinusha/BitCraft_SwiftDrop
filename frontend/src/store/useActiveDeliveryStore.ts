import { create } from 'zustand';
import type { DeliveryState } from '@/types/contracts';
type S = {
  state: DeliveryState;
  setState: (s: DeliveryState) => void;
};
export const useActiveDeliveryStore = create<S>((set) => ({
  state: 'CREATED',
  setState: (state) => set({ state })
}));
