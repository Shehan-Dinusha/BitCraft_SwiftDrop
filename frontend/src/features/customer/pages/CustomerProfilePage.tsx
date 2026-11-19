import { PageHeader, SurfaceCard } from '@/design-system';
import { districtLabel } from '../components/booking/bookingForm';
import { ChangePasswordCard } from '../components/profile/ChangePasswordCard';
import { DeleteAccountCard } from '../components/profile/DeleteAccountCard';
import { usePageTitle } from '../components/usePageTitle';
import { useAuthStore } from '@/store/useAuthStore';

function Detail({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
      <dt className="text-body text-secondary">{label}</dt>
      <dd className="text-body-strong text-primary sm:text-right">{value || 'Not set'}</dd>
    </div>
  );
}

/** The customer's account: the details they registered with, their password and deleting the account. */
export function CustomerProfilePage() {
  usePageTitle('Profile and security');
  const user = useAuthStore((state) => state.user);

  const name = [user?.firstName, user?.lastName].filter(Boolean).join(' ');

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="Profile and security"
        subtitle="Your account details, your password and your data."
      />

      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-2">
        <div className="flex flex-col gap-5">
          <SurfaceCard title="Your details">
            <dl className="flex flex-col gap-3">
              <Detail label="Name" value={name} />
              <Detail label="Username" value={user?.username} />
              <Detail label="Email" value={user?.email} />
              <Detail label="Phone" value={user?.phone} />
              <Detail
                label="Home district"
                value={user?.district ? districtLabel(user.district) : undefined}
              />
            </dl>
          </SurfaceCard>
          <DeleteAccountCard />
        </div>
        <ChangePasswordCard />
      </div>
    </div>
  );
}

export default CustomerProfilePage;
