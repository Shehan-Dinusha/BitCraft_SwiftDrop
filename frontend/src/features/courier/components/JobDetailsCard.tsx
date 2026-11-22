import { Banknote, MapPin, Navigation, Package, Phone, StickyNote } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';
import { districtLabel } from '@/features/profile/profileFormat';
import type { CourierJobResponse, CourierJobStop } from '@/services/api/deliveryApi';

export interface JobDetailsCardProps {
  job: CourierJobResponse;
  /** Goes to another district, so this courier takes it to a hub or collects it from one. */
  viaHubs: boolean;
  pickupDistrict?: string | null;
  deliveryDistrict?: string | null;
}

/** Where the courier goes, whom they call and what cash they collect, for the leg they carry. */
export function JobDetailsCard({
  job,
  viaHubs,
  pickupDistrict,
  deliveryDistrict
}: JobDetailsCardProps) {
  // On a cross-district parcel the service gives each courier one person; the other end is a hub.
  const pickupHub = viaHubs && !job.pickup && Boolean(job.dropOff);
  const dropOffHub = viaHubs && !job.dropOff;
  const parcel = parcelSummary(job);

  return (
    <SurfaceCard>
      <div className="space-y-5">
        {job.feeToCollect != null && (
          <CashToCollect from="sender" amount={job.feeToCollect} currency={job.currency} />
        )}
        {job.codToCollect != null && (
          <CashToCollect from="recipient" amount={job.codToCollect} currency={job.currency} />
        )}

        {job.pickup && <Stop icon={MapPin} title="Pick up from" stop={job.pickup} />}
        {pickupHub && (
          <Hub icon={MapPin} title="Collect from" name={`${districtLabel(deliveryDistrict)} hub`} />
        )}
        {job.dropOff && <Stop icon={Navigation} title="Deliver to" stop={job.dropOff} />}
        {dropOffHub && (
          <Hub icon={Navigation} title="Hand in at" name={`${districtLabel(pickupDistrict)} hub`} />
        )}

        {job.notes && (
          <Row icon={StickyNote} title="Note from the sender">
            <p className="break-words text-body text-primary">{job.notes}</p>
          </Row>
        )}
        {parcel && (
          <Row icon={Package} title="Parcel">
            <p className="break-words text-body tabular-nums text-primary">{parcel}</p>
          </Row>
        )}
      </div>
    </SurfaceCard>
  );
}

function CashToCollect({
  from,
  amount,
  currency
}: {
  from: 'sender' | 'recipient';
  amount: number;
  currency?: string;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-status-warning-border bg-status-warning-bg p-4 text-status-warning-fg">
      <span className="flex items-center gap-2 text-body-strong">
        <Banknote aria-hidden="true" className="size-5 shrink-0" />
        Collect cash from the {from}
      </span>
      <MoneyAmount amount={amount} currency={currency} size="lg" inheritColor />
    </div>
  );
}

function Stop({ icon, title, stop }: { icon: LucideIcon; title: string; stop: CourierJobStop }) {
  const phones = [stop.phone, stop.alternatePhone].filter((phone): phone is string =>
    Boolean(phone)
  );
  return (
    <Row icon={icon} title={title}>
      <p className="break-words text-body-strong text-primary">{stop.name}</p>
      <address className="break-words text-body not-italic text-primary">
        {addressLines(stop).map((line) => (
          <span key={line} className="block">
            {line}
          </span>
        ))}
      </address>
      {phones.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-2">
          {phones.map((phone) => (
            <a
              key={phone}
              href={`tel:${phone}`}
              aria-label={`Call ${stop.name ?? ''} on ${phone}`}
              className="inline-flex min-h-target items-center gap-2 rounded-lg border border-border-subtle bg-card px-4 text-body-strong tabular-nums text-brand-primary hover:bg-canvas"
            >
              <Phone aria-hidden="true" className="size-4 shrink-0" />
              {phone}
            </a>
          ))}
        </div>
      )}
    </Row>
  );
}

function Hub({ icon, title, name }: { icon: LucideIcon; title: string; name: string }) {
  return (
    <Row icon={icon} title={title}>
      <p className="text-body-strong text-primary">{name}</p>
    </Row>
  );
}

function Row({
  icon: Icon,
  title,
  children
}: {
  icon: LucideIcon;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="flex items-start gap-3">
      <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-brand-subtle text-brand-primary">
        <Icon aria-hidden="true" className="size-4" />
      </span>
      <div className="min-w-0 flex-1">
        <h3 className="text-label text-secondary">{title}</h3>
        {children}
      </div>
    </section>
  );
}

function addressLines(stop: CourierJobStop): string[] {
  const city = [stop.city, stop.postalCode].filter(Boolean).join(' ');
  const district = stop.district ? `${districtLabel(stop.district)} district` : '';
  return [stop.addressLine1, stop.addressLine2, city, district].filter((line): line is string =>
    Boolean(line)
  );
}

function parcelSummary(job: CourierJobResponse): string {
  const size =
    job.lengthCm != null && job.widthCm != null && job.heightCm != null
      ? `${job.lengthCm} × ${job.widthCm} × ${job.heightCm} cm`
      : null;
  const weight = job.weightKg != null ? `${job.weightKg} kg` : null;
  return [job.itemDescription, weight, size].filter(Boolean).join(' · ');
}
