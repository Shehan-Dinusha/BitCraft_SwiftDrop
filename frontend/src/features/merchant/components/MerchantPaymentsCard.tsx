import { useEffect, useState } from 'react';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { HighDensityDataGrid } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { cashTime, lkr } from '@/features/cash/cashDisplay';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import {
  cashApi,
  type CashEntryResponse,
  type MerchantPaymentResponse
} from '@/services/api/cashApi';

const PAGE_SIZE = 20;

/** What the shop paid at each pickup visit, with a receipt. */
export function MerchantPaymentsCard({ refreshToken = 0 }: { refreshToken?: number }) {
  const [page, setPage] = useState(0);
  const [payments, setPayments] = useState<MerchantPaymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState<MerchantPaymentResponse | null>(null);

  useEffect(() => {
    let current = true;
    setLoading(true);
    cashApi
      .merchantPayments(page)
      .then((loaded) => {
        if (!current) return;
        setPayments(loaded);
        setError('');
      })
      .catch((err: unknown) => {
        if (current) setError(apiErrorMessage(err, 'Your payments could not be loaded.'));
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [page, refreshToken]);

  return (
    <SurfaceCard density="compact">
      <div className="space-y-3">
        <h2 className="text-h3 text-primary">Payments</h2>
        {error && (
          <StatusBanner tone="danger" title="Payments">
            {error}
          </StatusBanner>
        )}
        <HighDensityDataGrid
          density="compact"
          label="Payments"
          searchable={false}
          emptyMessage={loading ? 'Loading payments…' : 'No payments yet'}
          rows={payments.map((payment) => ({ ...payment, id: payment.paymentId }))}
          columns={[
            { key: 'paidAt', label: 'Paid', render: (r) => cashTime(r.paidAt) },
            { key: 'courierName', label: 'Courier', render: (r) => r.courierName ?? '—' },
            { key: 'parcels', label: 'Parcels', align: 'right' },
            { key: 'amount', label: 'Amount', align: 'right', render: (r) => lkr(r.amount) },
            {
              key: 'id',
              label: 'Action',
              render: (r) => (
                <Button variant="outline" onClick={() => setReceipt(r)}>
                  Receipt
                </Button>
              )
            }
          ]}
        />
        {(page > 0 || payments.length === PAGE_SIZE) && (
          <div className="flex items-center justify-end gap-3 text-sm">
            <Button
              variant="outline"
              disabled={loading || page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <span className="tabular-nums">Page {page + 1}</span>
            <Button
              variant="outline"
              disabled={loading || payments.length < PAGE_SIZE}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        )}
      </div>
      {receipt && (
        <Receipt key={receipt.paymentId} payment={receipt} onClose={() => setReceipt(null)} />
      )}
    </SurfaceCard>
  );
}

function Receipt({ payment, onClose }: { payment: MerchantPaymentResponse; onClose: () => void }) {
  const [entries, setEntries] = useState<CashEntryResponse[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let current = true;
    cashApi
      .merchantPaymentEntries(payment.paymentId)
      .then((loaded) => {
        if (current) setEntries(loaded);
      })
      .catch((err: unknown) => {
        if (current) setError(apiErrorMessage(err, 'The receipt could not be loaded.'));
      });
    return () => {
      current = false;
    };
  }, [payment.paymentId]);

  return (
    <Modal open onClose={onClose} title="Payment receipt">
      <div className="space-y-4">
        <dl className="grid grid-cols-2 gap-3 text-body text-primary">
          <div>
            <dt className="text-label text-secondary">Paid</dt>
            <dd>{cashTime(payment.paidAt)}</dd>
          </div>
          <div>
            <dt className="text-label text-secondary">Courier</dt>
            <dd>{payment.courierName ?? '—'}</dd>
          </div>
        </dl>
        {error ? (
          <p role="alert" className="text-body text-status-danger-fg">
            {error}
          </p>
        ) : entries ? (
          <table className="w-full text-sm tabular-nums text-primary">
            <caption className="sr-only">Parcels paid for</caption>
            <thead>
              <tr className="text-secondary">
                <th scope="col" className="py-2 text-left font-medium">
                  Tracking number
                </th>
                <th scope="col" className="py-2 text-right font-medium">
                  Delivery fee
                </th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.entryId} className="border-t border-border-subtle">
                  <td className="py-2 font-mono">{entry.trackingNumber}</td>
                  <td className="py-2 text-right">{lkr(entry.amount)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t border-border-subtle font-semibold">
                <th scope="row" className="py-2 text-left">
                  Total
                </th>
                <td className="py-2 text-right">{lkr(payment.amount)}</td>
              </tr>
            </tfoot>
          </table>
        ) : (
          <p role="status">Loading receipt…</p>
        )}
      </div>
    </Modal>
  );
}
