import { useEffect, useState } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import {
  merchantApi,
  type MerchantApprovalResponse,
  type MerchantStatus
} from '@/services/api/merchantApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { HighDensityDataGrid } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';

export function MerchantApprovalsPage() {
  const role = useAuthStore((s) => s.user?.role);
  const [status, setStatus] = useState<MerchantStatus | ''>('PENDING_APPROVAL');
  const [rows, setRows] = useState<MerchantApprovalResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [revision, setRevision] = useState(0);
  const [decision, setDecision] = useState<{
    merchant: MerchantApprovalResponse;
    action: 'approve' | 'reject';
  } | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (role !== 'SYSTEM_ADMIN') return;
    let cancelled = false;
    setLoading(true);
    setError('');
    setRows([]);
    merchantApi
      .listApplications(status || undefined)
      .then((data) => {
        if (!cancelled) setRows(data);
      })
      .catch((err) => {
        if (!cancelled) setError(apiErrorMessage(err, 'Unable to load merchant applications.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [status, revision, role]);
  const confirm = async () => {
    if (!decision?.merchant.merchantId || busy) return;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      if (decision.action === 'approve') await merchantApi.approve(decision.merchant.merchantId);
      else await merchantApi.reject(decision.merchant.merchantId, reason.trim());
      setMessage(
        `${decision.merchant.businessName} ${decision.action === 'approve' ? 'approved' : 'rejected'}.`
      );
      setDecision(null);
      setRevision((v) => v + 1);
    } catch (err) {
      setError(
        apiErrorMessage(
          err,
          'Unable to save the decision. Refresh to check the latest application state.'
        )
      );
    } finally {
      setBusy(false);
    }
  };
  if (role !== 'SYSTEM_ADMIN')
    return (
      <main className="p-6">
        <StatusBanner tone="warning" title="Administrator access required">
          Only system administrators can review merchant applications.
        </StatusBanner>
      </main>
    );
  return (
    <main className="space-y-4 p-4 md:p-6" data-density="compact">
      <header>
        <h1 className="text-h1 text-primary">Merchant applications</h1>
        <p className="text-sm text-secondary">
          Review business details before enabling merchant integrations.
        </p>
      </header>
      <div className="flex flex-wrap items-center gap-3">
        <label className="text-sm text-primary">
          Status{' '}
          <select
            className="rounded-lg border border-border-subtle bg-card p-2"
            value={status}
            disabled={busy}
            onChange={(e) => setStatus(e.target.value as MerchantStatus | '')}
          >
            {['PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED', ''].map((s) => (
              <option key={s} value={s}>
                {s ? s.replaceAll('_', ' ') : 'All statuses'}
              </option>
            ))}
          </select>
        </label>
        <Button
          variant="outline"
          disabled={loading || busy}
          onClick={() => setRevision((v) => v + 1)}
        >
          Refresh
        </Button>
      </div>
      {error && (
        <StatusBanner tone="danger" title="Merchant review error">
          {error}
        </StatusBanner>
      )}
      {message && (
        <StatusBanner tone="success" title="Decision saved">
          {message}
        </StatusBanner>
      )}
      {loading ? (
        <p role="status">Loading applications…</p>
      ) : (
        <HighDensityDataGrid
          density="compact"
          label="Merchant applications"
          filterPlaceholder="Search business, registration or email"
          emptyMessage="No applications match this status."
          rows={rows.filter((r) => r.merchantId).map((r) => ({ ...r, id: r.merchantId! }))}
          columns={[
            { key: 'businessName', label: 'Business' },
            { key: 'businessRegNo', label: 'Registration' },
            { key: 'contactEmail', label: 'Email' },
            { key: 'contactPhone', label: 'Phone' },
            { key: 'pickupAddress', label: 'Pickup address' },
            { key: 'operatingDistrict', label: 'District' },
            { key: 'status', label: 'Status' },
            { key: 'rejectionReason', label: 'Rejection reason' },
            {
              key: 'createdAt',
              label: 'Applied',
              render: (r) => (r.createdAt ? new Date(r.createdAt).toLocaleString() : '—')
            },
            {
              key: 'id',
              label: 'Review',
              render: (r) =>
                r.status === 'PENDING_APPROVAL' ? (
                  <div className="flex gap-2">
                    {(['approve', 'reject'] as const).map((action) => (
                      <Button
                        key={action}
                        variant={action === 'reject' ? 'outline' : 'primary'}
                        disabled={busy}
                        onClick={() => {
                          setReason('');
                          setError('');
                          setDecision({ merchant: r, action });
                        }}
                      >
                        {action === 'approve' ? 'Approve' : 'Reject'}
                      </Button>
                    ))}
                  </div>
                ) : (
                  'Reviewed'
                )
            }
          ]}
        />
      )}
      <Modal
        open={!!decision}
        onClose={() => {
          if (!busy) setDecision(null);
        }}
        title={decision?.action === 'approve' ? 'Approve business' : 'Reject business'}
      >
        <div className="space-y-4">
          <p>{decision?.merchant.businessName}</p>
          {decision?.action === 'reject' ? (
            <label className="block text-sm">
              Rejection reason
              <textarea
                className="mt-1 block w-full rounded-lg border border-border-subtle bg-card p-2"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                maxLength={500}
                required
                disabled={busy}
                rows={4}
              />
            </label>
          ) : (
            <p className="text-sm text-secondary">
              This enables API keys and order ingestion for this merchant.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="outline" disabled={busy} onClick={() => setDecision(null)}>
              Cancel
            </Button>
            <Button
              loading={busy}
              disabled={busy || (decision?.action === 'reject' && !reason.trim())}
              onClick={confirm}
            >
              Confirm {decision?.action}
            </Button>
          </div>
        </div>
      </Modal>
    </main>
  );
}
export default MerchantApprovalsPage;
