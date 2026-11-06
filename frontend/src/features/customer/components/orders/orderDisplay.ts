import type { Speed } from '@/design-system';
import type { CustomerOrderResponse } from '@/services/api/orderApi';

/** Maps the order's speed onto the design system's `SpeedTag` values. */
export const toSpeed = (speed: CustomerOrderResponse['orderSpeed']): Speed =>
  speed === 'EXPRESS' ? 'express' : speed === 'SAME_DAY' ? 'same-day' : 'standard';

/** Customer-facing service names. */
export const SPEED_LABEL: Record<NonNullable<CustomerOrderResponse['orderSpeed']>, string> = {
  STANDARD: 'Standard',
  EXPRESS: 'Express',
  SAME_DAY: 'Same day'
};
