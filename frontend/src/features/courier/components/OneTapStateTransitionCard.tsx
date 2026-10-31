import { useState } from 'react';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { Button } from '@/design-system/primitives/Button';
import { SlideToTransitionBar } from '@/design-system/components/SlideToTransitionBar/SlideToTransitionBar';
import type { DeliverySessionResponse, DeliveryState, DeliveryFailureReason } from '@/services/api/deliveryApi';
import {
  PackageCheck,
  Truck,
  CheckCircle2,
  AlertTriangle,
  FileCheck,
  ChevronDown,
  ChevronUp
} from 'lucide-react';

export interface OneTapStateTransitionCardProps {
  session: DeliverySessionResponse;
  onTransition: (targetState: DeliveryState, reason?: DeliveryFailureReason, notes?: string) => Promise<void>;
  onOpenPoD: () => void;
  isPoDVerified: boolean;
  pendingState: DeliveryState | null;
}

export function OneTapStateTransitionCard({
  session,
  onTransition,
  onOpenPoD,
  isPoDVerified,
  pendingState
}: OneTapStateTransitionCardProps) {
  const [showFailurePanel, setShowFailurePanel] = useState(false);
  const [failureReason, setFailureReason] = useState<DeliveryFailureReason>('RECIPIENT_UNAVAILABLE');
  const [failureNotes, setFailureNotes] = useState('');

  const currentState = session.currentState as DeliveryState;
  const isPending = pendingState !== null;

  const handleReportFailure = async () => {
    await onTransition('FAILED', failureReason, failureNotes || 'Reported by courier via mobile app');
    setShowFailurePanel(false);
  };

  return (
    <SurfaceCard variant="elevated" className="space-y-4 p-5" data-density="touch">
      <div className="flex items-center justify-between border-b border-border-subtle pb-3">
        <div>
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
            FSM Transition Action
          </span>
          <h3 className="text-base font-bold text-slate-900">
            Current Stage: {currentState}
          </h3>
        </div>
        <FsmStatusBadge state={currentState} size="lg" />
      </div>

      {/* Dynamic Action Controls based on FSM State */}
      <div className="space-y-4">
        {currentState === 'ASSIGNED' && (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
              You are assigned to this delivery. Arrive at pickup point and confirm receipt of package.
            </p>
            <SlideToTransitionBar
              label="Slide to confirm pickup"
              confirmLabel="Release to pick up"
              onConfirm={() => onTransition('PICKED_UP', undefined, 'Courier picked up package')}
              disabled={isPending}
              density="touch"
              icon={<PackageCheck className="h-5 w-5" />}
            />
            <Button
              variant="outline"
              className="w-full min-h-[48px] text-sm"
              onClick={() => onTransition('PICKED_UP', undefined, 'Courier confirmed pickup')}
              loading={pendingState === 'PICKED_UP'}
              disabled={isPending}
            >
              <PackageCheck className="mr-2 h-4 w-4 text-emerald-500" />
              One-Tap: Confirm Package Pickup
            </Button>
          </div>
        )}

        {currentState === 'PICKED_UP' && (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
              Package securely in your possession. Start the transit to customer delivery address.
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
              One-Tap: Start Delivery Transit
            </Button>
          </div>
        )}

        {currentState === 'IN_TRANSIT' && (
          <div className="space-y-3">
            <div className={`rounded-xl border p-4 ${
              isPoDVerified
                ? 'border-emerald-500/40 bg-emerald-500/10'
                : 'border-amber-500/40 bg-amber-500/10'
            }`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {isPoDVerified ? (
                    <CheckCircle2 className="h-5 w-5 text-emerald-600" />
                  ) : (
                    <FileCheck className="h-5 w-5 text-amber-600" />
                  )}
                  <span className="text-sm font-bold text-slate-900">
                    {isPoDVerified ? 'PoD Guard: Cleared' : 'PoD Guard: Required'}
                  </span>
                </div>
                <Button
                  variant={isPoDVerified ? 'outline' : 'primary'}
                  className="min-h-[40px] text-xs"
                  onClick={onOpenPoD}
                >
                  {isPoDVerified ? 'View / Update PoD' : 'Capture PoD (OTP / Photo)'}
                </Button>
              </div>
              <p className="mt-2 text-xs text-slate-600">
                {isPoDVerified
                  ? 'Proof of delivery verified! You can now complete the final delivery handover.'
                  : 'Recipient OTP or photographic/signature evidence must be captured before handover can be completed.'}
              </p>
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
                Complete Delivery (Capture PoD first)
              </Button>
            )}
          </div>
        )}

        {currentState === 'DELIVERED' && (
          <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-center">
            <CheckCircle2 className="mx-auto h-8 w-8 text-emerald-600" />
            <h4 className="mt-2 font-bold text-slate-900">Delivery Completed</h4>
            <p className="mt-1 text-xs text-slate-500">
              Completed at: {session.deliveredAt ? new Date(session.deliveredAt).toLocaleTimeString() : 'Recently'}
            </p>
          </div>
        )}

        {(currentState === 'FAILED' || currentState === 'CANCELLED') && (
          <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-center">
            <AlertTriangle className="mx-auto h-8 w-8 text-rose-600" />
            <h4 className="mt-2 font-bold text-slate-900">Order {currentState}</h4>
            <p className="mt-1 text-xs text-slate-500">
              Reason: {session.failedReason || 'Not specified'}
            </p>
          </div>
        )}

        {/* Collapsible Report Failure Section for active states */}
        {(currentState === 'ASSIGNED' || currentState === 'PICKED_UP' || currentState === 'IN_TRANSIT') && (
          <div className="pt-2">
            <button
              type="button"
              className="flex w-full items-center justify-between text-xs font-semibold text-rose-600 hover:text-rose-700"
              onClick={() => setShowFailurePanel(!showFailurePanel)}
            >
              <span>Need to report delivery issue or failure?</span>
              {showFailurePanel ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            </button>

            {showFailurePanel && (
              <div className="mt-3 space-y-3 rounded-xl border border-rose-200 bg-rose-50/50 p-4">
                <label className="block text-xs font-semibold text-slate-700">
                  Select Failure Reason:
                </label>
                <select
                  value={failureReason}
                  onChange={(e) => setFailureReason(e.target.value as DeliveryFailureReason)}
                  className="w-full rounded-lg border border-border-subtle bg-white p-2.5 text-xs text-slate-800"
                >
                  <option value="RECIPIENT_UNAVAILABLE">Recipient Unavailable / Not Home</option>
                  <option value="INCORRECT_ADDRESS">Incorrect / Incomplete Address</option>
                  <option value="CUSTOMER_REJECTED">Customer Refused Package</option>
                  <option value="PACKAGE_DAMAGED">Package Damaged During Transit</option>
                  <option value="COURIER_BREAKDOWN">Vehicle / Courier Breakdown</option>
                  <option value="OTHER">Other Reason</option>
                </select>

                <input
                  type="text"
                  placeholder="Additional context or notes..."
                  value={failureNotes}
                  onChange={(e) => setFailureNotes(e.target.value)}
                  className="w-full rounded-lg border border-border-subtle bg-white p-2.5 text-xs text-slate-800"
                />

                <Button
                  variant="destructive"
                  className="w-full min-h-[44px] text-xs font-bold"
                  onClick={handleReportFailure}
                  loading={pendingState === 'FAILED'}
                  disabled={isPending}
                >
                  Confirm & Report Delivery Failure
                </Button>
              </div>
            )}
          </div>
        )}
      </div>
    </SurfaceCard>
  );
}
