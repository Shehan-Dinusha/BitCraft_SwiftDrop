import { create } from 'zustand';
import type { DeliveryState, LocationUpdatedEvent } from '@/types/contracts';
type S = {
  state: DeliveryState;
  location: LocationUpdatedEvent | null;
  setState: (s: DeliveryState) => void;
  setLocation: (l: LocationUpdatedEvent) => void;
};
export const useActiveDeliveryStore = create<S>((set) => ({
  state: 'CREATED',
  location: null,
  setState: (state) => set({ state }),
  setLocation: (location) => set({ location })
}));
