import { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams, useSearchParams, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { deliveryApi } from '@/services/api/deliveryApi';
import { parseApiError } from '@/services/api/apiError';
import type {
  CourierJobResponse,
  DeliverySessionResponse,
  DeliveryState,
  DeliveryFailureReason
} from '@/services/api/deliveryApi';
import { courierNextStep, crossesDistricts } from '@/types/fsm';
import { DeliveryProgressStepper } from '@/design-system/components/DeliveryProgressStepper/DeliveryProgressStepper';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { ErgonomicActionBar } from '@/design-system/components/ErgonomicActionBar/ErgonomicActionBar';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { cn } from '@/utils/cn';
import { JobDetailsCard } from '../components/JobDetailsCard';
import { OneTapStateTransitionCard } from '../components/OneTapStateTransitionCard';
import { ProofOfDeliveryCaptureModal } from '../components/ProofOfDeliveryCaptureModal';
import { DeliveryFailureReportModal } from '../components/DeliveryFailureReportModal';
import { ArrowLeft, RefreshCw, Package, AlertTriangle } from 'lucide-react';

export function ActiveDeliveryExecutionPage() {
  const { id } = useParams<{ id?: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const courierId = user?.id ?? '';

  const orderIdParam = searchParams.get('orderId');
  const deliveryIdParam = id || searchParams.get('deliveryId');

  const [session, setSession] = useState<DeliverySessionResponse | null>(null);
  const [allDeliveries, setAllDeliveries] = useState<DeliverySessionResponse[]>([]);
  const [job, setJob] = useState<CourierJobResponse | null>(null);
  const [jobError, setJobError] = useState<string | null>(null);
  // The step the ticked box belongs to, so a tick never carries over to another job or amount.
  const [confirmedCash, setConfirmedCash] = useState<string | null>(null);
  const [isPoDVerified, setIsPoDVerified] = useState(false);
  const [isPoDModalOpen, setIsPoDModalOpen] = useState(false);
  const [isFailureModalOpen, setIsFailureModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [pendingState, setPendingState] = useState<DeliveryState | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // A parcel going to another district goes through both hubs; the courier's next step depends on it.
  const viaHubs = session ? crossesDistricts(session) : false;
  const allowedNextStates = useMemo(() => {
    const next = courierNextStep(session?.currentState as DeliveryState | undefined, viaHubs);
    return next ? [next] : ([] as DeliveryState[]);
  }, [session?.currentState, viaHubs]);

  // Load delivery session
  const loadDelivery = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      let activeDelivery: DeliverySessionResponse | null = null;

      // Always load all today's deliveries to populate the switcher bar
      let myDeliveries: DeliverySessionResponse[] = [];
      try {
        // A parcel handed in at the hub is no longer this courier's to open.
        myDeliveries = ((await deliveryApi.getMyDeliveries('today')) || []).filter(
          (d) => d.currentState !== 'AT_ORIGIN_HUB'
        );
        setAllDeliveries(myDeliveries);
      } catch {
        myDeliveries = [];
      }

      if (deliveryIdParam) {
        activeDelivery = await deliveryApi.getDeliveryById(deliveryIdParam);
      } else if (orderIdParam) {
        activeDelivery = await deliveryApi.getDeliveryByOrderId(orderIdParam);
      } else if (myDeliveries.length > 0) {
        // Prioritize actionable/in-progress delivery so the slide-to-transition bar is immediately available
        activeDelivery =
          myDeliveries.find(
            (d) =>
              d.currentState !== 'DELIVERED' &&
              d.currentState !== 'FAILED' &&
              d.currentState !== 'CANCELLED'
          ) || myDeliveries[0];
      } else if (courierId) {
        // Fallback: lookup current courier's active deliveries
        const courierDeliveries = await deliveryApi.getDeliveriesByCourier(courierId);
        setAllDeliveries(courierDeliveries || []);
        if (courierDeliveries && courierDeliveries.length > 0) {
          activeDelivery =
            courierDeliveries.find(
              (d) =>
                d.currentState !== 'DELIVERED' &&
                d.currentState !== 'FAILED' &&
                d.currentState !== 'CANCELLED'
            ) || courierDeliveries[0];
        }
      }

      if (activeDelivery) {
        setSession(activeDelivery);
        if (activeDelivery.deliveryId) {
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
      const msg = err instanceof Error ? err.message : 'The delivery could not be loaded.';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  }, [courierId, deliveryIdParam, orderIdParam]);

  useEffect(() => {
    loadDelivery();
  }, [loadDelivery]);

  // Addresses and cash change with each step, so they are read again once the step is saved.
  const deliveryId = session?.deliveryId;
  const currentState = session?.currentState;
  const loadJob = useCallback(
    async (isCurrent: () => boolean = () => true) => {
      if (!deliveryId) return;
      try {
        const loaded = await deliveryApi.getJob(deliveryId);
        if (!isCurrent()) return;
        setJob(loaded);
        setJobError(null);
      } catch (err: unknown) {
        if (!isCurrent()) return;
        setJob(null);
        setJobError(parseApiError(err).message ?? 'The addresses could not be loaded. Try again.');
      }
    },
    [deliveryId]
  );

  useEffect(() => {
    if (pendingState !== null) return;
    let current = true;
    void loadJob(() => current);
    return () => {
      current = false;
    };
  }, [loadJob, currentState, pendingState]);

  // Another job's addresses are never shown while this one's load.
  const shownJob = job?.deliveryId === deliveryId ? job : null;
  const orderCancelled = Boolean(shownJob?.cancelled);

  // Only a pickup takes money: the delivery fee, from the sender.
  const atPickup = currentState === 'ASSIGNED';
  const cashDue = (atPickup && shownJob?.feeToCollect) || 0;
  const cashKey = `${deliveryId}:${cashDue}`;
  const cashConfirmed = cashDue > 0 && confirmedCash === cashKey;
  // Until the job is loaded the amount is unknown, so the step waits for it.
  const cashBlocked = atPickup && (!shownJob || (cashDue > 0 && !cashConfirmed));
  const CASH_FIRST = 'Collect the cash and tick the box first.';

  // Handle state transition with optimistic update and rollback on 409 Conflict
  const handleTransition = async (
    targetState: DeliveryState,
    reason?: DeliveryFailureReason,
    notes?: string
  ) => {
    if (!session?.deliveryId) return;
    const takesCash = targetState === 'PICKED_UP';
    if (takesCash && cashBlocked) {
      setErrorMsg(CASH_FIRST);
      return;
    }

    // Snapshot current session for rollback
    const previousSession = { ...session };

    // 1. Optimistic update: advance local session immediately
    setPendingState(targetState);
    setErrorMsg(null);
    setSuccessMsg(null);
    setSession({
      ...session,
      currentState: targetState,
      ...(targetState === 'DELIVERED' ? { deliveredAt: new Date().toISOString() } : {}),
      ...(targetState === 'FAILED' && reason ? { failedReason: reason } : {})
    });

    try {
      const updated = await deliveryApi.transitionDelivery(session.deliveryId, {
        targetState,
        actorRole: 'COURIER',
        failureReason: reason,
        notes: notes || `Courier transitioned to ${targetState}`,
        cashCollected: takesCash && cashDue > 0 ? cashDue : undefined
      });
      setSession(updated);
      setSuccessMsg(`Delivery moved to ${targetState.replace(/_/g, ' ')}!`);
    } catch (err: unknown) {
      // 2. Rollback on 409 Conflict or any server error
      setSession(previousSession);

      let msg = `Failed to transition to ${targetState.replace(/_/g, ' ')}`;
      if (typeof err === 'object' && err !== null && 'response' in err) {
        const axiosErr = err as {
          response?: { status?: number; data?: { message?: string; error?: string } };
        };
        const serverMsg = axiosErr.response?.data?.message || axiosErr.response?.data?.error;
        if (serverMsg) msg = serverMsg;
        // The amount may have changed since it was shown, so it is read again.
        void loadJob();
      } else if (err instanceof Error) {
        msg = err.message;
      }
      setErrorMsg(msg);
    } finally {
      setPendingState(null);
    }
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
            <h1 className="text-xl font-bold tracking-tight text-primary sm:text-2xl">
              Active delivery
            </h1>
          </div>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          {session &&
            !orderCancelled &&
            session.currentState !== 'DELIVERED' &&
            session.currentState !== 'FAILED' &&
            session.currentState !== 'CANCELLED' && (
              <Button
                variant="outline"
                onClick={() => setIsFailureModalOpen(true)}
                disabled={loading || pendingState !== null}
                className="text-xs text-status-danger-fg hover:border-status-danger-border hover:bg-status-danger-bg min-h-[44px]"
              >
                <AlertTriangle className="mr-1.5 h-4 w-4 text-status-danger-fg" />
                Can't deliver
              </Button>
            )}

          <Button
            variant="outline"
            onClick={loadDelivery}
            disabled={loading || pendingState !== null}
            className="text-xs min-h-[44px]"
          >
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
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
          <SurfaceCard className="h-32 animate-pulse bg-canvas" />
          <SurfaceCard className="h-48 animate-pulse bg-canvas" />
        </div>
      ) : !session ? (
        <SurfaceCard className="p-8 text-center">
          <Package className="mx-auto h-12 w-12 text-muted" />
          <h2 className="mt-3 text-lg font-bold text-primary">No active delivery</h2>
          <p className="mt-1 text-sm text-secondary">
            {orderIdParam
              ? `Nothing found for order ${orderIdParam}.`
              : 'New jobs appear on your shift.'}
          </p>
          <div className="mt-5">
            <Button variant="primary" onClick={() => navigate('/courier')}>
              Back to my shift
            </Button>
          </div>
        </SurfaceCard>
      ) : (
        <div className="space-y-5">
          {/* Active Job Switcher Bar */}
          {allDeliveries.length > 1 && (
            <SurfaceCard className="space-y-2.5 p-3.5">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-primary">
                  Today's Jobs ({allDeliveries.length})
                </span>
              </div>
              <div className="flex items-center gap-2 overflow-x-auto pb-1">
                {allDeliveries.map((job) => {
                  const isSelected = job.deliveryId === session.deliveryId;
                  return (
                    <button
                      key={job.deliveryId}
                      type="button"
                      onClick={() => navigate(`/courier/delivery/${job.deliveryId}`)}
                      className={cn(
                        'flex items-center gap-2 shrink-0 rounded-lg border px-3 py-1.5 text-xs font-semibold transition-all touch-manipulation cursor-pointer',
                        isSelected
                          ? 'border-brand-primary bg-brand-subtle text-brand-primary shadow-sm'
                          : 'border-border-subtle bg-card text-secondary hover:border-brand-primary hover:text-primary'
                      )}
                    >
                      <span className="font-mono">{job.trackingNumber}</span>
                      <FsmStatusBadge state={job.currentState || 'ASSIGNED'} size="sm" />
                    </button>
                  );
                })}
              </div>
            </SurfaceCard>
          )}

          {/* Visual FSM Stepper Card */}
          <SurfaceCard className="space-y-4 p-5">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm font-bold text-brand-primary">
                  {session.trackingNumber}
                </span>
              </div>
            </div>

            {/* Stepper with Touch Density */}
            <div className="py-2">
              <DeliveryProgressStepper
                current={(session.currentState || 'CREATED') as DeliveryState}
                viaHubs={viaHubs}
                density="touch"
              />
            </div>
          </SurfaceCard>

          {orderCancelled && session.currentState !== 'CANCELLED' && (
            <StatusBanner tone="danger" title="This order was cancelled">
              Do not pick it up or collect any cash. Ask your dispatcher what to do with it.
            </StatusBanner>
          )}

          {atPickup && session.merchantId && !orderCancelled && (
            <StatusBanner tone="info" title={session.merchantName ?? 'Shop pickup'}>
              <span className="block">Collect all this shop's parcels with one payment.</span>
              <Button
                variant="outline"
                className="mt-2 min-h-target"
                onClick={() => navigate(`/courier/shops/${session.merchantId}`)}
              >
                Open the shop
              </Button>
            </StatusBanner>
          )}

          {jobError && (
            <StatusBanner tone="danger" title="Addresses not loaded">
              <span className="block">{jobError}</span>
              <Button
                variant="outline"
                className="mt-2 min-h-target"
                onClick={() => void loadJob()}
              >
                Try again
              </Button>
            </StatusBanner>
          )}

          {shownJob && !shownJob.cancelled && (
            <JobDetailsCard
              job={shownJob}
              viaHubs={viaHubs}
              pickupDistrict={session.pickupDistrict}
              deliveryDistrict={session.deliveryDistrict}
              cashConfirmed={cashConfirmed}
              onCashConfirmedChange={(checked) => setConfirmedCash(checked ? cashKey : null)}
            />
          )}

          {/* Primary Action Transition Card */}
          {!orderCancelled && (
            <OneTapStateTransitionCard
              session={session}
              onTransition={handleTransition}
              onOpenPoD={() => setIsPoDModalOpen(true)}
              blocked={cashBlocked}
              isPoDVerified={isPoDVerified}
              pendingState={pendingState}
            />
          )}

          {/* Ergonomic Action Bar: thumb-friendly sticky bar for next allowed action */}
          {!orderCancelled && !cashBlocked && allowedNextStates.length > 0 && (
            <div className="sticky bottom-[76px] z-20 pt-2 md:bottom-4">
              <ErgonomicActionBar
                next={allowedNextStates}
                onTransition={(target) => {
                  if (target === 'DELIVERED' && !isPoDVerified) {
                    setIsPoDModalOpen(true);
                    return;
                  }
                  handleTransition(target);
                }}
                pendingState={pendingState}
                density="touch"
                label="Courier next actions"
              />
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
              onTransitionToDelivered={async () => {
                setIsPoDVerified(true);
                await handleTransition(
                  'DELIVERED',
                  undefined,
                  'Completed delivery with verified photo proof'
                );
              }}
            />
          )}

          {/* Report Delivery Failure Modal */}
          {session.deliveryId && (
            <DeliveryFailureReportModal
              open={isFailureModalOpen}
              onClose={() => setIsFailureModalOpen(false)}
              deliveryId={session.deliveryId}
              trackingNumber={session.trackingNumber}
              onSubmitFailure={async (reason, notes) => {
                await handleTransition('FAILED', reason, notes);
              }}
              loading={pendingState === 'FAILED'}
            />
          )}
        </div>
      )}
    </main>
  );
}
