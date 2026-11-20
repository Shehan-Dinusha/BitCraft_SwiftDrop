import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Bike,
  Calculator,
  Camera,
  LayoutDashboard,
  MapPin,
  Store,
  Truck,
  User
} from 'lucide-react';
import { BrandMark, Button, Card, DensityProvider, PublicHeader } from '@/design-system';
import { TrackingSearchForm } from '@/features/public-tracking/components/TrackingSearchForm';
import { roleHome } from '@/app/roleHome';
import { useAuthStore } from '@/store/useAuthStore';

const steps = [
  {
    icon: <Calculator className="size-5" />,
    title: 'Book and see the price',
    text: 'Enter pickup and drop-off details and get the delivery fee before you confirm.'
  },
  {
    icon: <Truck className="size-5" />,
    title: 'A courier is assigned',
    text: 'Dispatch matches your parcel with an available courier in the zone.'
  },
  {
    icon: <MapPin className="size-5" />,
    title: 'Track it live',
    text: 'Follow every status change in real time with your tracking number.'
  },
  {
    icon: <Camera className="size-5" />,
    title: 'Proof of delivery',
    text: 'The courier records a photo at the door, so you know it arrived.'
  }
];

const audiences = [
  {
    icon: <User className="size-5" />,
    title: 'Customers',
    text: 'Book a pickup, pay cash to the courier, and keep every order and receipt in one place.'
  },
  {
    icon: <Store className="size-5" />,
    title: 'Merchants',
    text: 'Send orders one by one, in bulk or by CSV, connect with API keys, and get webhook updates.'
  },
  {
    icon: <Bike className="size-5" />,
    title: 'Couriers',
    text: "Start a shift, see today's deliveries, and move each parcel through pickup to drop-off."
  },
  {
    icon: <LayoutDashboard className="size-5" />,
    title: 'Operations',
    text: 'Assign couriers, manage zones and pricing, approve merchants, and watch delivery KPIs.'
  }
];

function IconBadge({ children }: { children: ReactNode }) {
  return (
    <span
      aria-hidden="true"
      className="inline-flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-subtle text-brand-primary"
    >
      {children}
    </span>
  );
}

/** Public home page: what SwiftDrop does, quick parcel tracking, and the way in for each role. */
export function LandingPage() {
  const navigate = useNavigate();
  const token = useAuthStore((state) => state.token);
  const role = useAuthStore((state) => state.role);
  const signedIn = Boolean(token);
  const dashboard = roleHome[role];

  const track = (trackingNumber: string) => navigate(`/track/${encodeURIComponent(trackingNumber)}`);

  return (
    <DensityProvider density="comfortable">
      <div className="min-h-screen bg-canvas text-primary">
        <PublicHeader
          onTrack={track}
          onSignIn={() => navigate(signedIn ? dashboard : '/login')}
          showEmptySearch={false}
        />

        <main>
          {/* Hero */}
          <section className="mx-auto grid w-full max-w-6xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-[1.1fr_1fr] lg:items-center lg:py-20">
            <div className="flex flex-col gap-5">
              <p className="text-label font-semibold uppercase tracking-wider text-brand-primary">
                Parcel delivery across Sri Lanka
              </p>
              <h1 className="text-display text-primary">Send it today. Follow it all the way.</h1>
              <p className="max-w-xl text-body-l text-secondary">
                SwiftDrop picks up your parcel, delivers it across all 25 districts, and lets you
                track every step live. Pay the courier in cash at pickup.
              </p>
              <div className="flex flex-wrap gap-3">
                <Button
                  density="comfortable"
                  onClick={() => navigate(signedIn ? dashboard : '/register')}
                >
                  {signedIn ? 'Open your dashboard' : 'Send a parcel'}
                </Button>
                {!signedIn && (
                  <Button variant="outline" density="comfortable" onClick={() => navigate('/login')}>
                    Sign in
                  </Button>
                )}
              </div>
            </div>

            <Card variant="elevated" className="p-6 sm:p-8">
              <h2 className="text-h3 text-primary">Track a parcel</h2>
              <p className="pb-4 pt-1 text-body text-secondary">
                No account needed. Enter the tracking number from your receipt or SMS.
              </p>
              <TrackingSearchForm onTrack={track} />
            </Card>
          </section>

          {/* How it works */}
          <section aria-labelledby="how-it-works" className="border-y border-border-subtle bg-card">
            <div className="mx-auto w-full max-w-6xl px-4 py-14 sm:px-6">
              <h2 id="how-it-works" className="text-h1 text-primary">
                How it works
              </h2>
              <ol className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
                {steps.map((step, index) => (
                  <li key={step.title} className="flex flex-col gap-3">
                    <div className="flex items-center gap-3">
                      <IconBadge>{step.icon}</IconBadge>
                      <span className="text-caption font-semibold text-muted tabular-nums">
                        Step {index + 1}
                      </span>
                    </div>
                    <h3 className="text-h3 text-primary">{step.title}</h3>
                    <p className="text-body text-secondary">{step.text}</p>
                  </li>
                ))}
              </ol>
            </div>
          </section>

          {/* Roles */}
          <section aria-labelledby="who-its-for" className="mx-auto w-full max-w-6xl px-4 py-14 sm:px-6">
            <h2 id="who-its-for" className="text-h1 text-primary">
              One platform for everyone in the delivery
            </h2>
            <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {audiences.map((audience) => (
                <Card key={audience.title} className="flex flex-col gap-3 p-5">
                  <IconBadge>{audience.icon}</IconBadge>
                  <h3 className="text-h3 text-primary">{audience.title}</h3>
                  <p className="text-body text-secondary">{audience.text}</p>
                </Card>
              ))}
            </div>
          </section>

          {/* Call to action */}
          {!signedIn && (
            <section className="mx-auto w-full max-w-6xl px-4 pb-16 sm:px-6">
              <Card
                variant="selected"
                className="flex flex-col items-start gap-4 p-6 sm:flex-row sm:items-center sm:justify-between sm:p-8"
              >
                <div>
                  <h2 className="text-h2 text-primary">Ready to send your first parcel?</h2>
                  <p className="pt-1 text-body text-secondary">
                    Create a free customer or merchant account in a minute.
                  </p>
                </div>
                <Button density="comfortable" onClick={() => navigate('/register')}>
                  Create an account
                </Button>
              </Card>
            </section>
          )}
        </main>

        <footer className="border-t border-border-subtle bg-card">
          <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-4 py-6 text-caption text-secondary sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <span className="flex items-center gap-2">
              <BrandMark size={20} />
              <span>© {new Date().getFullYear()} SwiftDrop</span>
            </span>
            <nav aria-label="Footer" className="flex gap-5">
              <Link to="/track" className="hover:text-primary">
                Track a parcel
              </Link>
              <Link to="/register" className="hover:text-primary">
                Create an account
              </Link>
              <Link to="/login" className="hover:text-primary">
                Sign in
              </Link>
            </nav>
          </div>
        </footer>
      </div>
    </DensityProvider>
  );
}
