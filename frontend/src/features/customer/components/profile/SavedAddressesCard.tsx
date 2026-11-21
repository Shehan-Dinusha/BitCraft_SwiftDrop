import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MapPin, Trash2 } from 'lucide-react';
import { Alert, Button, SurfaceCard } from '@/design-system';
import { addressBookApi, type SavedAddress } from '@/services/api/orderApi';
import { addressSummary } from '@/features/customer/components/booking/savedAddresses';
import { districtLabel } from '@/features/customer/components/booking/bookingForm';

/**
 * The customer's address book on their profile. Addresses are added from the booking form ("Save this
 * address for next time"); here they can be reviewed and deleted.
 */
export function SavedAddressesCard() {
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState<string | null>(null);
  const addresses = useQuery({
    queryKey: ['saved-addresses'],
    queryFn: addressBookApi.list,
    retry: false
  });
  const remove = useMutation({
    mutationFn: (addressId: string) => addressBookApi.remove(addressId),
    onSuccess: () => {
      setConfirming(null);
      void queryClient.invalidateQueries({ queryKey: ['saved-addresses'] });
    }
  });

  const list: SavedAddress[] = addresses.data ?? [];

  return (
    <SurfaceCard title="Saved addresses">
      <div className="flex flex-col gap-3">
        {addresses.isError ? (
          <Alert tone="danger" title="Your saved addresses could not be loaded">
            <Button type="button" variant="ghost" onClick={() => void addresses.refetch()}>
              Try again
            </Button>
          </Alert>
        ) : addresses.isLoading ? (
          <p className="text-caption text-secondary">Loading saved addresses…</p>
        ) : list.length === 0 ? (
          <p className="text-body text-secondary">
            When you book a parcel, tick "Save this address for next time" to keep it here.
          </p>
        ) : (
          <ul className="flex flex-col divide-y divide-border-subtle">
            {list.map((address) => (
              <li
                key={address.addressId}
                className="flex items-start gap-3 py-3 first:pt-0 last:pb-0"
              >
                <MapPin aria-hidden="true" className="mt-1 size-4 shrink-0 text-brand-primary" />
                <div className="min-w-0 flex-1">
                  <p className="text-body-strong text-primary">{address.label}</p>
                  <p className="text-caption text-secondary">
                    {address.contactName} · {addressSummary(address)} ·{' '}
                    {districtLabel(address.district)}
                  </p>
                </div>
                {confirming === address.addressId ? (
                  <div className="flex shrink-0 gap-2">
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => setConfirming(null)}
                      disabled={remove.isPending}
                    >
                      Keep
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      loading={remove.isPending}
                      loadingLabel="Deleting"
                      onClick={() => address.addressId && remove.mutate(address.addressId)}
                    >
                      Delete
                    </Button>
                  </div>
                ) : (
                  <Button
                    type="button"
                    variant="ghost"
                    aria-label={`Delete ${address.label}`}
                    leftIcon={<Trash2 aria-hidden="true" />}
                    onClick={() => setConfirming(address.addressId ?? null)}
                  >
                    Delete
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
        {remove.isError ? (
          <Alert tone="danger" title="The address was not deleted">
            Try again in a moment.
          </Alert>
        ) : null}
      </div>
    </SurfaceCard>
  );
}

export default SavedAddressesCard;
