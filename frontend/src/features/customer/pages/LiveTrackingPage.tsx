import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, EmptyState, PageHeader, Skeleton, SurfaceCard } from '@/design-system';
import { LiveMapCanvas } from '../components/LiveMapCanvas';
import { TrackingDetails } from '@/features/public-tracking/components/TrackingDetails';
import { TrackingSearchForm } from '@/features/public-tracking/components/TrackingSearchForm';
import { useTrackingStream } from '@/hooks/useTrackingStream';
import { isTerminalPublicStatus, TrackingApiError } from '@/services/api/trackingHubApi';

export function LiveTrackingPage() {
  const { trackingNumber } = useParams<{ trackingNumber: string }>();
  const navigate = useNavigate();
  const { view, connection, error, isLoading, retry } = useTrackingStream(trackingNumber);

  const openTracking = (value: string) => {
    const next = value.trim();
    if (!next) return;
    if (trackingNumber === next) {
      retry();
      return;
    }
    navigate(`/customer/tracking/${encodeURIComponent(next)}`);
  };

  const clearTracking = () => {
    if (trackingNumber) navigate('/customer/tracking');
  };

  const notFound = error instanceof TrackingApiError && error.status === 404;
  const rateLimited = error instanceof TrackingApiError && error.status === 429;
  const fatalError = error !== null && view === null;

  return (
    <main className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="Live tracking"
        subtitle="Follow your parcel and see each public checkpoint as it happens."
        className="border-0 bg-transparent p-0"
      />

      <SurfaceCard title={trackingNumber ? 'Track another parcel' : 'Find a parcel'}>
        <TrackingSearchForm
          defaultValue={trackingNumber ?? ''}
          loading={isLoading && Boolean(trackingNumber)}
          buttonLabel={trackingNumber ? 'Track' : 'Find parcel'}
          onTrack={openTracking}
          onClear={clearTracking}
        />
      </SurfaceCard>

      {!trackingNumber ? (
        <EmptyState
          type="no-results"
          title="Enter a tracking number"
          message="Your tracking number is on your receipt or confirmation email."
          className="mx-auto"
        />
      ) : fatalError ? (
        notFound ? (
          <EmptyState
            type="no-results"
            title="Tracking number not found"
            message="Check the number and try again. A newly booked parcel can take a moment to appear."
            action={{ label: 'Try another number', onClick: clearTracking }}
            className="mx-auto"
          />
        ) : rateLimited ? (
          <Alert
            tone="warning"
            title="Too many tracking requests"
            action={
              <Button variant="ghost" density="compact" onClick={retry}>
                Try again
              </Button>
            }
          >
            {retryAfterMessage(error)}
          </Alert>
        ) : (
          <Alert
            tone="danger"
            title="We couldn't load this parcel"
            action={
              <Button variant="ghost" density="compact" onClick={retry}>
                Try again
              </Button>
            }
          >
            Check your connection and try again.
          </Alert>
        )
      ) : !view ? (
        <div className="flex flex-col gap-4" aria-busy="true" aria-live="polite">
          <Skeleton preset="card" className="w-full" />
          <Skeleton preset="text-block" />
        </div>
      ) : (
        <>
          {connection === 'offline' && !isTerminalPublicStatus(view.currentPublicStatus) ? (
            <Alert tone="info" title="Live updates paused">
              We&apos;re trying to reconnect. The latest status may be a few minutes old.
            </Alert>
          ) : null}
          <TrackingDetails view={view} connection={connection} />
          <SurfaceCard title="Live map">
            <LiveMapCanvas />
          </SurfaceCard>
        </>
      )}
    </main>
  );
}

function retryAfterMessage(error: Error): string {
  if (
    error instanceof TrackingApiError &&
    error.retryAfterSeconds !== undefined &&
    error.retryAfterSeconds > 0
  ) {
    return `Please wait ${error.retryAfterSeconds} seconds before trying again.`;
  }
  return 'Please wait a moment before trying again.';
}

export default LiveTrackingPage;
