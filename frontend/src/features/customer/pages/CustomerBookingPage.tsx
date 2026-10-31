import { useState } from 'react';
import { Card } from '@/design-system/primitives/Card';
import { OrderBookingWizard } from '../components/OrderBookingWizard';
import { CustomerOrderHistory } from '../components/CustomerOrderHistory';
import { NotificationPreferencesCard } from '../components/NotificationPreferencesCard';
import { PlusCircle, History, Bell } from 'lucide-react';

export function CustomerBookingPage() {
  const [activeTab, setActiveTab] = useState<'book' | 'history' | 'preferences'>('book');

  return (
    <main className="mx-auto max-w-4xl p-4 sm:p-6 space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border-subtle pb-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-black text-primary tracking-tight">
            Customer Delivery Portal
          </h1>
          <p className="text-xs sm:text-sm text-secondary mt-1">
            Book deliveries with dynamic quoting, generate A5 waybills with QR codes, and configure omnichannel notifications.
          </p>
        </div>

        {/* Tab Toggle */}
        <div className="flex rounded-xl bg-elevated p-1 border border-border-subtle">
          <button
            type="button"
            onClick={() => setActiveTab('book')}
            className={`flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
              activeTab === 'book'
                ? 'bg-brand-primary text-brand-on shadow-sm'
                : 'text-secondary hover:text-primary'
            }`}
          >
            <PlusCircle className="size-3.5" />
            Book Delivery
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('history')}
            className={`flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
              activeTab === 'history'
                ? 'bg-brand-primary text-brand-on shadow-sm'
                : 'text-secondary hover:text-primary'
            }`}
          >
            <History className="size-3.5" />
            My Orders
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('preferences')}
            className={`flex items-center gap-2 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
              activeTab === 'preferences'
                ? 'bg-brand-primary text-brand-on shadow-sm'
                : 'text-secondary hover:text-primary'
            }`}
          >
            <Bell className="size-3.5" />
            Notifications
          </button>
        </div>
      </div>

      <Card className="p-5 sm:p-7">
        {activeTab === 'book' && (
          <OrderBookingWizard onOrderCreated={() => setActiveTab('history')} />
        )}
        {activeTab === 'history' && <CustomerOrderHistory />}
        {activeTab === 'preferences' && <NotificationPreferencesCard />}
      </Card>
    </main>
  );
}
