import { useQuery } from '@tanstack/react-query';
import { orderApi } from '@/services/api/orderApi';
import { useAuthStore } from '@/store/useAuthStore';

/** Full contact details, shown instead of the masked ones when the viewer sent the parcel. */
export interface OwnParcelContacts {
  senderName?: string;
  senderPhone?: string;
  receiverName?: string;
  receiverPhone?: string;
}

/**
 * The sender (a customer, or the shop for its own orders) sees their parcel unmasked. The order service
 * decides who owns it; anyone else gets a 404 and keeps the public, masked view.
 */
export function useOwnParcel(trackingNumber: string | undefined): OwnParcelContacts | null {
  const signedIn = useAuthStore((state) => Boolean(state.token));
  const role = useAuthStore((state) => state.user?.role);
  const sender = role === 'CUSTOMER' || role === 'MERCHANT';
  const value = trackingNumber?.trim();

  const { data } = useQuery({
    queryKey: ['own-parcel', value],
    queryFn: () => orderApi.getOrderByTrackingNumber(value as string),
    enabled: signedIn && sender && Boolean(value),
    retry: false,
    staleTime: 5 * 60_000
  });

  if (!data) return null;
  return {
    senderName: data.pickupContactName,
    senderPhone: data.pickupContactPhone,
    receiverName: data.recipientName,
    receiverPhone: data.recipientPhone
  };
}
