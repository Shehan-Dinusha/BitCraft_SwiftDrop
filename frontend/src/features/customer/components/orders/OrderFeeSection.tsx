import type { ReactNode } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { MoneyAmount, SurfaceCard } from '@/design-system';
import type { CustomerOrderResponse } from '@/services/api/orderApi';
import {
  PAID_NOTE,
  PAYMENT_NOTE,
  feePaid,
  hasPendingFeeRevision,
  payableFee
} from './orderDisplay';

/**
 * The delivery fee, how it changed if the courier re-measured the parcel, and whether it is paid. The
 * sender pays the courier at pickup, so from then on it reads "Paid" instead of "To pay".
 */
export function OrderFeeSection({ order }: { order: CustomerOrderResponse }) {
  const currency = order.currency ?? 'LKR';
  const booked = order.estimatedFee ?? 0;
  const pending = hasPendingFeeRevision(order);
  const current = payableFee(order);
  const changed = !pending && current !== booked;
  const paid = feePaid(order);
  const billedByWeight =
    order.chargeableWeightKg !== undefined &&
    order.actualWeightKg !== undefined &&
    order.chargeableWeightKg > order.actualWeightKg;

  return (
    <SurfaceCard title="Delivery fee">
      <dl className="flex flex-col gap-2">
        {pending ? (
          <>
            <Row label="Fee at booking">
              <MoneyAmount amount={booked} size="sm" currency={currency} />
            </Row>
            <Row label="New fee, waiting for your answer">
              <MoneyAmount amount={order.revisedFee ?? 0} size="sm" currency={currency} />
            </Row>
          </>
        ) : (
          <>
            {changed ? (
              <Row label="Fee at booking">
                <MoneyAmount amount={booked} size="sm" currency={currency} />
              </Row>
            ) : null}
            <div className="flex items-center justify-between gap-4">
              <dt className="flex items-center gap-2 text-body-strong text-primary">
                {paid ? 'Paid' : changed ? 'Updated fee' : 'To pay'}
                {paid ? (
                  <CheckCircle2 aria-hidden="true" className="size-4 text-status-success" />
                ) : null}
              </dt>
              <dd>
                <MoneyAmount amount={current} size="lg" currency={currency} />
              </dd>
            </div>
          </>
        )}
      </dl>
      <p className="mt-3 text-caption text-secondary">{paid ? PAID_NOTE : PAYMENT_NOTE}</p>
      {billedByWeight ? (
        <p className="mt-3 text-caption text-secondary">
          Charged as {order.chargeableWeightKg} kg because the parcel&apos;s size counts for more
          than its weight.
        </p>
      ) : null}
    </SurfaceCard>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-body text-secondary">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}
