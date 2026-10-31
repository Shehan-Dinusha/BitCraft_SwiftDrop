import { useState } from 'react';
import {
  CashState,
  MoneyAmount,
  QueueChip,
  RoleTag,
  SpeedTag,
  TrackingNumber,
  type CashStateKind,
  type RoleTagRole
} from '@/design-system';
import { CatalogRow, DemoRow } from '../components/catalog-kit';

const roles: RoleTagRole[] = ['customer', 'merchant', 'courier', 'dispatcher', 'system-admin'];
const cashStates: CashStateKind[] = ['pending', 'collected', 'handed-over', 'shortage', 'excess'];
const queues = [
  { label: 'Created', count: 12 },
  { label: 'Unassigned', count: 7 },
  { label: 'Pickup Assigned', count: 18 },
  { label: 'At Hub', count: 31 },
  { label: 'Outbound', count: 9 },
  { label: 'Incoming', count: 14 },
  { label: 'Out for Delivery', count: 42 },
  { label: 'Failed / Return', count: 3 },
  { label: 'Delivered', count: 126 }
];

export function IdentifiersSection() {
  const [queue, setQueue] = useState('Created');

  return (
    <>
      <CatalogRow label="Tracking number" hint="Mono, selectable, with a copy button">
        <DemoRow>
          <TrackingNumber value="SD-7K4M-9QX2" />
          <TrackingNumber value="SD-7K4M-9QX2" size="lg" />
          <TrackingNumber value="SD-7K4M-9QX2" copyable={false} />
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Money amount" hint="S, M and L; tabular numerals">
        <DemoRow className="gap-6">
          <MoneyAmount amount={1250} size="sm" />
          <MoneyAmount amount={1250} />
          <MoneyAmount amount={1250} size="lg" />
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Speed and role tags">
        <div className="space-y-3">
          <DemoRow>
            <SpeedTag speed="standard" />
            <SpeedTag speed="express" />
          </DemoRow>
          <DemoRow>
            {roles.map((role) => (
              <RoleTag key={role} role={role} />
            ))}
          </DemoRow>
          <DemoRow>
            {roles.map((role) => (
              <RoleTag key={role} role={role} variant="solid" />
            ))}
          </DemoRow>
        </div>
      </CatalogRow>

      <CatalogRow label="Queue chips" hint="Dispatcher filters with live counts">
        <DemoRow>
          {queues.map((item) => (
            <QueueChip
              key={item.label}
              label={item.label}
              count={item.count}
              selected={queue === item.label}
              onClick={() => setQueue(item.label)}
            />
          ))}
        </DemoRow>
      </CatalogRow>

      <CatalogRow label="Cash state" hint="Cash on delivery lifecycle">
        <DemoRow>
          {cashStates.map((state) => (
            <CashState key={state} state={state} amount={450} />
          ))}
        </DemoRow>
      </CatalogRow>
    </>
  );
}
