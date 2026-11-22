import { useCallback, useEffect, useState } from 'react';
import { Printer, RefreshCw, Truck } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { HighDensityDataGrid } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { lkr } from '@/features/cash/cashDisplay';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import {
  merchantApi,
  type MerchantPickupResponse,
  type MerchantPickupState
} from '@/services/api/merchantApi';
import { orderApi } from '@/services/api/orderApi';

const STATE_LABEL: Record<MerchantPickupState, string> = {
  WAITING: 'Not ready',
  READY: 'Ready',
  ASSIGNED: 'Courier coming'
};

/** Parcels still at the shop: pack them, print the labels, say they are ready, and prepare the cash. */
export function MerchantPickupCard({
  merchantId,
  refreshToken = 0
}: {
  merchantId: string;
  refreshToken?: number;
}) {
  const [pickup, setPickup] = useState<MerchantPickupResponse | null>(null);
  const [busy, setBusy] = useState<'load' | 'ready' | 'labels' | null>('load');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setBusy('load');
    try {
      setPickup(await merchantApi.getPickup(merchantId));
      setError('');
    } catch (err) {
      setError(apiErrorMessage(err, 'Your pickup could not be loaded.'));
    } finally {
      setBusy(null);
    }
  }, [merchantId]);

  useEffect(() => {
    void load();
  }, [load, refreshToken]);

  const markReady = async () => {
    setBusy('ready');
    setError('');
    setMessage('');
    try {
      setPickup(await merchantApi.markPickupReady(merchantId));
      setMessage('Marked ready. A courier will be sent.');
    } catch (err) {
      setError(apiErrorMessage(err, 'The parcels could not be marked ready.'));
    } finally {
      setBusy(null);
    }
  };

  const printLabels = async () => {
    setBusy('labels');
    setError('');
    try {
      await orderApi.downloadShippingLabels(
        (pickup?.parcels ?? []).map((parcel) => parcel.orderId)
      );
    } catch {
      setError('The labels could not be downloaded. Try again.');
    } finally {
      setBusy(null);
    }
  };

  // Cash to have in hand when the courier comes: everything packed or on its way to be collected.
  const toPay = pickup ? pickup.ready.total + pickup.assigned.total : 0;
  const withoutFee = pickup ? pickup.ready.withoutFee + pickup.assigned.withoutFee : 0;

  return (
    <SurfaceCard density="compact">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-h3 text-primary">Pickup</h2>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              leftIcon={<RefreshCw className={busy === 'load' ? 'animate-spin' : undefined} />}
              disabled={busy !== null}
              onClick={() => void load()}
            >
              Refresh
            </Button>
            <Button
              variant="outline"
              leftIcon={<Printer />}
              loading={busy === 'labels'}
              disabled={!pickup?.parcels.length || (busy !== null && busy !== 'labels')}
              onClick={() => void printLabels()}
            >
              Print labels
            </Button>
            <Button
              variant="primary"
              leftIcon={<Truck />}
              loading={busy === 'ready'}
              disabled={!pickup?.waiting.parcels || (busy !== null && busy !== 'ready')}
              onClick={() => void markReady()}
            >
              Ready for pickup
            </Button>
          </div>
        </div>

        {error && (
          <StatusBanner tone="danger" title="Pickup" onDismiss={() => setError('')}>
            {error}
          </StatusBanner>
        )}
        {message && (
          <StatusBanner tone="success" title={message} onDismiss={() => setMessage('')} />
        )}

        {pickup && (
          <>
            <dl className="grid gap-3 sm:grid-cols-4">
              <Group label="Not ready" parcels={pickup.waiting.parcels} />
              <Group label="Ready" parcels={pickup.ready.parcels} />
              <Group label="Courier coming" parcels={pickup.assigned.parcels} />
              <div className="rounded-lg bg-canvas p-3">
                <dt className="text-label text-secondary">Cash to prepare</dt>
                <dd>
                  <MoneyAmount amount={toPay} currency={pickup.currency} size="lg" />
                  {withoutFee > 0 && (
                    <span className="block text-caption text-secondary">
                      plus {withoutFee} older {withoutFee === 1 ? 'parcel' : 'parcels'}
                    </span>
                  )}
                </dd>
              </div>
            </dl>
            {pickup.parcels.length > 0 && (
              <HighDensityDataGrid
                density="compact"
                label="Parcels at your shop"
                filterPlaceholder="Search reference or tracking number"
                rows={pickup.parcels.map((parcel) => ({ ...parcel, id: parcel.orderId }))}
                columns={[
                  { key: 'externalRef', label: 'Reference', render: (r) => r.externalRef ?? '—' },
                  { key: 'trackingNumber', label: 'Tracking number' },
                  {
                    key: 'state',
                    label: 'Pickup',
                    render: (r) =>
                      r.state === 'ASSIGNED' && r.courierName
                        ? `${r.courierName} is coming`
                        : STATE_LABEL[r.state]
                  },
                  {
                    key: 'fee',
                    label: 'Delivery fee',
                    align: 'right',
                    render: (r) => (r.fee == null ? '—' : lkr(r.fee))
                  }
                ]}
              />
            )}
          </>
        )}
      </div>
    </SurfaceCard>
  );
}

function Group({ label, parcels }: { label: string; parcels: number }) {
  return (
    <div className="rounded-lg bg-canvas p-3">
      <dt className="text-label text-secondary">{label}</dt>
      <dd className="text-h2 tabular-nums text-primary">{parcels}</dd>
    </div>
  );
}
