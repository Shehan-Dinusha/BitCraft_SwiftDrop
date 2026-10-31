import { useState } from 'react';
import { Printer, Share2 } from 'lucide-react';
import {
  Button,
  FilterBar,
  FsmStatusBadge,
  OrderTable,
  OrderTableRow,
  PageHeader,
  Pagination,
  SpeedTag,
  Tabs,
  type OrderTableRowData,
  type SortDirection
} from '@/design-system';
import { CatalogRow } from '../components/catalog-kit';

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
    trackingNumber: 'SD-5C8R-1WZB',
    status: 'FAILED',
    from: 'Kandy',
    to: 'Colombo',
    speed: 'standard',
    receiverName: 'Ruwan Jayasinghe',
    receiverArea: '00100 · Colombo 01',
    fee: 790
  },
  {
    id: '4',
    trackingNumber: 'SD-9D3F-6MQK',
    status: 'DELIVERED',
    from: 'Gampaha',
    to: 'Colombo',
    speed: 'express',
    receiverName: 'Ishara Fernando',
    receiverArea: '00500 · Colombo 05',
    fee: 1250
  }
];

export function PageStructureSection() {
  const [tab, setTab] = useState('timeline');
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<string[]>(['3']);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<SortDirection>('none');

  const allSelected =
    selected.length === orders.length ? true : selected.length > 0 ? 'mixed' : false;

  return (
    <>
      <CatalogRow label="Page header" hint="Breadcrumb, title with badges, subtitle, actions">
        <PageHeader
          breadcrumb={[
            { label: 'Orders' },
            { label: 'Colombo district' },
            { label: 'SD-7K4M-9QX2' }
          ]}
          title="SD-7K4M-9QX2"
          badges={
            <>
              <FsmStatusBadge state="OUT_FOR_DELIVERY" />
              <SpeedTag speed="express" />
            </>
          }
          subtitle="Created 25 Oct 2026, 09:10 · Gampaha → Colombo · 2.4 kg"
          actions={
            <>
              <Button variant="outline" leftIcon={<Share2 />}>
                Share
              </Button>
              <Button variant="secondary" leftIcon={<Printer />}>
                Label PDF
              </Button>
              <Button>Track</Button>
            </>
          }
        />
      </CatalogRow>

      <CatalogRow label="Tabs" hint="Arrow keys, Home and End move between tabs">
        <Tabs
          aria-label="Order detail"
          value={tab}
          onChange={setTab}
          tabs={[
            { id: 'details', label: 'Details' },
            { id: 'timeline', label: 'Timeline', count: 6 },
            { id: 'cash', label: 'Cash' },
            { id: 'label', label: 'Label & receipt' },
            { id: 'notifications', label: 'Notifications', count: 4 }
          ]}
        />
      </CatalogRow>

      <CatalogRow
        label="Orders table"
        hint="Filter bar, header with sortable cells, selectable rows, pagination"
      >
        <div className="overflow-hidden rounded-xl border border-border-subtle bg-card">
          <FilterBar
            searchValue={search}
            onSearchChange={setSearch}
            filters={[
              { id: 'date', label: 'Date', value: 'Today', active: true },
              { id: 'district', label: 'District', value: 'Colombo', active: true },
              { id: 'speed', label: 'Speed', value: 'All' },
              { id: 'postal', label: 'Postal code', value: 'All' },
              { id: 'courier', label: 'Courier', value: 'All' }
            ]}
            onFilterClick={() => undefined}
            onClearAll={() => setSearch('')}
          />
          <OrderTable
            allSelected={allSelected}
            onToggleAll={(checked) => setSelected(checked ? orders.map((order) => order.id) : [])}
            columns={[
              {
                key: 'tracking',
                label: 'Tracking no.',
                sort,
                onSort: () =>
                  setSort((current) =>
                    current === 'none'
                      ? 'ascending'
                      : current === 'ascending'
                        ? 'descending'
                        : 'none'
                  )
              },
              { key: 'status', label: 'Status' },
              { key: 'route', label: 'Route' },
              { key: 'speed', label: 'Speed' },
              { key: 'receiver', label: 'Receiver' },
              { key: 'fee', label: 'Fee' }
            ]}
          >
            {orders.map((order) => (
              <OrderTableRow
                key={order.id}
                order={order}
                selected={selected.includes(order.id)}
                onSelectedChange={(checked) =>
                  setSelected((current) =>
                    checked ? [...current, order.id] : current.filter((id) => id !== order.id)
                  )
                }
              />
            ))}
          </OrderTable>
          <Pagination page={page} pageSize={20} total={248} onPageChange={setPage} />
        </div>
      </CatalogRow>
    </>
  );
}
