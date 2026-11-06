import { useState } from 'react';
import { PageHeader, SurfaceCard, Tabs } from '@/design-system';
import { OrderBookingWizard } from '../components/OrderBookingWizard';
import { CustomerOrderHistory } from '../components/CustomerOrderHistory';
import { NotificationPreferencesCard } from '../components/NotificationPreferencesCard';
import { usePageTitle } from '../components/usePageTitle';

type BookingTab = 'book' | 'orders' | 'preferences';

const TABS: { id: BookingTab; label: string }[] = [
  { id: 'book', label: 'Book a parcel' },
  { id: 'orders', label: 'My parcels' },
  { id: 'preferences', label: 'Notifications' }
];

/** Booking, the parcel list (until it gets its own page) and notification settings. */
export function CustomerBookingPage() {
  const [activeTab, setActiveTab] = useState<BookingTab>('book');
  usePageTitle('Book a parcel');

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-5 p-4 sm:p-6">
      <PageHeader title="Book a parcel" subtitle="Pickup and delivery anywhere in Sri Lanka." />

      <Tabs
        aria-label="Booking, parcels and notifications"
        tabs={TABS}
        value={activeTab}
        onChange={(id) => setActiveTab(id as BookingTab)}
      />

      {/* The booking and notification panels stay mounted so switching tabs never clears a half-filled booking. */}
      <section role="tabpanel" aria-label="Book a parcel" hidden={activeTab !== 'book'}>
        <SurfaceCard>
          <OrderBookingWizard onViewOrders={() => setActiveTab('orders')} />
        </SurfaceCard>
      </section>
      {activeTab === 'orders' ? (
        <section role="tabpanel" aria-label="My parcels">
          <SurfaceCard>
            <CustomerOrderHistory />
          </SurfaceCard>
        </section>
      ) : null}
      <section role="tabpanel" aria-label="Notifications" hidden={activeTab !== 'preferences'}>
        <SurfaceCard>
          <NotificationPreferencesCard />
        </SurfaceCard>
      </section>
    </div>
  );
}
