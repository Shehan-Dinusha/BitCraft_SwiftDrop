import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { BulkCsvDropzone } from '../components/BulkCsvDropzone';
import { ApiKeyManagerCard } from '../components/ApiKeyManagerCard';
import { MerchantOrdersTable } from '../components/MerchantOrdersTable';
import { MerchantApplicationStatus } from '../components/MerchantApplicationStatus';
import {
  merchantApi,
  type MerchantApprovalResponse,
  type MerchantAccountResponse,
  type BulkIngestBatchResponse,
  type BulkBatchItemResponse
} from '@/services/api/merchantApi';
import { fetchCurrentUser, refreshTokens } from '@/services/api/authApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { districtLabel } from '@/features/profile/profileFormat';

const sentenceCase = (value: string) => value.charAt(0) + value.slice(1).toLowerCase();
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

/** True when the access token already carries the merchant company (it is added once approved). */
function tokenHasMerchant(token: string | null): boolean {
  try {
    const payload = token?.split('.')[1];
    if (!payload) return false;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { merchantId?: string };
    return Boolean(json.merchantId);
  } catch {
    return false;
  }
}

export function MerchantPortalDashboard() {
  const user = useAuthStore((s) => s.user);
  const setAuth = useAuthStore((s) => s.setAuth);
  // undefined while loading; null before the merchant has applied.
  const [application, setApplication] = useState<MerchantApprovalResponse | null | undefined>(undefined);
  // The merchant identity is a separate value from the user id. It comes from the server-side profile
  // (resolved from the verified token); there is no fallback. undefined = still loading, null = not linked.
  const [merchantId, setMerchantId] = useState<string | null | undefined>(undefined);

  const [profile, setProfile] = useState<MerchantAccountResponse | null>(null);
  const [batches, setBatches] = useState<BulkIngestBatchResponse[]>([]);
  const [selectedBatch, setSelectedBatch] = useState<BulkIngestBatchResponse | null>(null);
  const [batchItems, setBatchItems] = useState<BulkBatchItemResponse[]>([]);
  const [isItemsModalOpen, setIsItemsModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [ordersRevision, setOrdersRevision] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([fetchCurrentUser(), merchantApi.myApplication()])
      .then(async ([me, app]) => {
        if (cancelled) return;
        // Approval links the account to the merchant company, but a token issued before that does
        // not carry it yet, and every merchant API checks it. Rotate the session once to pick it up.
        if (app?.status === 'APPROVED' && me.merchantId && !tokenHasMerchant(useAuthStore.getState().token)) {
          setAuth(await refreshTokens());
        }
        if (cancelled) return;
        setApplication(app);
        setMerchantId(me.merchantId ?? null);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setApplication(null);
        setMerchantId(null);
        setErrorMsg(apiErrorMessage(err, 'Failed to load your merchant profile'));
      });
    return () => {
      cancelled = true;
    };
  }, [setAuth]);

  const fetchMerchantData = useCallback(async () => {
    if (!merchantId) {
      setLoading(merchantId === undefined);
      return;
    }
    try {
      setLoading(true);
      setErrorMsg(null);
      const [acc, batchList] = await Promise.all([
        merchantApi.getMerchant(merchantId),
        merchantApi.listBatches(merchantId)
      ]);
      setProfile(acc);
      setBatches(batchList || []);
      setOrdersRevision((v) => v + 1);
    } catch (err: unknown) {
      const msg = apiErrorMessage(err, 'Failed to fetch merchant data');
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
      setBatchItems([]);
      const items = await merchantApi.getBatchItems(batch.batchId);
      setBatchItems(items || []);
    } catch (err) {
      setErrorMsg(apiErrorMessage(err, 'Failed to load batch items'));
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

  if (application === undefined) {
    return (
      <main className="p-4 md:p-6" data-density="compact">
        <p role="status" className="py-12 text-center text-sm text-secondary">
          Loading your merchant portal…
        </p>
      </main>
    );
  }
  if (!application || application.status !== 'APPROVED') {
    return <MerchantApplicationStatus application={application} />;
  }

  // Only real values: a missing district or email is left out, never replaced by a sample.
  const headerDetails = [
    profile?.operatingDistrict ? districtLabel(profile.operatingDistrict) : null,
    profile?.contactEmail || null
  ].filter((part): part is string => !!part);

  return (
    <main className="space-y-4 p-4 md:p-6" data-density="compact">
      {/* Top Header Bar */}
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <div className="flex items-center gap-2">
            <Store className="h-5 w-5 text-brand-primary" />
            <h1 className="text-xl font-bold tracking-tight text-primary">
              {profile?.businessName || user?.username || 'Your business'}
            </h1>
            {profile?.status ? (
              <span
                className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                  profile.status === 'APPROVED'
                    ? 'bg-status-success-bg text-status-success-fg'
                    : 'bg-status-warning-bg text-status-warning-fg'
                }`}
              >
                {sentenceCase(profile.status)}
              </span>
            ) : null}
          </div>
          {headerDetails.length > 0 ? (
            <p className="text-xs text-secondary">{headerDetails.join(' · ')}</p>
          ) : null}
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <Link to="/merchant/webhooks">
            <Button variant="outline" className="h-8 text-xs">
              <Webhook className="mr-1.5 h-3.5 w-3.5 text-brand-primary" />
              Webhooks
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

      {merchantId === null && !errorMsg && (
        <StatusBanner tone="info" title="Merchant access pending">
          Applied already? Sign in again once approved. Otherwise,{' '}
          <Link to="/merchant/register" className="underline">
            register your business
          </Link>
          .
        </StatusBanner>
      )}
      {profile && profile.status !== 'APPROVED' && (
        <StatusBanner tone="warning" title="Approval required">
          API keys and order uploads unlock once you're approved.
        </StatusBanner>
      )}
      {/* KPI Stats Strip */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-secondary">
            <span>Uploads</span>
            <Layers className="h-4 w-4 text-brand-primary" />
          </div>
          <div className="mt-1 text-lg font-bold text-primary">{batches.length}</div>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-secondary">
            <span>Orders accepted</span>
            <PackageCheck className="h-4 w-4 text-status-success-fg" />
          </div>
          <div className="mt-1 text-lg font-bold text-primary">{totalOrdersDispatched}</div>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-secondary">
            <span>Valid rows</span>
            <CheckCircle2 className="h-4 w-4 text-status-info-fg" />
          </div>
          <div className="mt-1 text-lg font-bold text-primary">{overallSuccessRate}%</div>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-xs text-secondary">
            <span>Pickup address</span>
            <MapPin className="h-4 w-4 text-status-warning-fg" />
          </div>
          <div
            className={`mt-1 truncate text-xs font-semibold ${profile?.pickupAddress ? 'text-primary' : 'text-muted'}`}
          >
            {profile?.pickupAddress || 'Not set'}
          </div>
        </SurfaceCard>
      </div>

      {/* Two Column Ingestion & Key Management Strip */}
      <div className="grid gap-4 lg:grid-cols-2">
        {merchantId && profile?.status === 'APPROVED' ? (
          <>
            <BulkCsvDropzone merchantId={merchantId} onBatchIngested={fetchMerchantData} />
            <ApiKeyManagerCard merchantId={merchantId} />
          </>
        ) : merchantId === null ? (
          <SurfaceCard className="p-4 text-xs text-secondary lg:col-span-2">
            Your login isn't linked to a business yet. Contact SwiftDrop support.
          </SurfaceCard>
        ) : null}
      </div>

      {merchantId && (
        <MerchantOrdersTable
          key={merchantId}
          merchantId={merchantId}
          refreshToken={ordersRevision}
        />
      )}

      {/* Ingestion Batches Table */}
      <SurfaceCard className="space-y-3 p-4" data-density="compact">
        <div className="flex items-center justify-between border-b border-border-subtle pb-2">
          <div className="flex items-center gap-2">
            <FileSpreadsheet className="h-4 w-4 text-brand-primary" />
            <h3 className="text-sm font-bold text-primary">
              Recent Ingestion Batches ({batches.length})
            </h3>
          </div>
          <span className="text-xs text-secondary">Automated Delivery Generation</span>
        </div>

        <div className="overflow-x-auto text-xs">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-border-subtle text-secondary">
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
                  <td colSpan={7} className="py-6 text-center text-secondary">
                    No uploads yet.
                  </td>
                </tr>
              ) : (
                batches.map((b) => (
                  <tr key={b.batchId} className="hover:bg-canvas">
                    <td className="py-2.5 font-medium text-primary">{b.fileName}</td>
                    <td className="py-2.5">
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                          b.batchStatus === 'COMPLETED'
                            ? 'bg-status-success-bg text-status-success-fg'
                            : b.batchStatus === 'PROCESSING'
                              ? 'bg-status-info-bg text-status-info-fg'
                              : 'bg-status-danger-bg text-status-danger-fg'
                        }`}
                      >
                        {b.batchStatus}
                      </span>
                    </td>
                    <td className="py-2.5 font-mono text-primary">{b.totalRecords}</td>
                    <td className="py-2.5 font-mono font-semibold text-status-success-fg">
                      {b.successCount}
                    </td>
                    <td className="py-2.5 font-mono text-status-danger-fg">{b.failedCount}</td>
                    <td className="py-2.5 text-secondary">
                      {b.createdAt
                        ? new Date(b.createdAt).toLocaleString([], {
                            dateStyle: 'short',
                            timeStyle: 'short'
                          })
                        : 'N/A'}
                    </td>
                    <td className="py-2.5 text-right">
                      <button
                        type="button"
                        onClick={() => handleViewBatchItems(b)}
                        className="inline-flex items-center gap-1 rounded bg-canvas px-2 py-1 text-[11px] font-medium text-primary hover:bg-border-subtle"
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
        title={selectedBatch?.fileName ?? 'Upload'}
        size="lg"
      >
        <div className="space-y-3 pt-2 text-xs">
          {itemsLoading ? (
            <div className="py-8 text-center text-secondary">Loading batch parcels...</div>
          ) : batchItems.length === 0 ? (
            <div className="py-8 text-center text-secondary">No items found for this batch.</div>
          ) : (
            <div className="max-h-96 overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="border-b border-border-subtle text-secondary">
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
                    <tr key={item.rowIndex} className="hover:bg-canvas">
                      <td className="py-2 text-muted">{item.rowIndex}</td>
                      <td className="py-2 font-mono font-bold text-brand-primary">
                        {item.trackingNumber || 'Pending'}
                      </td>
                      <td className="py-2">
                        <div className="font-medium text-primary">{item.recipientName}</div>
                        <div className="text-[11px] text-muted">{item.recipientPhone}</div>
                      </td>
                      <td className="py-2 font-medium text-primary">{item.deliveryDistrict}</td>
                      <td className="py-2 font-semibold text-secondary">{item.deliverySpeed}</td>
                      <td className="py-2">
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                            item.itemStatus === 'SUCCESS'
                              ? 'bg-status-success-bg text-status-success-fg'
                              : 'bg-status-danger-bg text-status-danger-fg'
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
