import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { Button } from '@/design-system/primitives/Button';
import { SlideToTransitionBar } from '@/design-system/components/SlideToTransitionBar/SlideToTransitionBar';
import { failureLabel } from '@/utils/failureReasons';
import type { DeliverySessionResponse, DeliveryState, DeliveryFailureReason } from '@/services/api/deliveryApi';
import { crossesDistricts } from '@/types/fsm';
import { districtLabel } from '@/features/profile/profileFormat';
import {
  PackageCheck,
  Truck,
  CheckCircle2,
  AlertTriangle,
  FileCheck,
  MapPin,
  Warehouse
} from 'lucide-react';

export interface OneTapStateTransitionCardProps {
  session: DeliverySessionResponse;
  onTransition: (targetState: DeliveryState, reason?: DeliveryFailureReason, notes?: string) => Promise<void>;
  onOpenPoD: () => void;
  isPoDVerified: boolean;
  pendingState: DeliveryState | null;
  /** The fee is still to be confirmed, so the pickup cannot be completed yet. */
  blocked?: boolean;
  /** This courier just handed it in at a hub, so it is no longer theirs. */
  handedIn?: boolean;
}

export function OneTapStateTransitionCard({
  session,
  onTransition,
  onOpenPoD,
  isPoDVerified,
  pendingState,
  blocked = false,
  handedIn = false
}: OneTapStateTransitionCardProps) {
  const currentState = session.currentState as DeliveryState;
  const isPending = pendingState !== null;
  // Going to another district: the courier hands it in at their district's hub instead of delivering it.
  const viaHubs = crossesDistricts(session);

  return (
    <SurfaceCard variant="elevated" className="space-y-4 p-5" data-density="touch">
      <div className="flex items-center justify-between border-b border-border-subtle pb-3">
        <h3 className="text-base font-bold text-primary">Next step</h3>
        <FsmStatusBadge state={currentState} size="lg" />
      </div>

      {/* Dynamic Action Controls based on FSM State */}
      <div className="space-y-4">
        {currentState === 'ASSIGNED' && (
          <div className="space-y-3">
            <p className="text-sm text-secondary">
              Go to the pickup point and collect the parcel.
            </p>
            <SlideToTransitionBar
              label="Slide to confirm pickup"
              confirmLabel="Release to pick up"
              onConfirm={() => onTransition('PICKED_UP', undefined, 'Courier picked up package')}
              disabled={isPending || blocked}
              density="touch"
              icon={<PackageCheck className="h-5 w-5" />}
            />
            <Button
              variant="outline"
              className="w-full min-h-[48px] text-sm"
              onClick={() => onTransition('PICKED_UP', undefined, 'Courier confirmed pickup')}
              loading={pendingState === 'PICKED_UP'}
              disabled={isPending || blocked}
            >
              <PackageCheck className="mr-2 h-4 w-4 text-status-success-fg" />
              Confirm pickup
            </Button>
          </div>
        )}

        {currentState === 'PICKED_UP' && viaHubs && (
          <div className="space-y-3">
            <p className="text-sm text-secondary">
              This parcel goes to {districtLabel(session.deliveryDistrict)}. Take it to the{' '}
              {districtLabel(session.pickupDistrict)} hub.
            </p>
            <Button
              variant="primary"
              className="w-full min-h-[48px] text-sm"
              onClick={() =>
                onTransition('AT_ORIGIN_HUB', undefined, 'Courier handed the parcel in at the hub')
              }
              loading={pendingState === 'AT_ORIGIN_HUB'}
              disabled={isPending}
            >
              <Warehouse className="mr-2 h-4 w-4" />
              Handed in at the {districtLabel(session.pickupDistrict)} hub
            </Button>
          </div>
        )}

        {currentState === 'AT_DESTINATION_HUB' && (
          <div className="space-y-3">
            <p className="text-sm text-secondary">
              Collect the parcel from the {districtLabel(session.deliveryDistrict)} hub, then take
              it to the recipient.
            </p>
            <Button
              variant="primary"
              className="w-full min-h-[48px] text-sm"
              onClick={() =>
                onTransition('OUT_FOR_DELIVERY', undefined, 'Courier collected the parcel from the hub')
              }
              loading={pendingState === 'OUT_FOR_DELIVERY'}
              disabled={isPending}
            >
              <PackageCheck className="mr-2 h-4 w-4" />
              Collected from the hub
            </Button>
          </div>
        )}

        {currentState === 'PICKED_UP' && !viaHubs && (
          <div className="space-y-3">
            <p className="text-sm text-secondary">
              Head to the delivery address.
            </p>
            <SlideToTransitionBar
              label="Slide to start transit"
              confirmLabel="Release to start trip"
              onConfirm={() => onTransition('IN_TRANSIT', undefined, 'Courier departed for delivery')}
              disabled={isPending}
              density="touch"
              icon={<Truck className="h-5 w-5" />}
            />
            <Button
              variant="primary"
              className="w-full min-h-[48px] text-sm"
              onClick={() => onTransition('IN_TRANSIT', undefined, 'Courier started transit')}
              loading={pendingState === 'IN_TRANSIT'}
              disabled={isPending}
            >
              <Truck className="mr-2 h-4 w-4" />
              Start trip
            </Button>
          </div>
        )}

        {currentState === 'IN_TRANSIT' && !viaHubs && (
          <div className="space-y-3">
            <p className="text-sm text-secondary">
              Confirm when you reach the recipient.
            </p>
            <SlideToTransitionBar
              label="Slide when you arrive"
              confirmLabel="Release to confirm arrival"
              onConfirm={() => onTransition('OUT_FOR_DELIVERY', undefined, 'Courier arrived at destination')}
              disabled={isPending}
              density="touch"
              icon={<MapPin className="h-5 w-5" />}
            />
            <Button
              variant="primary"
              className="w-full min-h-[48px] text-sm"
              onClick={() => onTransition('OUT_FOR_DELIVERY', undefined, 'Courier marked Out for Delivery')}
              loading={pendingState === 'OUT_FOR_DELIVERY'}
              disabled={isPending}
            >
              <MapPin className="mr-2 h-4 w-4" />
              I've arrived
            </Button>
          </div>
        )}

        {currentState === 'OUT_FOR_DELIVERY' && (
          <div className="space-y-3">
            <div className={`rounded-xl border p-4 ${
              isPoDVerified
                ? 'border-status-success-border bg-status-success-bg'
                : 'border-status-warning-border bg-status-warning-bg'
            }`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {isPoDVerified ? (
                    <CheckCircle2 className="h-5 w-5 text-status-success-fg" />
                  ) : (
                    <FileCheck className="h-5 w-5 text-status-warning-fg" />
                  )}
                  <span className="text-sm font-bold text-primary">
                    {isPoDVerified ? 'Proof captured' : 'Proof of delivery needed'}
                  </span>
                </div>
                <Button
                  variant={isPoDVerified ? 'outline' : 'primary'}
                  className="min-h-[40px] text-xs"
                  onClick={onOpenPoD}
                >
                  {isPoDVerified ? 'Update' : 'Take photo'}
                </Button>
              </div>
            </div>

            {isPoDVerified ? (
              <SlideToTransitionBar
                label="Slide to complete delivery"
                confirmLabel="Release to deliver"
                onConfirm={() => onTransition('DELIVERED', undefined, 'Handover completed with verified PoD')}
                disabled={isPending}
                density="touch"
                icon={<CheckCircle2 className="h-5 w-5" />}
              />
            ) : (
              <Button
                variant="secondary"
                className="w-full min-h-[48px] text-sm opacity-60"
                onClick={onOpenPoD}
              >
                Take a photo to complete
              </Button>
            )}
          </div>
        )}

        {currentState === 'ATTEMPT_FAILED' && (
          <div className="space-y-3">
            <p className="text-body text-primary">
              {failureLabel(session.failedReason)}. Bring it to the{' '}
              {districtLabel(session.deliveryDistrict)} hub.
            </p>
            <Button
              variant="primary"
              className="w-full min-h-target"
              onClick={() =>
                onTransition('HELD_AT_HUB', undefined, 'Courier handed the parcel in after a failed delivery')
              }
              loading={pendingState === 'HELD_AT_HUB'}
              disabled={isPending}
            >
              <Warehouse className="mr-2 h-4 w-4" />
              Handed in at the {districtLabel(session.deliveryDistrict)} hub
            </Button>
          </div>
        )}

        {currentState === 'RETURN_AT_HUB' && !handedIn && (
          <div className="space-y-3">
            <p className="text-body text-primary">
              Collect the parcel from the {districtLabel(session.pickupDistrict)} hub, then bring it
              back to the sender.
            </p>
            <Button
              variant="primary"
              className="w-full min-h-target"
              onClick={() =>
                onTransition('OUT_FOR_RETURN', undefined, 'Courier collected the return from the hub')
              }
              loading={pendingState === 'OUT_FOR_RETURN'}
              disabled={isPending}
            >
              <PackageCheck className="mr-2 h-4 w-4" />
              Collected from the hub
            </Button>
          </div>
        )}

        {currentState === 'OUT_FOR_RETURN' && (
          <div className="space-y-3">
            <p className="text-body text-primary">
              Hand the parcel back to the sender and take a photo. Nothing is to be paid.
            </p>
            <Button
              variant="primary"
              className="w-full min-h-target"
              onClick={onOpenPoD}
              disabled={isPending}
            >
              <FileCheck className="mr-2 h-4 w-4" />
              Take a photo to complete
            </Button>
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
          </div>
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
            <p className="mt-1 text-xs text-secondary">
              {failureLabel(session.failedReason)}
            </p>
          </div>
        )}
      </div>
    </SurfaceCard>
  );
}
