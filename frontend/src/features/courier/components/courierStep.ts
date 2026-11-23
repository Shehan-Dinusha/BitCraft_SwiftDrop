import type { DeliverySessionResponse, DeliveryState } from '@/services/api/deliveryApi';
import { courierNextStep, crossesDistricts } from '@/types/fsm';
import { districtLabel } from '@/features/profile/profileFormat';

export type CourierStepIcon = 'pickup' | 'trip' | 'arrive' | 'hub' | 'collect' | 'done';

/** The one step the courier takes next, as the slide shows it. */
export interface CourierStep {
  target: DeliveryState;
  /** What the slide says, e.g. "Slide to confirm pickup". */
  label: string;
  /** Shown once the slide is far enough to let go. */
  confirm: string;
  /** Saved with the step. */
  note: string;
  icon: CourierStepIcon;
  /** Needs a photo: the slide opens the camera, which saves the photo and the step together. */
  photo?: boolean;
}

/** The courier's next step on this parcel, or null when it is not theirs to move. */
export function courierStep(session: DeliverySessionResponse): CourierStep | null {
  const state = session.currentState as DeliveryState | undefined;
  const viaHubs = crossesDistricts(session);
  const target = courierNextStep(state, viaHubs);
  if (!target) return null;
  const pickupHub = `the ${districtLabel(session.pickupDistrict)} hub`;
  const deliveryHub = `the ${districtLabel(session.deliveryDistrict)} hub`;
  switch (target) {
    case 'PICKED_UP':
      return {
        target,
        label: 'Slide to confirm pickup',
        confirm: 'Release to pick up',
        note: 'Courier picked up the parcel',
        icon: 'pickup'
      };
    case 'AT_ORIGIN_HUB':
      return {
        target,
        label: `Slide when handed in at ${pickupHub}`,
        confirm: 'Release to hand in',
        note: 'Courier handed the parcel in at the hub',
        icon: 'hub'
      };
    case 'IN_TRANSIT':
      return {
        target,
        label: 'Slide to start the trip',
        confirm: 'Release to start',
        note: 'Courier left for the delivery',
        icon: 'trip'
      };
    case 'OUT_FOR_DELIVERY':
      return state === 'AT_DESTINATION_HUB'
        ? {
            target,
            label: `Slide when collected from ${deliveryHub}`,
            confirm: 'Release to collect',
            note: 'Courier collected the parcel from the hub',
            icon: 'collect'
          }
        : {
            target,
            label: 'Slide when you arrive',
            confirm: 'Release to confirm arrival',
            note: 'Courier arrived at the recipient',
            icon: 'arrive'
          };
    case 'DELIVERED':
      return {
        target,
        label: 'Slide to take the photo and deliver',
        confirm: 'Release to take the photo',
        note: 'Delivered with a photo',
        icon: 'done',
        photo: true
      };
    case 'HELD_AT_HUB':
      return {
        target,
        label: `Slide when handed in at ${deliveryHub}`,
        confirm: 'Release to hand in',
        note: 'Courier handed the parcel in after a failed delivery',
        icon: 'hub'
      };
    case 'OUT_FOR_RETURN':
      return {
        target,
        label: `Slide when collected from ${pickupHub}`,
        confirm: 'Release to collect',
        note: 'Courier collected the return from the hub',
        icon: 'collect'
      };
    case 'RETURNED':
      return {
        target,
        label: 'Slide to take the photo and return it',
        confirm: 'Release to take the photo',
        note: 'Returned to the sender with a photo',
        icon: 'done',
        photo: true
      };
    default:
      return null;
  }
}
