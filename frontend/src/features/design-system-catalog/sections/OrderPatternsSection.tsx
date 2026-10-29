import { useState } from 'react';
import {
  BottomSheet,
  Button,
  NotificationTray,
  OrderCard,
  PhotoUpload,
  PickupSlotPicker,
  PriceBreakdown,
  Radio,
  type NotificationData
} from '@/design-system';
import { CatalogRow, DemoRow } from '../components/catalog-kit';

const lines = [
  { label: 'Base fee', amount: 250 },
  { label: 'Weight fee', note: 'Chargeable 2.4 kg', amount: 120 },
  { label: 'Distance fee', note: 'Gampaha → Colombo · Nearby band', amount: 80 }
];

const reasons = [
  'Receiver not available',
  'Wrong or incomplete address',
  'Receiver refused the parcel',
  'Area not reachable',
  'Other (add a note)'
];

const initialNotifications: NotificationData[] = [
  {
    id: '1',
    title: 'Your parcel is out for delivery',
    message: 'SD-7K4M-9QX2 · Arriving today in Colombo',
    time: '2 min ago',
    unread: true
  },
  {
    id: '2',
    title: 'Pickup time set',
    message: 'SD-Q2LC-8RTA · Courier arrives 14:00–16:00',
    time: '18 min ago',
    unread: true
  },
  {
    id: '3',
    title: 'Parcel delivered',
    message: 'SD-Z9KD-1BNE · Received by K. Perera',
    time: '1 h ago',
    status: 'DELIVERED'
  }
];

export function OrderPatternsSection() {
  const [day, setDay] = useState('today');
  const [slot, setSlot] = useState('s4');
  const [reason, setReason] = useState(reasons[0]);
  const [notifications, setNotifications] = useState(initialNotifications);

  return (
    <>
      <CatalogRow
        label="Price breakdown"
        hint="Customer, merchant (with discount), recalculated at pickup"
      >
        <DemoRow className="items-start gap-4">
          <PriceBreakdown
            type="customer"
            lines={lines}
            subtotal={450}
            surcharge={{ label: 'Express × 1.5', amount: 225 }}
            total={675}
            footnote="Pay in cash to the courier at pickup."
          />
          <PriceBreakdown
            type="merchant"
            lines={lines}
            subtotal={450}
            surcharge={{ label: 'Express × 1.5', amount: 225 }}
            discount={{
              label: 'Merchant discount (10%)',
              note: 'Set by System Admin',
              amount: 67.5
            }}
            total={607.5}
            footnote="Pay in cash to the courier at pickup."
          />
          <PriceBreakdown
            type="recalculated"
            measuredNote="Courier measured 3.1 kg and 35 × 25 × 20 cm, so the chargeable weight is 3.5 kg."
            lines={[
              { label: 'Base fee', amount: 250 },
              { label: 'Weight fee', note: 'Chargeable 3.5 kg (volumetric)', amount: 160 },
              { label: 'Distance fee', note: 'Gampaha → Colombo · Nearby band', amount: 80 }
            ]}
            subtotal={490}
            surcharge={{ label: 'Express × 1.5', amount: 245 }}
            total={735}
            previousTotal={675}
            onViewEvidence={() => undefined}
            footnote="Pay the updated fee in cash to the courier now."
          />
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Order cards" hint="Customer list, courier pickup, courier delivery">
        <DemoRow className="items-start gap-4">
          <OrderCard
            type="customer"
            trackingNumber="SD-7K4M-9QX2"
            status="IN_TRANSIT"
            from="Gampaha"
            to="Colombo 03"
            meta="To Kasun Perera · Created 25 Oct · Expected 27 Oct"
            speed="express"
            fee={675}
            onOpen={() => undefined}
          />
          <OrderCard
            type="courier-pickup"
            name="Lanka Crafts (Pvt) Ltd"
            address="12 Kandy Road, Kadawatha"
            postalCode="11850"
            area="Kadawatha · 3.2 km"
            trackingNumber="SD-Q2LC-8RTA"
            speed="standard"
            timeLabel="14:00–16:00"
            cash={{ state: 'pending', amount: 450 }}
          />
          <OrderCard
            type="courier-delivery"
            name="Kasun Perera"
            address="No. 42, Galle Road, Kollupitiya"
            postalCode="00300"
            area="Colombo 03 · 1.4 km"
            trackingNumber="SD-7K4M-9QX2"
            speed="express"
            timeLabel="Attempt 2 of 3"
            weight="2.4 kg"
          />
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Bottom sheet" hint="Mark as failed; rendered inline here">
        <BottomSheet open inline onClose={() => undefined} title="Why could you not deliver?">
          <div role="radiogroup" aria-label="Failure reason" className="flex flex-col gap-1">
            {reasons.map((item) => (
              <div
                key={item}
                className={
                  item === reason ? 'rounded-[10px] bg-brand-subtle p-3' : 'rounded-[10px] p-3'
                }
              >
                <Radio
                  name="failure-reason"
                  label={item}
                  checked={item === reason}
                  onChange={() => setReason(item)}
                />
              </div>
            ))}
          </div>
          <p className="rounded-lg bg-fsm-in-transit-bg px-3 py-2.5 text-caption text-fsm-in-transit-fg">
            This is attempt 2 of 3. After the 3rd failed attempt the parcel returns to the sender.
          </p>
          <Button variant="destructive" density="kinetic">
            Mark as failed
          </Button>
          <Button variant="ghost" density="kinetic">
            Cancel
          </Button>
        </BottomSheet>
      </CatalogRow>

      <CatalogRow label="Photo upload" hint="Empty, captured, uploading, error">
        <DemoRow className="items-start gap-4">
          <PhotoUpload state="empty" />
          <PhotoUpload state="captured" />
          <PhotoUpload state="uploading" progress={64} />
          <PhotoUpload state="error" />
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Pickup slot picker" hint="Full slots are disabled">
        <PickupSlotPicker
          days={[
            { id: 'today', label: 'Today · 26 Oct' },
            { id: 'tue', label: 'Tue · 27 Oct' },
            { id: 'wed', label: 'Wed · 28 Oct' }
          ]}
          selectedDay={day}
          onDayChange={setDay}
          slots={[
            { id: 's1', time: '08:00–10:00', booked: 8, capacity: 8 },
            { id: 's2', time: '10:00–12:00', booked: 5, capacity: 8 },
            { id: 's3', time: '12:00–14:00', booked: 2, capacity: 8 },
            { id: 's4', time: '14:00–16:00', booked: 3, capacity: 8 },
            { id: 's5', time: '16:00–18:00', booked: 0, capacity: 8 }
          ]}
          selectedSlot={slot}
          onSlotChange={setSlot}
        />
      </CatalogRow>

      <CatalogRow label="Notifications" hint="Tray with unread and read items">
        <NotificationTray
          items={notifications}
          onMarkAllRead={() =>
            setNotifications((current) => current.map((item) => ({ ...item, unread: false })))
          }
          onViewAll={() => undefined}
        />
      </CatalogRow>
    </>
  );
}
