import { PageHeader, SurfaceCard } from '@/design-system';
import { OrderBookingWizard } from '../components/OrderBookingWizard';
import { usePageTitle } from '../components/usePageTitle';

/** Booking lives here; past parcels have their own page at `/customer/orders` ("My parcels"). */
export function CustomerBookingPage() {
  usePageTitle('Book a parcel');

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="Book a parcel"
        subtitle="Pickup and delivery anywhere in Sri Lanka."
        className="border-0 bg-transparent p-0"
      />

      {/* The dropdown lists open below their field, so the card must not clip them. */}
      <SurfaceCard className="overflow-visible">
        <OrderBookingWizard />
      </SurfaceCard>
    </div>
  );
}
