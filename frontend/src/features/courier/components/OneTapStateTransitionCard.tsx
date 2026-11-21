import { useState } from 'react';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { Button } from '@/design-system/primitives/Button';
import { SlideToTransitionBar } from '@/design-system/components/SlideToTransitionBar/SlideToTransitionBar';
import { FAILURE_REASONS } from './DeliveryFailureReportModal';
import type { DeliverySessionResponse, DeliveryState, DeliveryFailureReason } from '@/services/api/deliveryApi';
import {
  PackageCheck,
  Truck,
  CheckCircle2,
  AlertTriangle,
  FileCheck,
  ChevronDown,
  ChevronUp,
  MapPin
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
        <h3 className="text-base font-bold text-slate-900">Next step</h3>
        <FsmStatusBadge state={currentState} size="lg" />
      </div>

      {/* Dynamic Action Controls based on FSM State */}
      <div className="space-y-4">
        {currentState === 'ASSIGNED' && (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
              Go to the pickup point and collect the parcel.
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
              Confirm pickup
            </Button>
          </div>
        )}

        {currentState === 'PICKED_UP' && (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
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

        {currentState === 'IN_TRANSIT' && (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">
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

        {currentState === 'DELIVERED' && (
          <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-center">
            <CheckCircle2 className="mx-auto h-8 w-8 text-emerald-600" />
            <h4 className="mt-2 font-bold text-slate-900">Delivered</h4>
            <p className="mt-1 text-xs text-slate-500">
              {session.deliveredAt ? new Date(session.deliveredAt).toLocaleTimeString() : 'Recently'}
            </p>
          </div>
        )}

        {(currentState === 'FAILED' || currentState === 'CANCELLED') && (
          <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-center">
            <AlertTriangle className="mx-auto h-8 w-8 text-rose-600" />
            <h4 className="mt-2 font-bold text-slate-900">
              {currentState === 'FAILED' ? 'Delivery failed' : 'Cancelled'}
            </h4>
            <p className="mt-1 text-xs text-slate-500">
              {FAILURE_REASONS.find((r) => r.value === session.failedReason)?.label ??
                (session.failedReason || 'No reason given')}
            </p>
          </div>
        )}

        {/* Collapsible Report Failure Section for active states */}
        {(currentState === 'ASSIGNED' || currentState === 'PICKED_UP' || currentState === 'IN_TRANSIT' || currentState === 'OUT_FOR_DELIVERY') && (
          <div className="pt-2">
            <button
              type="button"
              className="flex w-full items-center justify-between text-xs font-semibold text-rose-600 hover:text-rose-700"
              onClick={() => setShowFailurePanel(!showFailurePanel)}
            >
              <span>Can't deliver?</span>
              {showFailurePanel ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
            </button>

            {showFailurePanel && (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleReportFailure();
                }}
                className="mt-3 space-y-3 rounded-xl border border-rose-200 bg-rose-50/60 p-4"
              >
                <div className="flex items-center gap-2">
                  <AlertTriangle className="h-4 w-4 text-rose-600" />
                  <h4 className="text-xs font-bold uppercase tracking-wider text-rose-900">
                    Report failed delivery
                  </h4>
                </div>

                <label htmlFor="failure-reason" className="block text-xs font-semibold text-slate-700">
                  Reason
                </label>
                <select id="failure-reason"
                  value={failureReason}
                  onChange={(e) => setFailureReason(e.target.value as DeliveryFailureReason)}
                  className="w-full rounded-lg border border-border-subtle bg-white p-2.5 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500 min-h-[44px]"
                >
                  {FAILURE_REASONS.map((r) => (
                    <option key={r.value} value={r.value}>
                      {r.label}
                    </option>
                  ))}
                </select>

                <label htmlFor="failure-notes" className="block text-xs font-semibold text-slate-700">
                  Note
                </label>
                <input id="failure-notes"
                  type="text"
                  placeholder="e.g. Called 3 times, gate locked"
                  value={failureNotes}
                  onChange={(e) => setFailureNotes(e.target.value)}
                  className="w-full rounded-lg border border-border-subtle bg-white p-2.5 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-rose-500 min-h-[44px]"
                  required
                />

                <Button
                  type="submit"
                  variant="destructive"
                  className="w-full min-h-[44px] text-xs font-bold"
                  loading={pendingState === 'FAILED'}
                  disabled={isPending || !failureNotes.trim()}
                >
                  <AlertTriangle className="mr-2 h-4 w-4" />
                  Report failed delivery
                </Button>
              </form>
            )}
          </div>
        )}
      </div>
    </SurfaceCard>
  );
}
