import { Navigate, useParams } from 'react-router-dom';
import {
  Alert,
  BottomNav,
  EmptyState,
  FilterBar,
  NavItem,
  OrderCard,
  OrderTable,
  OrderTableRow,
  PageHeader,
  Pagination,
  PriceBreakdown,
  PublicHeader,
  QueueChip,
  SideNav,
  SlideToConfirm,
  TopBar,
  roleShellSpec,
  useMediaQuery,
  type OrderTableRowData,
  type ShellRole
} from '@/design-system';

export type PreviewRole = ShellRole | 'public';
export const previewRoles: PreviewRole[] = [
  'public',
  'customer',
  'merchant',
  'courier',
  'dispatcher',
  'system-admin'
];

const orders: OrderTableRowData[] = [
  {
    id: '1',
    trackingNumber: 'SD-7K4M-9QX2',
    status: 'OUT_FOR_DELIVERY',
    from: 'Gampaha',
    to: 'Colombo',
    speed: 'express',
    receiverName: 'Kasun Perera',
    receiverArea: '00300 · Colombo 03',
    fee: 1250
  },
  {
    id: '2',
    trackingNumber: 'SD-2H9P-4LTA',
    status: 'IN_TRANSIT',
    from: 'Gampaha',
    to: 'Colombo',
    speed: 'standard',
    receiverName: 'Nadeesha Silva',
    receiverArea: '00700 · Colombo 07',
    fee: 450
  },
  {
    id: '3',
    trackingNumber: 'SD-9D3F-6MQK',
    status: 'DELIVERED',
    from: 'Kandy',
    to: 'Colombo',
    speed: 'standard',
    receiverName: 'Ishara Fernando',
    receiverArea: '00500 · Colombo 05',
    fee: 790
  }
];

const tableColumns = [
  { key: 'tracking', label: 'Tracking no.' },
  { key: 'status', label: 'Status' },
  { key: 'route', label: 'Route' },
  { key: 'speed', label: 'Speed' },
  { key: 'receiver', label: 'Receiver' },
  { key: 'fee', label: 'Fee' }
] as const;

function OrdersTable() {
  return (
    <div className="overflow-hidden rounded-xl border border-border-subtle bg-card">
      <OrderTable columns={[...tableColumns]}>
        {orders.map((order) => (
          <OrderTableRow key={order.id} order={order} />
        ))}
      </OrderTable>
      <Pagination page={1} pageSize={20} total={248} onPageChange={() => undefined} />
    </div>
  );
}

const pageTitles: Record<ShellRole, string> = {
  customer: 'Book a parcel',
  merchant: 'Dashboard',
  courier: 'Today',
  dispatcher: 'Queues',
  'system-admin': 'Overview'
};

const activeIds: Record<ShellRole, string> = {
  customer: 'book',
  merchant: 'dashboard',
  courier: 'today',
  dispatcher: 'queues',
  'system-admin': 'overview'
};

/** Sample page content so each shell looks like a real screen. */
function SampleContent({ role }: { role: ShellRole }) {
  if (role === 'customer') {
    return (
      <div className="space-y-6 p-4 sm:p-6">
        <h2 className="text-h2 text-primary">Send a parcel</h2>
        <div className="flex flex-wrap items-start gap-4">
          <OrderCard
            type="customer"
            trackingNumber="SD-7K4M-9QX2"
            status="IN_TRANSIT"
            from="Gampaha"
            to="Colombo 03"
            meta="To Kasun Perera · Created 25 Oct · Expected 27 Oct"
            speed="express"
            fee={675}
          />
          <PriceBreakdown
            lines={[
              { label: 'Base fee', amount: 250 },
              { label: 'Weight fee', note: 'Chargeable 2.4 kg', amount: 120 },
              { label: 'Distance fee', note: 'Gampaha → Colombo', amount: 80 }
            ]}
            subtotal={450}
            surcharge={{ label: 'Express × 1.5', amount: 225 }}
            total={675}
            footnote="Pay in cash to the courier at pickup."
          />
        </div>
      </div>
    );
  }

  if (role === 'courier') {
    return (
      <div className="space-y-4 p-4">
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
        <SlideToConfirm label="Slide to confirm pickup" onConfirm={() => undefined} />
      </div>
    );
  }

  if (role === 'merchant') {
    return (
      <div className="space-y-4 p-4 sm:p-6">
        <PageHeader title="Orders" subtitle="248 orders this month" className="rounded-xl border" />
        <Alert tone="warning" title="2 webhook deliveries are retrying">
          Check the webhook logs if this keeps happening.
        </Alert>
        <OrdersTable />
      </div>
    );
  }

  if (role === 'dispatcher') {
    return (
      <div className="space-y-4 p-4 sm:p-6">
        <div className="flex flex-wrap gap-2">
          <QueueChip label="Created" count={12} selected />
          <QueueChip label="Unassigned" count={7} />
          <QueueChip label="Pickup Assigned" count={18} />
          <QueueChip label="Out for Delivery" count={42} />
        </div>
        <div className="overflow-hidden rounded-xl border border-border-subtle bg-card">
          <FilterBar
            searchValue=""
            onSearchChange={() => undefined}
            filters={[
              { id: 'district', label: 'District', value: 'Colombo', active: true },
              { id: 'courier', label: 'Courier', value: 'All' }
            ]}
            onFilterClick={() => undefined}
          />
          <OrderTable columns={[...tableColumns]}>
            {orders.map((order) => (
              <OrderTableRow key={order.id} order={order} />
            ))}
          </OrderTable>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4 p-4 sm:p-6">
      <div className="grid gap-4 sm:grid-cols-3">
        {[
          ['Open orders', '1,248'],
          ['Merchants awaiting approval', '4'],
          ['Delivered today', '362']
        ].map(([label, value]) => (
          <div key={label} className="rounded-xl border border-border-subtle bg-card p-5">
            <p className="text-caption text-secondary">{label}</p>
            <p className="font-mono text-h1 tabular-nums text-primary">{value}</p>
          </div>
        ))}
      </div>
      <EmptyState type="empty-queue" title="No pricing changes pending" />
    </div>
  );
}

function PublicPreview() {
  return (
    <div className="light min-h-screen bg-canvas text-primary">
      <PublicHeader onTrack={() => undefined} onSignIn={() => undefined} />
      <main className="mx-auto max-w-4xl space-y-6 px-6 py-16 text-center">
        <h1 className="text-display text-primary">Send it across Sri Lanka</h1>
        <p className="text-body-l text-secondary">
          Island-wide delivery. Pay cash to the courier at pickup.
        </p>
        <div className="grid gap-4 pt-4 text-left sm:grid-cols-3">
          {['Book in two minutes', 'Track every step', 'Pay on pickup'].map((title) => (
            <div key={title} className="rounded-xl border border-border-subtle bg-card p-5">
              <p className="text-h3 text-primary">{title}</p>
              <p className="pt-1 text-body text-secondary">
                A clear, simple step with no surprises.
              </p>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}

/**
 * `/catalog/preview/:role`: a full-page role shell with sample content and no login.
 * The catalog embeds it in a frame so its media queries react to the frame width.
 */
export function ShellPreviewPage() {
  const { role } = useParams();
  const isWide = useMediaQuery('(min-width: 1280px)');

  if (role === 'public') return <PublicPreview />;
  if (!role || !(role in roleShellSpec)) return <Navigate to="/catalog/shells" replace />;

  const shellRole = role as ShellRole;
  const spec = roleShellSpec[shellRole];
  const hasTabs = shellRole === 'customer' || shellRole === 'courier';
  const activeId = activeIds[shellRole];

  return (
    <div className="light flex min-h-screen bg-canvas text-primary">
      <div className={hasTabs ? 'hidden md:block' : undefined}>
        <SideNav
          role={shellRole}
          activeId={activeId}
          rail={!isWide}
          cta={spec.cta ? { label: spec.cta.label, onClick: () => undefined } : undefined}
          account={{ name: 'Nimali Perera', email: 'nimali.p@gmail.com' }}
          onSignOut={() => undefined}
          renderItem={(entry, active, rail) => (
            <NavItem
              href="#"
              onClick={(event: { preventDefault: () => void }) => event.preventDefault()}
              icon={entry.icon}
              label={entry.label}
              count={entry.count}
              active={active}
              rail={rail}
            />
          )}
        />
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar
          role={shellRole}
          section="Workspace"
          page={pageTitles[shellRole]}
          live={shellRole === 'courier' || shellRole === 'dispatcher' ? 'live' : undefined}
          environment={shellRole === 'system-admin' ? 'Production' : undefined}
          district={shellRole === 'dispatcher' ? 'Colombo' : undefined}
          hasUnread
        />
        <main className={hasTabs ? 'flex-1 pb-[68px] md:pb-0' : 'flex-1'}>
          <SampleContent role={shellRole} />
        </main>
      </div>

      {hasTabs ? (
        <BottomNav
          role={shellRole}
          activeId={roleShellSpec[shellRole].tabs?.[0]?.id}
          renderTab={(_entry, active, content, tabClassName) => (
            <a
              href="#"
              onClick={(event) => event.preventDefault()}
              aria-current={active ? 'page' : undefined}
              className={tabClassName}
            >
              {content}
            </a>
          )}
        />
      ) : null}
    </div>
  );
}

export default ShellPreviewPage;
