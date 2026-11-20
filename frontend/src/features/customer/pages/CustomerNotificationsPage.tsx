import { PageHeader, SurfaceCard } from '@/design-system';
import { NotificationPreferencesCard } from '../components/NotificationPreferencesCard';
import { usePageTitle } from '../components/usePageTitle';

/** How the customer is told about their parcels. Lives in the sidebar, apart from booking. */
export function CustomerNotificationsPage() {
  usePageTitle('Notifications');

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="Notifications"
        subtitle="Choose how we keep you updated about your parcels."
        className="border-0 bg-transparent p-0"
      />
      <SurfaceCard>
        <NotificationPreferencesCard />
      </SurfaceCard>
    </div>
  );
}
