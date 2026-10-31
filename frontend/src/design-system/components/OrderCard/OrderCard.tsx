import { ChevronRight, Clock, Navigation, Phone } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/utils/cn';
import type { PublicOrderStatus } from '@/types/contracts';
import { CashState, type CashStateProps } from '@/design-system/components/CashState/CashState';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';
import { SpeedTag, type Speed } from '@/design-system/components/SpeedTag/SpeedTag';
import { TrackingNumber } from '@/design-system/components/TrackingNumber/TrackingNumber';

export interface CustomerOrderCardProps {
  type: 'customer';
  trackingNumber: string;
  status: PublicOrderStatus;
  from: string;
  to: string;
  /** e.g. `To Kasun Perera · Created 25 Oct · Expected 27 Oct`. */
  meta?: string;
  speed: Speed;
  fee: number;
  /** Makes the whole card a button. */
  onOpen?: () => void;
  className?: string;
}

export interface CourierOrderCardProps {
  type: 'courier-pickup' | 'courier-delivery';
  /** Merchant (pickup) or receiver (delivery). */
  name: string;
  address: string;
  postalCode: string;
  /** e.g. `Kadawatha · 3.2 km`. */
  area: string;
  trackingNumber: string;
  speed: Speed;
  /** Pickup window (`14:00–16:00`) or delivery attempt (`Attempt 2 of 3`). */
  timeLabel: string;
  /** Pickup only: cash to collect. */
  cash?: Pick<CashStateProps, 'state' | 'amount'>;
  /** Delivery only: parcel weight, e.g. `2.4 kg`. */
  weight?: string;
  onNavigate?: () => void;
  onCall?: () => void;
  /** Start pickup / Deliver. */
  onPrimary?: () => void;
  primaryLabel?: string;
  className?: string;
}

export type OrderCardProps = CustomerOrderCardProps | CourierOrderCardProps;

function CustomerCard(props: CustomerOrderCardProps) {
  const body = (
    <>
      <div className="flex items-center justify-between gap-2">
        <TrackingNumber value={props.trackingNumber} copyable={false} />
        <FsmStatusBadge state={props.status} size="md" />
      </div>
      <div className="flex items-center gap-1.5 text-body-strong text-primary">
        {props.from}
        <ChevronRight aria-hidden="true" className="size-3.5 text-muted" />
        {props.to}
      </div>
      {props.meta ? <p className="text-caption text-secondary">{props.meta}</p> : null}
      <div className="flex items-center justify-between gap-2">
        <SpeedTag speed={props.speed} />
        <span className="flex items-center gap-2">
          <MoneyAmount amount={props.fee} />
          {props.onOpen ? (
            <ChevronRight aria-hidden="true" className="size-[18px] text-muted" />
          ) : null}
        </span>
      </div>
    </>
  );

  const classes = cn(
    'flex w-full max-w-[360px] flex-col gap-2.5 rounded-xl border border-border-subtle bg-card px-4 py-3.5 text-left',
    props.className
  );

  return props.onOpen ? (
    <button
      type="button"
      onClick={props.onOpen}
      className={cn(
        classes,
        'transition-colors hover:bg-canvas focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus'
      )}
    >
      {body}
    </button>
  ) : (
    <article className={classes}>{body}</article>
  );
}

function ActionButton({
  icon,
  children,
  onClick
}: {
  icon: ReactNode;
  children: ReactNode;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex h-11 items-center gap-1.5 rounded-[10px] border border-border-subtle bg-card px-3.5 text-label text-primary transition-colors hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus [&_svg]:size-[18px]"
    >
      {icon}
      {children}
    </button>
  );
}

function CourierCard(props: CourierOrderCardProps) {
  const isPickup = props.type === 'courier-pickup';
  const primaryLabel = props.primaryLabel ?? (isPickup ? 'Start pickup' : 'Deliver');

  return (
    <article
      data-type={props.type}
      className={cn(
        'flex w-full max-w-[360px] flex-col gap-3 rounded-2xl border border-border-subtle bg-card p-4',
        props.className
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-2">
          <span
            className={cn(
              'rounded-md px-2 py-[3px] text-[11px] font-semibold uppercase leading-4 tracking-[0.04em]',
              isPickup
                ? 'bg-fsm-created-bg text-fsm-created-fg'
                : 'bg-fsm-out-for-delivery-bg text-fsm-out-for-delivery-fg'
            )}
          >
            {isPickup ? 'Pickup' : 'Delivery'}
          </span>
          <span className="flex items-center gap-1 text-label text-secondary">
            <Clock aria-hidden="true" className="size-3.5" />
            {props.timeLabel}
          </span>
        </span>
        <SpeedTag speed={props.speed} />
      </div>

      <div className="flex flex-col gap-0.5">
        <h3 className="text-h3 text-primary">{props.name}</h3>
        <p className="text-body text-primary">{props.address}</p>
        <p className="flex items-center gap-1.5">
          <span className="text-label text-primary">{props.postalCode}</span>
          <span className="text-caption text-secondary">{props.area}</span>
        </p>
      </div>

      <div className="flex flex-col gap-2 rounded-[10px] bg-canvas px-3 py-2.5">
        <div className="flex items-center justify-between gap-2">
          <TrackingNumber value={props.trackingNumber} copyable={false} />
          {!isPickup && props.weight ? (
            <span className="text-body text-secondary">{props.weight}</span>
          ) : null}
        </div>
        {isPickup && props.cash ? (
          <CashState state={props.cash.state} amount={props.cash.amount} className="self-start" />
        ) : null}
      </div>

      <div className="flex items-center gap-2">
        <ActionButton icon={<Navigation aria-hidden="true" />} onClick={props.onNavigate}>
          Navigate
        </ActionButton>
        <ActionButton icon={<Phone aria-hidden="true" />} onClick={props.onCall}>
          Call
        </ActionButton>
        <button
          type="button"
          onClick={props.onPrimary}
          className="inline-flex h-11 flex-1 items-center justify-center rounded-[10px] bg-brand-primary px-3.5 text-label text-brand-on transition-colors hover:bg-brand-hover active:bg-brand-pressed focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
        >
          {primaryLabel}
        </button>
      </div>
    </article>
  );
}

/** Figma OrderCard: Type = Customer / Courier pickup / Courier delivery. */
export function OrderCard(props: OrderCardProps) {
  return props.type === 'customer' ? <CustomerCard {...props} /> : <CourierCard {...props} />;
}

export default OrderCard;
