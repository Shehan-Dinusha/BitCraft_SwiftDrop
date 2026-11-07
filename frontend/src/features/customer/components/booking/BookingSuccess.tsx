import { useNavigate } from 'react-router-dom';
import { CheckCircle2, FileDown, Plus, Route } from 'lucide-react';
import { Alert, Button, MoneyAmount, SpeedTag, SurfaceCard, TrackingNumber } from '@/design-system';
import type { CustomerOrderResponse } from '@/services/api/orderApi';
import { placeLabel } from './bookingForm';
import { toSpeed } from '../orders/orderDisplay';
import { useReceiptDownload } from '../orders/useReceiptDownload';

const money = (value: number) =>
  value.toLocaleString('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

interface BookingSuccessProps {
  order: CustomerOrderResponse;
  /** The estimate the customer saw before confirming. */
  quotedFee?: number;
  onBookAnother: () => void;
}

export function BookingSuccess({ order, quotedFee, onBookAnother }: BookingSuccessProps) {
  const navigate = useNavigate();
  const receipts = useReceiptDownload();
  const trackingNumber = order.trackingNumber ?? '';
  const currency = order.currency ?? 'LKR';
  const fee = order.estimatedFee ?? 0;
  const priceChanged = quotedFee !== undefined && Math.abs(quotedFee - fee) >= 0.01;

  return (
    <div className="flex flex-col items-center gap-6 text-center">
      <div className="flex flex-col items-center gap-3">
        <span className="grid size-14 place-items-center rounded-full bg-status-success-bg text-status-success-fg">
          <CheckCircle2 aria-hidden="true" className="size-7" />
        </span>
        <h2 className="text-h2 text-primary" tabIndex={-1} data-autofocus>
          Parcel booked
        </h2>
        <p className="text-body text-secondary">Your tracking number</p>
        {trackingNumber ? <TrackingNumber value={trackingNumber} size="lg" /> : null}
      </div>

      <SurfaceCard className="w-full max-w-lg text-left">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-body-strong text-primary">
              {placeLabel(order.pickupCity, order.pickupDistrict)} to{' '}
              {placeLabel(order.deliveryCity, order.deliveryDistrict)}
            </span>
            <SpeedTag speed={toSpeed(order.orderSpeed)} />
          </div>
          <div className="flex items-center justify-between border-t border-border-subtle pt-3">
            <span className="text-body text-secondary">Estimated fee</span>
            <MoneyAmount amount={fee} currency={currency} size="lg" />
          </div>
        </div>
      </SurfaceCard>

      {priceChanged ? (
        <Alert tone="info" title="The fee was updated" className="w-full max-w-lg text-left">
          Your booking was priced at {currency} {money(fee)}, not the {currency}{' '}
          {money(quotedFee ?? 0)} shown earlier.
        </Alert>
      ) : null}

      {receipts.failure ? (
        <Alert
          tone="danger"
          title="We couldn't download the label"
          onDismiss={receipts.clearFailure}
          className="w-full max-w-lg text-left"
        >
          Try again in a moment. You can also get it later from My parcels.
        </Alert>
      ) : null}

      <div className="w-full max-w-lg text-left">
        <h3 className="mb-2 text-body-strong text-primary">What happens next</h3>
        <ol className="flex list-decimal flex-col gap-1 pl-5 text-body text-secondary">
          <li>Download the label and receipt, print it and attach the label to the parcel.</li>
          <li>We assign a courier to collect it from the pickup address.</li>
          <li>Follow the parcel with your tracking number.</li>
        </ol>
      </div>

      <div className="flex w-full max-w-lg flex-col gap-3 sm:flex-row">
        <Button
          type="button"
          className="sm:flex-1"
          leftIcon={<FileDown />}
          loading={receipts.isPending(order.orderId)}
          loadingLabel="Downloading label and receipt"
          onClick={() => void receipts.download(order)}
        >
          Download label and receipt
        </Button>
        {trackingNumber ? (
          <Button
            type="button"
            variant="secondary"
            className="sm:flex-1"
            leftIcon={<Route />}
            onClick={() => navigate(`/track/${encodeURIComponent(trackingNumber)}`)}
          >
            Track parcel
          </Button>
        ) : null}
      </div>
      <Button type="button" variant="ghost" leftIcon={<Plus />} onClick={onBookAnother}>
        Book another parcel
      </Button>
    </div>
  );
}
