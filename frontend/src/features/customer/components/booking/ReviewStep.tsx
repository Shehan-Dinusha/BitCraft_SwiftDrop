import type { ReactNode } from 'react';
import { Pencil } from 'lucide-react';
import {
  Alert,
  Button,
  Checkbox,
  MoneyAmount,
  PriceBreakdown,
  Skeleton,
  SpeedTag,
  SurfaceCard
} from '@/design-system';
import type { FeeEstimateResponse } from '@/services/api/orderApi';
import { SPEED_LABEL, toSpeed } from '../orders/orderDisplay';
import { placeLabel, type BookingForm } from './bookingForm';
import type { BookingErrors, BookingStepId } from './validation';
import type { FeeEstimateStatus } from './useFeeEstimate';

interface ReviewStepProps {
  form: BookingForm;
  errors: BookingErrors;
  estimate: FeeEstimateResponse | null;
  estimateStatus: FeeEstimateStatus;
  onRetryEstimate: () => void;
  onDeclare: (value: boolean) => void;
  onEdit: (step: BookingStepId) => void;
}

const formatPhone = (digits: string) =>
  `+94 ${digits.slice(0, 2)} ${digits.slice(2, 5)} ${digits.slice(5)}`.trim();

export function ReviewStep({
  form,
  errors,
  estimate,
  estimateStatus,
  onRetryEstimate,
  onDeclare,
  onEdit
}: ReviewStepProps) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <h2 className="text-h3 text-primary">Check and confirm</h2>
        <p className="text-body text-secondary">
          Nothing is booked until you confirm. You can still change any detail.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex flex-col gap-3">
          <Summary title="Pickup" onEdit={() => onEdit('pickup')}>
            <Line strong>{form.pickupContactName}</Line>
            <Line mono>{formatPhone(form.pickupContactPhone)}</Line>
            <Line>
              {[form.pickupAddressLine1, form.pickupAddressLine2].filter(Boolean).join(', ')}
            </Line>
            <Line>
              {placeLabel(form.pickupCity, form.pickupDistrict)} ·{' '}
              <span className="tabular-nums">{form.pickupPostalCode}</span>
            </Line>
          </Summary>

          <Summary title="Drop-off" onEdit={() => onEdit('dropoff')}>
            <Line strong>{form.recipientName}</Line>
            <Line mono>{formatPhone(form.recipientPhone)}</Line>
            <Line>{form.recipientEmail}</Line>
            <Line>
              {[form.deliveryAddressLine1, form.deliveryAddressLine2].filter(Boolean).join(', ')}
            </Line>
            <Line>
              {placeLabel(form.deliveryCity, form.deliveryDistrict)} ·{' '}
              <span className="tabular-nums">{form.deliveryPostalCode}</span>
            </Line>
          </Summary>

          <Summary title="Parcel" onEdit={() => onEdit('parcel')}>
            <div className="flex flex-wrap items-center gap-2">
              <SpeedTag speed={toSpeed(form.speed)} />
              <span className="text-body tabular-nums text-primary">
                {form.actualWeightKg} kg · {form.lengthCm} × {form.widthCm} × {form.heightCm} cm
              </span>
            </div>
            {form.itemDescription ? <Line>{form.itemDescription}</Line> : null}
            {form.declaredValue ? (
              <Line>
                Declared value <MoneyAmount amount={Number(form.declaredValue)} size="sm" />
              </Line>
            ) : null}
            {form.codAmount ? (
              <Line>
                Cash on delivery <MoneyAmount amount={Number(form.codAmount)} size="sm" />
              </Line>
            ) : null}
          </Summary>
        </div>

        {/* On phones the price comes first, next to the decision; on desktop it sits beside the summary. */}
        <div
          className="order-first flex flex-col gap-3 lg:order-none"
          aria-busy={estimateStatus === 'loading'}
        >
          <p className="sr-only" role="status">
            {estimateStatus === 'ready' && estimate
              ? `Estimated fee ${estimate.currency ?? 'LKR'} ${(estimate.estimatedFee ?? 0).toFixed(2)}`
              : estimateStatus === 'loading'
                ? 'Getting your price'
                : ''}
          </p>
          <FeePanel
            estimate={estimate}
            status={estimateStatus}
            onRetry={onRetryEstimate}
            speedLabel={SPEED_LABEL[form.speed]}
          />
        </div>
      </div>

      <div className="rounded-xl border border-border-subtle bg-canvas p-4">
        <Checkbox
          checked={form.prohibitedItemsDeclared}
          onChange={(event) => onDeclare(event.target.checked)}
          aria-invalid={errors.prohibitedItemsDeclared ? true : undefined}
          aria-describedby={errors.prohibitedItemsDeclared ? 'declaration-error' : undefined}
          label={
            <span className="text-body text-primary">
              I confirm this parcel has no prohibited, flammable, explosive or toxic items, and I
              agree to an inspection at pickup.
            </span>
          }
        />
        {errors.prohibitedItemsDeclared ? (
          <p id="declaration-error" className="mt-2 text-caption text-status-danger">
            {errors.prohibitedItemsDeclared}
          </p>
        ) : null}
      </div>
    </div>
  );
}

function FeePanel({
  estimate,
  status,
  onRetry,
  speedLabel
}: {
  estimate: FeeEstimateResponse | null;
  status: FeeEstimateStatus;
  onRetry: () => void;
  speedLabel: string;
}) {
  if (status === 'loading' || status === 'idle') {
    return <Skeleton preset="card" className="w-full" />;
  }
  if (status === 'unavailable' || status === 'error' || !estimate) {
    return (
      <Alert
        tone={status === 'unavailable' ? 'warning' : 'danger'}
        title={
          status === 'unavailable'
            ? 'Pricing is temporarily unavailable'
            : "We couldn't get a price"
        }
        action={
          <Button type="button" variant="ghost" onClick={onRetry}>
            Try again
          </Button>
        }
      >
        Your details are saved. Try again in a moment.
      </Alert>
    );
  }

  const base = estimate.baseFee ?? 0;
  const weight = estimate.weightFee ?? 0;
  const surcharge = estimate.speedSurcharge ?? 0;

  return (
    <PriceBreakdown
      type="customer"
      title="Estimated fee"
      currency={estimate.currency ?? 'LKR'}
      lines={[
        { label: 'Base fee', note: 'First 1 kg included', amount: base },
        {
          label: 'Weight fee',
          note: `Charged as ${estimate.chargeableWeightKg ?? '–'} kg, by weight or size, whichever is more`,
          amount: weight
        }
      ]}
      subtotal={base + weight}
      surcharge={surcharge > 0 ? { label: speedLabel, amount: surcharge } : undefined}
      total={estimate.estimatedFee ?? base + weight + surcharge}
      footnote="The courier checks weight and size at pickup. If they differ, we ask you before the fee changes."
    />
  );
}

function Summary({
  title,
  onEdit,
  children
}: {
  title: string;
  onEdit: () => void;
  children: ReactNode;
}) {
  return (
    <SurfaceCard
      title={title}
      actions={
        <Button
          type="button"
          variant="ghost"
          leftIcon={<Pencil />}
          onClick={onEdit}
          aria-label={`Edit ${title.toLowerCase()} details`}
        >
          Edit
        </Button>
      }
    >
      <div className="flex flex-col gap-1">{children}</div>
    </SurfaceCard>
  );
}

function Line({
  children,
  strong,
  mono
}: {
  children: ReactNode;
  strong?: boolean;
  mono?: boolean;
}) {
  return (
    <p
      className={
        strong
          ? 'text-body-strong text-primary'
          : mono
            ? 'text-body tabular-nums text-secondary'
            : 'text-body text-secondary'
      }
    >
      {children}
    </p>
  );
}
