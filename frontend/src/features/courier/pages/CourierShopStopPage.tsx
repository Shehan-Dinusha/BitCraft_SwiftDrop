import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Banknote, MapPin, Phone } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { Checkbox } from '@/design-system/components/Checkbox/Checkbox';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { lkr } from '@/features/cash/cashDisplay';
import { districtLabel } from '@/features/profile/profileFormat';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import {
  deliveryApi,
  type ShopStopAddress,
  type ShopStopResponse
} from '@/services/api/deliveryApi';

/** One visit to a shop: tick the parcels taken, collect one amount, pick them all up. */
export function CourierShopStopPage() {
  const { merchantId = '' } = useParams<{ merchantId: string }>();
  const navigate = useNavigate();
  const [stop, setStop] = useState<ShopStopResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setStop(await deliveryApi.getShopStop(merchantId));
      setError('');
    } catch (err) {
      setError(apiErrorMessage(err, 'The parcels could not be loaded. Try again.'));
    } finally {
      setLoading(false);
    }
  }, [merchantId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <main className="mx-auto max-w-3xl space-y-5 p-4 md:p-6" data-density="kinetic">
      <header className="flex items-center gap-3">
        <Button
          variant="outline"
          className="min-h-target min-w-target p-2"
          aria-label="Back to my shift"
          onClick={() => navigate('/courier')}
        >
          <ArrowLeft aria-hidden="true" className="size-5" />
        </Button>
        <h1 className="min-w-0 break-words text-h1 text-primary">
          {stop?.merchantName ?? 'Shop pickup'}
        </h1>
      </header>

      {error && (
        <StatusBanner tone="danger" title="Pickup">
          <span className="block">{error}</span>
          <Button variant="outline" className="mt-2 min-h-target" onClick={() => void load()}>
            Try again
          </Button>
        </StatusBanner>
      )}
      {message && <StatusBanner tone="success" title={message} onDismiss={() => setMessage('')} />}

      {loading && !stop && <p role="status">Loading parcels…</p>}
      {stop && stop.addresses.length === 0 && (
        <SurfaceCard>
          <div className="space-y-4 text-center">
            <p className="text-body text-secondary">No parcels left to collect here.</p>
            <Button variant="primary" className="min-h-target" onClick={() => navigate('/courier')}>
              Back to my shift
            </Button>
          </div>
        </SurfaceCard>
      )}
      {stop?.addresses.map((address) => (
        <AddressStop
          // A new set of parcels starts with a new selection and an unticked amount.
          key={address.parcels.map((parcel) => parcel.deliveryId).join()}
          merchantId={merchantId}
          address={address}
          onCollected={(text) => {
            setMessage(text);
            void load();
          }}
          onRefused={() => void load()}
        />
      ))}
    </main>
  );
}

function AddressStop({
  merchantId,
  address,
  onCollected,
  onRefused
}: {
  merchantId: string;
  address: ShopStopAddress;
  onCollected: (message: string) => void;
  onRefused: () => void;
}) {
  const [left, setLeft] = useState<ReadonlySet<string>>(new Set());
  // The amount that was ticked, so changing the parcels asks again.
  const [confirmed, setConfirmed] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const { pickup } = address;
  const taken = address.parcels.filter((parcel) => !left.has(parcel.deliveryId));
  // Whole cents: the server compares the amount exactly.
  const total = Math.round(taken.reduce((sum, parcel) => sum + parcel.fee, 0) * 100) / 100;
  const paid = total === 0 || confirmed === total;
  const phones = [pickup.phone, pickup.alternatePhone].filter((phone): phone is string =>
    Boolean(phone)
  );
  const place = [pickup.addressLine1, pickup.addressLine2, pickup.city, pickup.postalCode]
    .filter(Boolean)
    .join(', ');

  const toggle = (deliveryId: string) =>
    setLeft((current) => {
      const next = new Set(current);
      if (!next.delete(deliveryId)) next.add(deliveryId);
      return next;
    });

  const collect = async () => {
    if (busy || !paid || taken.length === 0) return;
    setBusy(true);
    setError('');
    try {
      const done = await deliveryApi.collectShop(merchantId, {
        deliveryIds: taken.map((parcel) => parcel.deliveryId),
        cashCollected: total
      });
      onCollected(
        `${done.parcels} ${done.parcels === 1 ? 'parcel' : 'parcels'} picked up` +
          (done.payment ? `, ${lkr(done.payment.amount)} collected` : '')
      );
    } catch (err) {
      setError(apiErrorMessage(err, 'The parcels could not be picked up. Try again.'));
      setConfirmed(null);
      onRefused();
    } finally {
      setBusy(false);
    }
  };

  return (
    <SurfaceCard>
      <div className="space-y-5">
        <div className="flex items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-brand-subtle text-brand-primary">
            <MapPin aria-hidden="true" className="size-4" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="break-words text-body-strong text-primary">{pickup.name}</p>
            <p className="break-words text-body text-primary">{place}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {phones.map((phone) => (
                <a
                  key={phone}
                  href={`tel:${phone}`}
                  aria-label={`Call ${pickup.name ?? 'the shop'} on ${phone}`}
                  className="inline-flex min-h-target items-center gap-2 rounded-lg border border-border-subtle bg-card px-4 text-body-strong tabular-nums text-brand-primary hover:bg-canvas"
                >
                  <Phone aria-hidden="true" className="size-4 shrink-0" />
                  {phone}
                </a>
              ))}
            </div>
          </div>
        </div>

        <fieldset>
          <legend className="pb-2 text-label text-secondary">
            Parcels you take ({taken.length} of {address.parcels.length})
          </legend>
          <ul className="divide-y divide-border-subtle rounded-xl border border-border-subtle">
            {address.parcels.map((parcel) => (
              <li key={parcel.deliveryId} className="flex items-center justify-between gap-3 px-3">
                <Checkbox
                  className="min-h-target min-w-0 flex-1"
                  checked={!left.has(parcel.deliveryId)}
                  disabled={busy}
                  onChange={() => toggle(parcel.deliveryId)}
                  label={
                    <span className="min-w-0">
                      <span className="block font-mono text-body-strong tabular-nums">
                        {parcel.trackingNumber}
                      </span>
                      <span className="block break-words text-caption text-secondary">
                        {[
                          districtLabel(parcel.deliveryDistrict) +
                            (parcel.viaHub ? ', via hub' : ''),
                          parcel.itemDescription,
                          parcel.weightKg == null ? null : `${parcel.weightKg} kg`
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </span>
                    </span>
                  }
                />
                <MoneyAmount amount={parcel.fee} size="sm" />
              </li>
            ))}
          </ul>
        </fieldset>

        {total > 0 && (
          <div className="rounded-xl border border-status-warning-border bg-status-warning-bg px-4 pt-4 text-status-warning-fg">
            <div className="flex flex-wrap items-center justify-between gap-2 pb-4">
              <span className="flex items-center gap-2 text-body-strong">
                <Banknote aria-hidden="true" className="size-5 shrink-0" />
                Collect cash from the shop
              </span>
              <MoneyAmount amount={total} size="lg" inheritColor />
            </div>
            <Checkbox
              className="min-h-target w-full border-t border-status-warning-border"
              label="I have this cash from the shop"
              checked={confirmed === total}
              disabled={busy}
              onChange={(event) => setConfirmed(event.target.checked ? total : null)}
            />
          </div>
        )}

        {error && (
          <p role="alert" className="text-body text-status-danger-fg">
            {error}
          </p>
        )}
        <Button
          variant="primary"
          className="min-h-target w-full"
          loading={busy}
          disabled={taken.length === 0 || !paid}
          onClick={() => void collect()}
        >
          Pick up {taken.length} {taken.length === 1 ? 'parcel' : 'parcels'}
        </Button>
      </div>
    </SurfaceCard>
  );
}
