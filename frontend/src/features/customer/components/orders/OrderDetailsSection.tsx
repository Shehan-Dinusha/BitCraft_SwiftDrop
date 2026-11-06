import { useId, useState, type ReactNode } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { Button, MoneyAmount, SurfaceCard } from '@/design-system';
import type { CustomerOrderResponse } from '@/services/api/orderApi';
import { placeLabel } from '../booking/bookingForm';
import { formatPhone } from './orderDisplay';

/**
 * What the customer entered at booking. Collapsed by default: it is their own data and rarely what
 * they came for, so status and actions stay near the top (progressive disclosure).
 */
export function OrderDetailsSection({ order }: { order: CustomerOrderResponse }) {
  const [open, setOpen] = useState(false);
  const bodyId = useId();

  return (
    <SurfaceCard
      title="Booking details"
      actions={
        <Button
          type="button"
          variant="ghost"
          rightIcon={open ? <ChevronUp /> : <ChevronDown />}
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={() => setOpen((value) => !value)}
        >
          {open ? 'Hide' : 'Show'}
        </Button>
      }
    >
      {open ? (
        <div id={bodyId} className="grid grid-cols-1 gap-6 md:grid-cols-3">
          <Group title="Pickup">
            <Line strong>{order.pickupContactName}</Line>
            <Line numeric>{formatPhone(order.pickupContactPhone)}</Line>
            <Line>{joinLines(order.pickupAddressLine1, order.pickupAddressLine2)}</Line>
            <Line>
              {placeLabel(order.pickupCity, order.pickupDistrict)} ·{' '}
              <span className="tabular-nums">{order.pickupPostalCode}</span>
            </Line>
          </Group>
          <Group title="Drop-off">
            <Line strong>{order.recipientName}</Line>
            <Line numeric>{formatPhone(order.recipientPhone)}</Line>
            <Line>{order.recipientEmail}</Line>
            <Line>{joinLines(order.deliveryAddressLine1, order.deliveryAddressLine2)}</Line>
            <Line>
              {placeLabel(order.deliveryCity, order.deliveryDistrict)} ·{' '}
              <span className="tabular-nums">{order.deliveryPostalCode}</span>
            </Line>
          </Group>
          <Group title="Parcel">
            <Line numeric>
              {order.actualWeightKg} kg · {order.lengthCm} × {order.widthCm} × {order.heightCm} cm
            </Line>
            {order.itemDescription ? <Line>{order.itemDescription}</Line> : null}
            {order.declaredValue ? (
              <Line>
                Declared value <MoneyAmount amount={order.declaredValue} size="sm" />
              </Line>
            ) : null}
            {order.codAmount ? (
              <Line>
                Collect on delivery <MoneyAmount amount={order.codAmount} size="sm" />
              </Line>
            ) : null}
          </Group>
        </div>
      ) : (
        <p id={bodyId} className="text-body text-secondary">
          Pickup and drop-off contacts, addresses and parcel size.
        </p>
      )}
    </SurfaceCard>
  );
}

const joinLines = (...lines: (string | undefined)[]) => lines.filter(Boolean).join(', ');

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-1">
      <h4 className="mb-1 text-label font-semibold text-secondary">{title}</h4>
      {children}
    </section>
  );
}

function Line({
  children,
  strong,
  numeric
}: {
  children: ReactNode;
  strong?: boolean;
  numeric?: boolean;
}) {
  return (
    <p
      className={
        strong
          ? 'text-body-strong text-primary'
          : `text-body text-secondary${numeric ? ' tabular-nums' : ''}`
      }
    >
      {children}
    </p>
  );
}
