import { useQuery } from '@tanstack/react-query';
import { PageHeader, Skeleton, SurfaceCard } from '@/design-system';
import { fetchCurrentUser } from '@/services/api/authApi';
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
  const sessionUser = useAuthStore((state) => state.user);
  // The session only keeps what the sign-in returned (username, email, role, district). The name
  // and phone come from the account itself.
  const profile = useQuery({ queryKey: ['account', 'me'], queryFn: fetchCurrentUser });
  const user = profile.data ?? sessionUser;

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
            {profile.isLoading ? (
              <div
                className="flex flex-col gap-3"
                aria-busy="true"
                aria-label="Loading your details"
              >
                <Skeleton className="h-5 w-full" />
                <Skeleton className="h-5 w-full" />
                <Skeleton className="h-5 w-full" />
              </div>
            ) : (
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
            )}
          </SurfaceCard>
          <DeleteAccountCard />
        </div>
        <ChangePasswordCard />
      </div>
    </div>
  );
}

export default CustomerProfilePage;
