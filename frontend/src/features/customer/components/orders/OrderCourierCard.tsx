import { useQuery } from '@tanstack/react-query';
import { Bike } from 'lucide-react';
import { SurfaceCard } from '@/design-system';
import { dispatchApi } from '@/services/api/dispatchApi';
import { vehicleLabel } from '@/utils/vehicles';
import type { PublicOrderStatus } from './orderDisplay';

/** While a courier is on the way, the customer can recognise them by name and number plate. */
const WITH_COURIER: PublicOrderStatus[] = ['PICKUP_ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'RETURNING'];

/**
 * The courier handling the parcel: first name and vehicle only (never a phone number). Shown only
 * while a courier is assigned; if the details cannot be loaded the card is simply left out.
 */
export function OrderCourierCard({ orderId, status }: { orderId: string; status: PublicOrderStatus }) {
  const active = WITH_COURIER.includes(status);
  const query = useQuery({
    queryKey: ['order-courier', orderId, status],
    queryFn: () => dispatchApi.getOrderCourier(orderId),
    enabled: active,
    retry: false
  });
  const courier = query.data;
  if (!active || !courier) return null;

  const description = [courier.vehicleColour, courier.vehicleModel].filter(Boolean).join(' ');

  return (
    <SurfaceCard>
      <div className="flex items-center gap-4">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-brand-subtle text-brand-primary">
          <Bike className="size-6" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <p className="text-caption text-secondary">Your courier</p>
          <p className="text-body-strong text-primary">
            {courier.firstName ?? 'SwiftDrop courier'}
            {courier.vehicleType ? ` · ${vehicleLabel(courier.vehicleType)}` : ''}
          </p>
          {courier.plateNumber ? (
            <p className="text-body text-secondary">
              <span className="font-mono font-semibold tabular-nums text-primary">{courier.plateNumber}</span>
              {description ? ` · ${description}` : ''}
            </p>
          ) : null}
        </div>
      </div>
    </SurfaceCard>
  );
}
