import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { merchantApi, type MerchantOrderReceiptResponse } from '@/services/api/merchantApi';
import { getTrackingView, type PublicOrderStatus } from '@/services/api/trackingHubApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { HighDensityDataGrid } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { Button } from '@/design-system/primitives/Button';

type OrderRow = MerchantOrderReceiptResponse & { id: string; status?: PublicOrderStatus };
export function MerchantOrdersTable({
  merchantId,
  refreshToken = 0
}: {
  merchantId: string;
  refreshToken?: number;
}) {
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<OrderRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  useEffect(() => {
    setPage(0);
  }, [merchantId]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setRows([]);
    setHasNext(false);
    const load = async () => {
      try {
        const receipts = await merchantApi.listOrders(merchantId, page, 20);
        if (controller.signal.aborted) return;
        setHasNext(receipts.length === 20);
        setRows(receipts.map((r) => ({ ...r, id: r.orderId })));
        // Status failures remain explicit; an accepted submission is never shown as delivered.
        const tracked = await Promise.all(
          receipts.map(async (r) => {
            try {
              const tracking = await getTrackingView(r.trackingNumber, {
                signal: controller.signal
              });
              return { ...r, id: r.orderId, status: tracking.currentPublicStatus };
            } catch {
              return { ...r, id: r.orderId };
            }
          })
        );
        if (!controller.signal.aborted) setRows(tracked);
      } catch (err) {
        if (!controller.signal.aborted)
          setError(apiErrorMessage(err, 'Unable to load merchant orders.'));
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    };
    void load();
    return () => controller.abort();
  }, [merchantId, page, revision, refreshToken]);
  return (
    <SurfaceCard className="space-y-3 p-4" density="compact">
      <div className="flex items-center justify-between">
        <h2 className="text-h3 text-primary">Merchant orders</h2>
        <Button variant="outline" disabled={loading} onClick={() => setRevision((v) => v + 1)}>
          Refresh orders
        </Button>
      </div>
      {error && (
        <StatusBanner tone="danger" title="Orders unavailable">
          {error}
        </StatusBanner>
      )}
      {loading && (
        <p role="status" className="text-sm text-secondary">
          Loading orders and delivery status…
        </p>
      )}
      <HighDensityDataGrid
        density="compact"
        label="Merchant orders"
        filterPlaceholder="Search this page by reference or tracking number"
        emptyMessage={
          loading
            ? 'Loading orders…'
            : error
              ? 'Orders could not be loaded.'
              : 'No orders on this page.'
        }
        rows={rows}
        columns={[
          { key: 'externalRef', label: 'Reference' },
          {
            key: 'trackingNumber',
            label: 'Tracking number',
            render: (r) => (
              <Link
                className="font-mono text-brand-primary underline tabular-nums"
                to={`/track/${encodeURIComponent(r.trackingNumber)}`}
              >
                {r.trackingNumber}
              </Link>
            )
          },
          {
            key: 'status',
            label: 'Delivery status',
            render: (r) =>
              r.status ? (
                <FsmStatusBadge state={r.status} density="compact" />
              ) : (
                <span className="text-secondary">Status unavailable</span>
              )
          },
          {
            key: 'createdAt',
            label: 'Submitted',
            render: (r) => new Date(r.createdAt).toLocaleString()
          }
        ]}
      />
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
          disabled={loading || !hasNext}
          onClick={() => setPage((p) => p + 1)}
        >
          Next
        </Button>
      </div>
    </SurfaceCard>
  );
}
