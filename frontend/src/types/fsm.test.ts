import { describe, expect, it } from 'vitest';
import { canTransition, courierNextStep, crossesDistricts, isOpenForCourier } from './fsm';

describe('delivery routes', () => {
  it('tells a cross-district parcel from a same-district one', () => {
    expect(crossesDistricts({ pickupDistrict: 'COLOMBO', deliveryDistrict: 'KANDY' })).toBe(true);
    expect(crossesDistricts({ pickupDistrict: 'COLOMBO', deliveryDistrict: 'COLOMBO' })).toBe(false);
    expect(crossesDistricts({ pickupDistrict: 'COLOMBO', deliveryDistrict: null })).toBe(false);
  });

  it('gives a same-district courier the direct steps', () => {
    expect(courierNextStep('ASSIGNED', false)).toBe('PICKED_UP');
    expect(courierNextStep('PICKED_UP', false)).toBe('IN_TRANSIT');
    expect(courierNextStep('IN_TRANSIT', false)).toBe('OUT_FOR_DELIVERY');
    expect(courierNextStep('OUT_FOR_DELIVERY', false)).toBe('DELIVERED');
  });

  it('sends a cross-district parcel to the hub and never offers couriers the dispatcher hub steps', () => {
    expect(courierNextStep('PICKED_UP', true)).toBe('AT_ORIGIN_HUB');
    expect(courierNextStep('AT_ORIGIN_HUB', true)).toBeNull();
    expect(courierNextStep('IN_TRANSIT', true)).toBeNull();
    expect(courierNextStep('AT_DESTINATION_HUB', true)).toBe('OUT_FOR_DELIVERY');
  });

  it('brings a failed parcel to the hub, and a return to the sender', () => {
    expect(courierNextStep('ATTEMPT_FAILED', true)).toBe('HELD_AT_HUB');
    expect(courierNextStep('HELD_AT_HUB', true)).toBeNull();
    expect(courierNextStep('RETURN_AT_HUB', false)).toBe('OUT_FOR_RETURN');
    expect(courierNextStep('OUT_FOR_RETURN', false)).toBe('RETURNED');
    expect(canTransition('HELD_AT_HUB', 'OUT_FOR_DELIVERY')).toBe(false);
    expect(isOpenForCourier('ATTEMPT_FAILED')).toBe(true);
    expect(isOpenForCourier('RETURNED')).toBe(false);
  });

  it('mirrors the delivery service transitions', () => {
    expect(canTransition('PICKED_UP', 'AT_ORIGIN_HUB')).toBe(true);
    expect(canTransition('AT_ORIGIN_HUB', 'OUT_FOR_DELIVERY')).toBe(false);
    expect(canTransition('IN_TRANSIT', 'AT_DESTINATION_HUB')).toBe(true);
    expect(canTransition('AT_DESTINATION_HUB', 'OUT_FOR_DELIVERY')).toBe(true);
  });
});
