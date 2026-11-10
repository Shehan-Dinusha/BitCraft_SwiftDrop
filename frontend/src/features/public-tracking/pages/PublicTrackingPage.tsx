import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  DensityProvider as VelocityDensityProvider,
  EmptyState,
  PageHeader,
  PublicHeader,
  Skeleton
} from '@/design-system';
import { TrackingSearchForm } from '../components/TrackingSearchForm';
import { TrackingDetails } from '../components/TrackingDetails';
import { useTrackingStream } from '@/hooks/useTrackingStream';
import { isTerminalPublicStatus, TrackingApiError } from '@/services/api/trackingHubApi';

export function PublicTrackingPage() {
  const { trackingNumber } = useParams<{ trackingNumber: string }>();
  const navigate = useNavigate();
  const { view, connection, error, retry } = useTrackingStream(trackingNumber);

  const openTracking = (value: string) => {
    const next = value.trim();
    if (!next) return;
    if (trackingNumber === next) {
      retry();
      return;
    }
    navigate(`/track/${encodeURIComponent(next)}`);
  };

  const clearTracking = () => {
    if (trackingNumber) navigate('/track');
  };

  const notFound = error instanceof TrackingApiError && error.status === 404;
  const rateLimited = error instanceof TrackingApiError && error.status === 429;
  const fatalError = error !== null && view === null;

  const hasTracking = Boolean(trackingNumber?.trim());

  return (
    <VelocityDensityProvider density="comfortable">
      <div className="min-h-screen bg-canvas text-primary">
        <PublicHeader
          onTrack={openTracking}
          onSignIn={() => navigate('/login')}
          trackingNumber={trackingNumber}
          onClearInput={clearTracking}
          showEmptySearch={false}
        />

        <main className="mx-auto flex w-full max-w-3xl flex-col gap-5 px-4 py-6 sm:px-6">
          <PageHeader
            title="Track your parcel"
            subtitle="Follow a parcel across Sri Lanka with live, privacy-safe updates."
            className="rounded-xl border border-border-subtle px-5 py-5 sm:px-8"
          />

          {!hasTracking && (
            <TrackingSearchForm defaultValue="" onTrack={openTracking} onClear={clearTracking} />
          )}

          {!hasTracking ? (
            <EmptyState
              type="no-results"
              title="Enter a tracking number"
              message="Your tracking number starts with SD- and is on your receipt."
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
            </>
          )}
        </main>
      </div>
    </VelocityDensityProvider>
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

export default PublicTrackingPage;
