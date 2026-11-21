import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Calculator, Camera, Check, MapPin, Store, Truck, User } from 'lucide-react';
import { BrandMark, Button, Card, DensityProvider, PublicHeader } from '@/design-system';
import { TrackingSearchForm } from '@/features/public-tracking/components/TrackingSearchForm';
import { SkyBackdrop } from '@/features/auth/components/SkyBackdrop';
import { PAYMENT_NOTE } from '@/features/customer/components/orders/orderDisplay';
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
    title: 'A courier picks it up',
    text: `A courier in your district collects the parcel. ${PAYMENT_NOTE}`
  },
  {
    icon: <MapPin className="size-5" />,
    title: 'Track it live',
    text: 'Follow every status change in real time with your tracking number.'
  },
  {
    icon: <Camera className="size-5" />,
    title: 'Proof at the door',
    text: "The courier can only mark it delivered after a photo and the recipient's name. You get an email when it lands."
  }
];

const audiences = [
  {
    icon: <User className="size-5" />,
    title: 'For individuals',
    points: [
      'Send a parcel to any of the 25 districts',
      'See the fee before you book, pay cash at pickup',
      'Track every order and keep your receipts in one place'
    ],
    cta: { label: 'Create a free account', to: '/register' }
  },
  {
    icon: <Store className="size-5" />,
    title: 'For businesses',
    points: [
      'Create orders straight from your store with an API key, or upload a CSV for a batch',
      'Get a webhook every time a parcel changes status',
      'Pickups from your business location, every order on one dashboard'
    ],
    cta: { label: 'Register your business', to: '/register/business' }
  }
];

const businessSteps = [
  { title: 'Sign up', text: 'Create a business account and confirm your email.' },
  { title: 'Add your details', text: 'Enter your business details and a pickup location.' },
  {
    title: 'Get approved',
    text: 'The dispatcher for your district reviews it. Once approved, you can start sending.'
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

/** Public home page: what SwiftDrop does, quick parcel tracking, and the way in for senders and businesses. */
export function LandingPage() {
  const navigate = useNavigate();
  const token = useAuthStore((state) => state.token);
  const role = useAuthStore((state) => state.role);
  const signedIn = Boolean(token);
  const dashboard = roleHome[role];

  const track = (trackingNumber: string) =>
    navigate(`/track/${encodeURIComponent(trackingNumber)}`);

  return (
    <DensityProvider density="comfortable">
      <div className="min-h-screen bg-canvas text-primary">
        {/* Header and hero on the same sky as the sign-in page. */}
        <div className="relative">
          <SkyBackdrop />
          <div className="relative">
            <PublicHeader
              onTrack={track}
              onSignIn={signedIn ? undefined : () => navigate('/login')}
              showEmptySearch={false}
              className="border-transparent bg-transparent"
            />

            <section className="mx-auto grid w-full max-w-6xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-[1.1fr_1fr] lg:items-center lg:py-20">
              <div className="flex flex-col gap-5">
                <p className="text-label font-semibold uppercase tracking-wider text-brand-primary">
                  Parcel delivery across Sri Lanka
                </p>
                <h1 className="text-display text-primary">Send it today. Follow it all the way.</h1>
                <p className="max-w-xl text-body-l text-secondary">
                  SwiftDrop picks up parcels from homes and businesses, delivers them across all 25
                  districts, and lets you track every step live. {PAYMENT_NOTE}
                </p>
                <div>
                  <Button
                    density="comfortable"
                    onClick={() => navigate(signedIn ? dashboard : '/login')}
                  >
                    {signedIn ? 'Open your dashboard' : 'Send a parcel'}
                  </Button>
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
          </div>
        </div>

        <main>
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

          {/* Individuals and businesses */}
          <section
            aria-labelledby="who-its-for"
            className="mx-auto w-full max-w-6xl px-4 py-14 sm:px-6"
          >
            <h2 id="who-its-for" className="text-h1 text-primary">
              Sending one parcel or a thousand
            </h2>
            <div className="mt-8 grid gap-4 md:grid-cols-2">
              {audiences.map((audience) => (
                <Card key={audience.title} className="p-2">
                  <div className="flex h-full flex-col gap-5">
                    <div className="flex items-center gap-3">
                      <IconBadge>{audience.icon}</IconBadge>
                      <h3 className="text-h2 text-primary">{audience.title}</h3>
                    </div>
                    <ul className="flex flex-1 flex-col gap-3">
                      {audience.points.map((point) => (
                        <li key={point} className="flex gap-3 text-body text-secondary">
                          <Check
                            aria-hidden="true"
                            className="mt-0.5 size-4 shrink-0 text-brand-primary"
                          />
                          <span>{point}</span>
                        </li>
                      ))}
                    </ul>
                    {!signedIn && (
                      <div>
                        <Button
                          density="comfortable"
                          variant="outline"
                          onClick={() => navigate(audience.cta.to)}
                        >
                          {audience.cta.label}
                        </Button>
                      </div>
                    )}
                  </div>
                </Card>
              ))}
            </div>
          </section>

          {/* Business onboarding */}
          {!signedIn && (
            <section
              aria-labelledby="for-business"
              className="mx-auto w-full max-w-6xl px-4 pb-16 sm:px-6"
            >
              <Card variant="selected" className="p-2 sm:p-4">
                <div className="flex flex-col gap-6">
                  <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center sm:justify-between">
                    <h2 id="for-business" className="text-h2 text-primary">
                      Run a business? Open an account in three steps
                    </h2>
                    <Button density="comfortable" onClick={() => navigate('/register/business')}>
                      Register your business
                    </Button>
                  </div>
                  <ol className="grid gap-5 sm:grid-cols-3">
                    {businessSteps.map((step, index) => (
                      <li key={step.title} className="flex gap-3">
                        <span
                          aria-hidden="true"
                          className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-primary text-caption font-semibold text-brand-on tabular-nums"
                        >
                          {index + 1}
                        </span>
                        <div>
                          <h3 className="text-body-strong text-primary">{step.title}</h3>
                          <p className="pt-0.5 text-body text-secondary">{step.text}</p>
                        </div>
                      </li>
                    ))}
                  </ol>
                </div>
              </Card>
            </section>
          )}
        </main>

        <footer className="border-t border-border-subtle bg-card">
          <div className="mx-auto flex w-full max-w-6xl items-center gap-2 px-4 py-6 text-caption text-secondary sm:px-6">
            <BrandMark size={20} />
            <span>© {new Date().getFullYear()} SwiftDrop</span>
          </div>
        </footer>
      </div>
    </DensityProvider>
  );
}
