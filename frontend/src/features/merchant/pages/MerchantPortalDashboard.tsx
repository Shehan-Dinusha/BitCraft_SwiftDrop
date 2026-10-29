import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { BulkCsvDropzone } from '../components/BulkCsvDropzone';
import { ApiKeyManagerCard } from '../components/ApiKeyManagerCard';
import {
  merchantApi,
  type MerchantAccountResponse,
  type BulkIngestBatchResponse,
  type BulkBatchItemResponse
} from '@/services/api/merchantApi';
import {
  Store,
  Layers,
  PackageCheck,
  CheckCircle2,
  RefreshCw,
  MapPin,
  Eye,
  FileSpreadsheet,
  Webhook
} from 'lucide-react';

export function MerchantPortalDashboard() {
  const user = useAuthStore((s) => s.user);
  const merchantId = user?.id || '22222222-2222-2222-2222-222222222222';

  const [profile, setProfile] = useState<MerchantAccountResponse | null>(null);
  const [batches, setBatches] = useState<BulkIngestBatchResponse[]>([]);
  const [selectedBatch, setSelectedBatch] = useState<BulkIngestBatchResponse | null>(null);
  const [batchItems, setBatchItems] = useState<BulkBatchItemResponse[]>([]);
  const [isItemsModalOpen, setIsItemsModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const fetchMerchantData = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const [acc, batchList] = await Promise.all([
        merchantApi.getMerchant(merchantId).catch(() => null),
        merchantApi.listBatches(merchantId).catch(() => [])
      ]);
      setProfile(acc);
      setBatches(batchList || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch merchant data';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  }, [merchantId]);

  useEffect(() => {
    fetchMerchantData();
  }, [fetchMerchantData]);

  const handleViewBatchItems = async (batch: BulkIngestBatchResponse) => {
    if (!batch.batchId) return;
    try {
      setSelectedBatch(batch);
      setIsItemsModalOpen(true);
      setItemsLoading(true);
      const items = await merchantApi.getBatchItems(batch.batchId);
      setBatchItems(items || []);
    } catch (err) {
      console.error('Failed to load batch items', err);
    } finally {
      setItemsLoading(false);
    }
  };

  const totalOrdersDispatched = batches.reduce((acc, b) => acc + (b.successCount || 0), 0);
  const totalOrdersSubmitted = batches.reduce((acc, b) => acc + (b.totalRecords || 0), 0);
  const overallSuccessRate =
    totalOrdersSubmitted > 0
      ? Math.round((totalOrdersDispatched / totalOrdersSubmitted) * 100)
      : 100;

  return (
    <main className="space-y-4 p-4 md:p-6" data-density="compact">
      {/* Top Header Bar */}
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <div className="flex items-center gap-2">
            <Store className="h-5 w-5 text-indigo-500" />
            <h1 className="text-xl font-bold tracking-tight text-slate-900">
              {profile?.businessName || user?.username || 'Merchant Partner Portal'}
            </h1>
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-800">
              {profile?.status || 'ACTIVE'}
            </span>
          </div>
          <p className="flex items-center gap-2 text-xs text-slate-500">
            <span>Operating District: <strong>{profile?.operatingDistrict || 'COLOMBO'}</strong></span>
            <span>?</span>
            <span>{profile?.contactEmail || 'merchants@swiftdrop.lk'}</span>
          </p>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <Link to="/merchant/webhooks">
            <Button variant="outline" className="h-8 text-xs">
              <Webhook className="mr-1.5 h-3.5 w-3.5 text-brand-primary" />
              Webhook Console
            </Button>
          </Link>

          <Button
            variant="outline"
            className="h-8 text-xs"
            onClick={fetchMerchantData}
            disabled={loading}
          >
            <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </header>

      {errorMsg && (
        <StatusBanner tone="danger" title="Error" onDismiss={() => setErrorMsg(null)}>
          {errorMsg}
        </StatusBanner>
      )}

      {/* KPI Stats Strip */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Total Batches</span>
            <Layers className="h-4 w-4 text-indigo-500" />
          </div>
          <div className="mt-1 text-lg font-bold text-slate-900">
            {batches.length}
          </div>
          <p className="text-[11px] text-slate-400">Automated CSV / JSON files</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Dispatched Orders</span>
            <PackageCheck className="h-4 w-4 text-emerald-500" />
          </div>
          <div className="mt-1 text-lg font-bold text-slate-900">
            {totalOrdersDispatched}
          </div>
          <p className="text-[11px] text-slate-400">Injected to dispatch engine</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Success Rate</span>
            <CheckCircle2 className="h-4 w-4 text-cyan-500" />
          </div>
          <div className="mt-1 text-lg font-bold text-slate-900">
            {overallSuccessRate}%
          </div>
          <p className="text-[11px] text-slate-400">Valid address & schema</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Warehouse Hub</span>
            <MapPin className="h-4 w-4 text-amber-500" />
          </div>
          <div className="mt-1 truncate text-xs font-semibold text-slate-900">
            {profile?.pickupAddress || 'Colombo Central Hub'}
          </div>
          <p className="text-[11px] text-slate-400">Default dispatch pickup</p>
        </SurfaceCard>
      </div>

      {/* Two Column Ingestion & Key Management Strip */}
      <div className="grid gap-4 lg:grid-cols-2">
        <BulkCsvDropzone merchantId={merchantId} onBatchIngested={fetchMerchantData} />
        <ApiKeyManagerCard merchantId={merchantId} />
      </div>

      {/* Ingestion Batches Table */}
      <SurfaceCard className="space-y-3 p-4" data-density="compact">
        <div className="flex items-center justify-between border-b border-border-subtle pb-2">
          <div className="flex items-center gap-2">
            <FileSpreadsheet className="h-4 w-4 text-indigo-500" />
            <h3 className="text-sm font-bold text-slate-900">
              Recent Ingestion Batches ({batches.length})
            </h3>
          </div>
          <span className="text-xs text-slate-500">Automated Delivery Generation</span>
        </div>

        <div className="overflow-x-auto text-xs">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-border-subtle text-slate-500">
                <th className="pb-2 font-semibold">File / Batch</th>
                <th className="pb-2 font-semibold">Status</th>
                <th className="pb-2 font-semibold">Records</th>
                <th className="pb-2 font-semibold">Dispatched</th>
                <th className="pb-2 font-semibold">Failed</th>
                <th className="pb-2 font-semibold">Date</th>
                <th className="pb-2 text-right font-semibold">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-subtle">
              {batches.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-6 text-center text-slate-500">
                    No ingestion batches found. Upload your first CSV order batch above.
                  </td>
                </tr>
              ) : (
                batches.map((b) => (
                  <tr key={b.batchId} className="hover:bg-slate-50/50">
                    <td className="py-2.5 font-medium text-slate-900">
                      {b.fileName}
                    </td>
                    <td className="py-2.5">
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                          b.batchStatus === 'COMPLETED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : b.batchStatus === 'PROCESSING'
                              ? 'bg-cyan-100 text-cyan-800'
                              : 'bg-rose-100 text-rose-800'
                        }`}
                      >
                        {b.batchStatus}
                      </span>
                    </td>
                    <td className="py-2.5 font-mono text-slate-700">
                      {b.totalRecords}
                    </td>
                    <td className="py-2.5 font-mono font-semibold text-emerald-600">
                      {b.successCount}
                    </td>
                    <td className="py-2.5 font-mono text-rose-600">
                      {b.failedCount}
                    </td>
                    <td className="py-2.5 text-slate-500">
                      {b.createdAt ? new Date(b.createdAt).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' }) : 'N/A'}
                    </td>
                    <td className="py-2.5 text-right">
                      <button
                        type="button"
                        onClick={() => handleViewBatchItems(b)}
                        className="inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-1 text-[11px] font-medium text-slate-700 hover:bg-slate-200"
                      >
                        <Eye className="h-3 w-3" />
                        Parcels
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </SurfaceCard>

      {/* Batch Items Detail Modal */}
      <Modal
        open={isItemsModalOpen}
        onClose={() => setIsItemsModalOpen(false)}
        title={selectedBatch ? `Batch Items: ${selectedBatch.fileName}` : 'Batch Items'}
        description={`Showing generated tracking numbers and order dispatches for batch #${selectedBatch?.batchId?.substring(0, 8)}`}
        size="lg"
      >
        <div className="space-y-3 pt-2 text-xs">
          {itemsLoading ? (
            <div className="py-8 text-center text-slate-500">Loading batch parcels...</div>
          ) : batchItems.length === 0 ? (
            <div className="py-8 text-center text-slate-500">No items found for this batch.</div>
          ) : (
            <div className="max-h-96 overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="border-b border-border-subtle text-slate-500">
                    <th className="pb-2 font-semibold">#</th>
                    <th className="pb-2 font-semibold">Tracking #</th>
                    <th className="pb-2 font-semibold">Recipient</th>
                    <th className="pb-2 font-semibold">District</th>
                    <th className="pb-2 font-semibold">Speed</th>
                    <th className="pb-2 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-subtle">
                  {batchItems.map((item) => (
                    <tr key={item.rowIndex} className="hover:bg-slate-50">
                      <td className="py-2 text-slate-400">{item.rowIndex}</td>
                      <td className="py-2 font-mono font-bold text-indigo-600">
                        {item.trackingNumber || 'PENDING'}
                      </td>
                      <td className="py-2">
                        <div className="font-medium text-slate-900">
                          {item.recipientName}
                        </div>
                        <div className="text-[11px] text-slate-400">{item.recipientPhone}</div>
                      </td>
                      <td className="py-2 font-medium text-slate-700">
                        {item.deliveryDistrict}
                      </td>
                      <td className="py-2 font-semibold text-slate-600">
                        {item.deliverySpeed}
                      </td>
                      <td className="py-2">
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                            item.itemStatus === 'SUCCESS'
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-rose-100 text-rose-800'
                          }`}
                        >
                          {item.itemStatus}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex justify-end pt-2">
            <Button
              variant="outline"
              className="h-8 text-xs"
              onClick={() => setIsItemsModalOpen(false)}
            >
              Close
            </Button>
          </div>
        </div>
      </Modal>
    </main>
  );
}
