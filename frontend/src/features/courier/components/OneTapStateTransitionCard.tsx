import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { Button } from '@/design-system/primitives/Button';
import { failureLabel } from '@/utils/failureReasons';
import type { DeliverySessionResponse, DeliveryState, DeliveryFailureReason } from '@/services/api/deliveryApi';
import { crossesDistricts, goesViaHub } from '@/types/fsm';
import { districtLabel } from '@/features/profile/profileFormat';
import { CheckCircle2, AlertTriangle, FileCheck, Warehouse } from 'lucide-react';

export interface OneTapStateTransitionCardProps {
  session: DeliverySessionResponse;
  /** Only for the side step a return can take: the sender was not there, so it goes back to the hub. */
  onTransition: (targetState: DeliveryState, reason?: DeliveryFailureReason, notes?: string) => Promise<void>;
  isPoDVerified: boolean;
  pendingState: DeliveryState | null;
  /** This courier just handed it in at a hub, so it is no longer theirs. */
  handedIn?: boolean;
}

/**
 * What to do next on this parcel, in words. The step itself is the one slide at the bottom of the page
 * (`courierStep`), so nothing here repeats it.
 */
export function OneTapStateTransitionCard({
  session,
  onTransition,
  isPoDVerified,
  pendingState,
  handedIn = false
}: OneTapStateTransitionCardProps) {
  const currentState = session.currentState as DeliveryState;
  const isPending = pendingState !== null;
  const viaHubs = goesViaHub(session);
  const otherDistrict = crossesDistricts(session);
  const pickupHub = districtLabel(session.pickupDistrict);
  const deliveryHub = districtLabel(session.deliveryDistrict);

  const instruction: Partial<Record<DeliveryState, string>> = {
    ASSIGNED: 'Go to the pickup point and collect the parcel.',
    PICKED_UP: otherDistrict
      ? `This parcel goes to ${deliveryHub}. Take it to the ${pickupHub} hub.`
      : viaHubs
        ? `Take it to the ${pickupHub} hub. It goes out on a delivery run from there.`
        : 'Same day: head to the delivery address.',
    IN_TRANSIT: viaHubs ? '' : 'Confirm when you reach the recipient.',
    AT_DESTINATION_HUB: `Collect the parcel from the ${deliveryHub} hub, then take it to the recipient.`,
    ATTEMPT_FAILED: `${failureLabel(session.failedReason)}. Bring it to the ${deliveryHub} hub.`,
    RETURN_AT_HUB: handedIn
      ? ''
      : `Collect the parcel from the ${pickupHub} hub, then bring it back to the sender.`,
    OUT_FOR_RETURN: 'Hand the parcel back to the sender and take a photo. Nothing is to be paid.'
  };
  const text = instruction[currentState];

  return (
    <SurfaceCard variant="elevated" className="p-5" bodyClassName="space-y-4" data-density="touch">
      <div className="flex items-center justify-between border-b border-border-subtle pb-3">
        <h3 className="text-base font-bold text-primary">Next step</h3>
        <FsmStatusBadge state={currentState} size="lg" />
      </div>

      <div className="space-y-4">
        {text ? <p className="text-body text-primary">{text}</p> : null}

        {currentState === 'OUT_FOR_DELIVERY' && (
          <div
            className={`flex items-center gap-2 rounded-xl border p-4 ${
              isPoDVerified
                ? 'border-status-success-border bg-status-success-bg'
                : 'border-status-warning-border bg-status-warning-bg'
            }`}
          >
            {isPoDVerified ? (
              <CheckCircle2 className="h-5 w-5 text-status-success-fg" />
            ) : (
              <FileCheck className="h-5 w-5 text-status-warning-fg" />
            )}
            <span className="text-sm font-bold text-primary">
              {isPoDVerified ? 'Proof captured' : 'Proof of delivery needed'}
            </span>
          </div>
        )}

        {currentState === 'OUT_FOR_RETURN' && (
          <Button
            variant="outline"
            className="w-full min-h-target"
            onClick={() =>
              onTransition('RETURN_AT_HUB', undefined, 'The sender could not take it; back at the hub')
            }
            loading={pendingState === 'RETURN_AT_HUB'}
            disabled={isPending}
          >
            <Warehouse className="mr-2 h-4 w-4" />
            Sender not there, back at the hub
          </Button>
        )}

        {(handedIn || currentState === 'HELD_AT_HUB') && (
          <div className="rounded-xl border border-status-success-border bg-status-success-bg p-4 text-center">
            <CheckCircle2 className="mx-auto h-8 w-8 text-status-success-fg" />
            <h4 className="mt-2 font-bold text-primary">Handed in at the hub</h4>
          </div>
        )}

        {currentState === 'RETURNED' && (
          <div className="rounded-xl border border-status-success-border bg-status-success-bg p-4 text-center">
            <CheckCircle2 className="mx-auto h-8 w-8 text-status-success-fg" />
            <h4 className="mt-2 font-bold text-primary">Returned to the sender</h4>
          </div>
        )}

        {currentState === 'DELIVERED' && (
          <div className="rounded-xl border border-status-success-border bg-status-success-bg p-4 text-center">
            <CheckCircle2 className="mx-auto h-8 w-8 text-status-success-fg" />
            <h4 className="mt-2 font-bold text-primary">Delivered</h4>
            <p className="mt-1 text-xs text-secondary">
              {session.deliveredAt ? new Date(session.deliveredAt).toLocaleTimeString() : 'Recently'}
            </p>
          </div>
        )}

        {(currentState === 'FAILED' || currentState === 'CANCELLED') && (
          <div className="rounded-xl border border-status-danger-border bg-status-danger-bg p-4 text-center">
            <AlertTriangle className="mx-auto h-8 w-8 text-status-danger-fg" />
            <h4 className="mt-2 font-bold text-primary">
              {currentState === 'FAILED' ? 'Delivery failed' : 'Cancelled'}
            </h4>
            <p className="mt-1 text-xs text-secondary">{failureLabel(session.failedReason)}</p>
          </div>
        )}
      </div>
    </SurfaceCard>
  );
}
