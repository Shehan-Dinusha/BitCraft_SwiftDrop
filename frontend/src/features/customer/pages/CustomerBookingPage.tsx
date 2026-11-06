import { useState } from 'react';
import { PageHeader, SurfaceCard, Tabs } from '@/design-system';
import { OrderBookingWizard } from '../components/OrderBookingWizard';
import { NotificationPreferencesCard } from '../components/NotificationPreferencesCard';
import { usePageTitle } from '../components/usePageTitle';

type BookingTab = 'book' | 'preferences';

const TABS: { id: BookingTab; label: string }[] = [
  { id: 'book', label: 'Book a parcel' },
  { id: 'preferences', label: 'Notifications' }
];

/** Booking lives here; past parcels have their own page at `/customer/orders` ("My parcels"). */
export function CustomerBookingPage() {
  const [activeTab, setActiveTab] = useState<BookingTab>('book');
  usePageTitle('Book a parcel');

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="Book a parcel"
        subtitle="Pickup and delivery anywhere in Sri Lanka."
        className="border-0 bg-transparent p-0"
      />

      <Tabs
        aria-label="Booking and notifications"
        tabs={TABS}
        value={activeTab}
        onChange={(id) => setActiveTab(id as BookingTab)}
        className="overflow-y-hidden"
      />

      {/* Both panels stay mounted so switching tabs never clears a half-filled booking. */}
      <section role="tabpanel" aria-label="Book a parcel" hidden={activeTab !== 'book'}>
        {/* The dropdown lists open below their field, so the card must not clip them. */}
        <SurfaceCard className="overflow-visible">
          <OrderBookingWizard />
        </SurfaceCard>
      </section>
      <section role="tabpanel" aria-label="Notifications" hidden={activeTab !== 'preferences'}>
        <SurfaceCard>
          <NotificationPreferencesCard />
        </SurfaceCard>
      </section>
    </div>
  );
}
