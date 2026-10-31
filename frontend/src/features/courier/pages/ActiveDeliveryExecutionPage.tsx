import { useEffect, useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { deliveryApi } from '@/services/api/deliveryApi';
import type {
  DeliverySessionResponse,
  DeliveryState,
  DeliveryFailureReason,
  TrackingBreadcrumbResponse
} from '@/services/api/deliveryApi';
import { DeliveryProgressStepper } from '@/design-system/components/DeliveryProgressStepper/DeliveryProgressStepper';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { OneTapStateTransitionCard } from '../components/OneTapStateTransitionCard';
import { ProofOfDeliveryCaptureModal } from '../components/ProofOfDeliveryCaptureModal';
import { GpsTelemetryPublisher } from '../components/GpsTelemetryPublisher';
import {
  ArrowLeft,
  RefreshCw,
  MapPin,
  Package,
  Phone,
  Navigation
} from 'lucide-react';

export function ActiveDeliveryExecutionPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const courierId = user?.id || '11111111-1111-1111-1111-111111111111';

  const orderIdParam = searchParams.get('orderId');
  const deliveryIdParam = searchParams.get('deliveryId');

  const [session, setSession] = useState<DeliverySessionResponse | null>(null);
  const [breadcrumbs, setBreadcrumbs] = useState<TrackingBreadcrumbResponse[]>([]);
  const [isPoDVerified, setIsPoDVerified] = useState(false);
  const [isPoDModalOpen, setIsPoDModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [pendingState, setPendingState] = useState<DeliveryState | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Load delivery session
  const loadDelivery = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      let activeDelivery: DeliverySessionResponse | null = null;

      if (deliveryIdParam) {
        activeDelivery = await deliveryApi.getDeliveryById(deliveryIdParam);
      } else if (orderIdParam) {
        activeDelivery = await deliveryApi.getDeliveryByOrderId(orderIdParam);
      } else {
        // Fallback: lookup current courier's active deliveries
        const courierDeliveries = await deliveryApi.getDeliveriesByCourier(courierId);
        if (courierDeliveries && courierDeliveries.length > 0) {
          activeDelivery =
            courierDeliveries.find(
              (d) => d.currentState !== 'DELIVERED' && d.currentState !== 'FAILED' && d.currentState !== 'CANCELLED'
            ) || courierDeliveries[0];
        }
      }

      if (activeDelivery) {
        setSession(activeDelivery);
        if (activeDelivery.deliveryId) {
          try {
            const crumbs = await deliveryApi.getBreadcrumbs(activeDelivery.deliveryId);
            setBreadcrumbs(crumbs || []);
          } catch {
            // Non-fatal if breadcrumbs empty
          }
          try {
            const pod = await deliveryApi.getProofOfDelivery(activeDelivery.deliveryId);
            if (pod && pod.verified) {
              setIsPoDVerified(true);
            }
          } catch {
            setIsPoDVerified(false);
          }
        }
      } else {
        setSession(null);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load delivery execution session';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  }, [courierId, deliveryIdParam, orderIdParam]);

  useEffect(() => {
    loadDelivery();
  }, [loadDelivery]);

  // Handle state transition
  const handleTransition = async (
    targetState: DeliveryState,
    reason?: DeliveryFailureReason,
    notes?: string
  ) => {
    if (!session?.deliveryId) return;
    try {
      setPendingState(targetState);
      setErrorMsg(null);
      const updated = await deliveryApi.transitionDelivery(session.deliveryId, {
        targetState,
        actorRole: 'COURIER',
        failureReason: reason,
        notes: notes || `Courier transitioned to ${targetState}`
      });
      setSession(updated);
      setSuccessMsg(`Delivery successfully updated to ${targetState}!`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : `Failed to transition to ${targetState}`;
      setErrorMsg(msg);
    } finally {
      setPendingState(null);
    }
  };

  const handleBreadcrumbAdded = (crumb: TrackingBreadcrumbResponse) => {
    setBreadcrumbs((prev) => [crumb, ...prev.slice(0, 9)]);
  };

  return (
    <main className="mx-auto max-w-3xl space-y-5 p-4 md:p-6" data-density="touch">
      {/* Top Header Bar */}
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            className="min-h-[44px] min-w-[44px] p-2"
            onClick={() => navigate('/courier')}
            aria-label="Back to Shift Dashboard"
          >
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
              Active Delivery Execution
            </h1>
            <p className="text-xs text-slate-500">
              One-tap FSM state machine transitions, GPS breadcrumb telemetry, and PoD handover.
            </p>
          </div>
        </div>

        <Button
          variant="outline"
          onClick={loadDelivery}
          disabled={loading || pendingState !== null}
          className="self-start text-xs sm:self-auto min-h-[44px]"
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </header>

      {/* Notifications */}
      {errorMsg && (
        <StatusBanner tone="danger" title="Delivery Error" onDismiss={() => setErrorMsg(null)}>
          {errorMsg}
        </StatusBanner>
      )}
      {successMsg && (
        <StatusBanner tone="success" title="Success" onDismiss={() => setSuccessMsg(null)}>
          {successMsg}
        </StatusBanner>
      )}

      {loading ? (
        <div className="space-y-4">
          <SurfaceCard className="h-32 animate-pulse bg-slate-100" />
          <SurfaceCard className="h-48 animate-pulse bg-slate-100" />
        </div>
      ) : !session ? (
        <SurfaceCard className="p-8 text-center">
          <Package className="mx-auto h-12 w-12 text-slate-400" />
          <h2 className="mt-3 text-lg font-bold text-slate-900">
            No Active Delivery Found
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            {orderIdParam
              ? `No delivery session linked to order ID ${orderIdParam}.`
              : 'You do not have any active deliveries assigned right now.'}
          </p>
          <div className="mt-5">
            <Button variant="primary" onClick={() => navigate('/courier')}>
              Return to Shift Dashboard
            </Button>
          </div>
        </SurfaceCard>
      ) : (
        <div className="space-y-5">
          {/* Visual FSM Stepper Card */}
          <SurfaceCard className="space-y-4 p-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm font-bold text-indigo-600">
                  {session.trackingNumber || 'UNTRACKED'}
                </span>
                <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                  {session.courierName ? `Courier: ${session.courierName}` : 'SwiftDrop Express'}
                </span>
              </div>
              <span className="text-xs text-slate-500">
                Order #{session.orderId ? session.orderId.substring(0, 8) : 'N/A'}
              </span>
            </div>

            {/* Stepper with Touch Density */}
            <div className="py-2">
              <DeliveryProgressStepper
                current={(session.currentState || 'CREATED') as DeliveryState}
                density="touch"
              />
            </div>
          </SurfaceCard>

          {/* Primary Action Transition Card */}
          <OneTapStateTransitionCard
            session={session}
            onTransition={handleTransition}
            onOpenPoD={() => setIsPoDModalOpen(true)}
            isPoDVerified={isPoDVerified}
            pendingState={pendingState}
          />

          {/* Location & Districts Card */}
          <SurfaceCard className="space-y-4 p-5">
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-500">
              Delivery Route & Contact
            </h3>

            <div className="space-y-3">
              <div className="flex items-start gap-3">
                <div className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-amber-500/10 text-amber-600">
                  <MapPin className="h-4 w-4" />
                </div>
                <div>
                  <span className="text-xs font-semibold text-slate-500">Pickup District</span>
                  <p className="text-sm font-medium text-slate-900">
                    {session.pickupDistrict ? `${session.pickupDistrict} District Hub` : 'Merchant Hub, Colombo'}
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3">
                <div className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-emerald-500/10 text-emerald-600">
                  <Navigation className="h-4 w-4" />
                </div>
                <div>
                  <span className="text-xs font-semibold text-slate-500">Delivery Destination</span>
                  <p className="text-sm font-medium text-slate-900">
                    {session.deliveryDistrict ? `${session.deliveryDistrict} Recipient Address` : 'Customer Address, Colombo'}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 border-t border-border-subtle pt-3 text-xs">
                <div>
                  <span className="text-slate-500">Attempt Count:</span>
                  <p className="font-semibold text-slate-800">
                    {session.attemptCount ?? 1} of 3
                  </p>
                </div>
                <div>
                  <span className="text-slate-500">Courier Dispatch Hotline:</span>
                  <p className="flex items-center gap-1 font-semibold text-slate-800">
                    <Phone className="h-3.5 w-3.5 text-indigo-500" />
                    +94 11 234 5678
                  </p>
                </div>
              </div>
            </div>
          </SurfaceCard>

          {/* GPS Telemetry & Breadcrumb Publisher */}
          {session.deliveryId && (
            <div className="space-y-4">
              <GpsTelemetryPublisher
                deliveryId={session.deliveryId}
                courierId={courierId}
                onBreadcrumbAdded={handleBreadcrumbAdded}
              />

              {breadcrumbs.length > 0 && (
                <SurfaceCard className="space-y-3 p-4">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                      Recent Telemetry Breadcrumbs ({breadcrumbs.length})
                    </h4>
                    <span className="text-xs text-slate-400">Streamed to Gateway</span>
                  </div>
                  <div className="max-h-40 space-y-2 overflow-y-auto pr-1">
                    {breadcrumbs.map((crumb, idx) => (
                      <div
                        key={`${crumb.deliveryId || 'crumb'}-${idx}`}
                        className="flex items-center justify-between rounded-lg bg-slate-50 p-2 text-xs text-slate-700"
                      >
                        <div className="flex items-center gap-2">
                          <MapPin className="h-3 w-3 text-indigo-500" />
                          <span>
                            {crumb.latitude !== undefined && crumb.longitude !== undefined
                              ? `${crumb.latitude.toFixed(4)}, ${crumb.longitude.toFixed(4)}`
                              : '0.0000, 0.0000'}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 font-mono text-[11px] text-slate-500">
                          <span>{crumb.speedKmh ? `${crumb.speedKmh} km/h` : 'Moving'}</span>
                          <span>
                            {crumb.recordedAt
                              ? new Date(crumb.recordedAt).toLocaleTimeString()
                              : 'Recent'}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </SurfaceCard>
              )}
            </div>
          )}

          {/* Proof of Delivery Capture Modal */}
          {session.deliveryId && (
            <ProofOfDeliveryCaptureModal
              open={isPoDModalOpen}
              onClose={() => setIsPoDModalOpen(false)}
              deliveryId={session.deliveryId}
              onVerified={() => {
                setIsPoDVerified(true);
                setSuccessMsg('Proof of Delivery recorded and verified!');
              }}
            />
          )}
        </div>
      )}
    </main>
  );
}
