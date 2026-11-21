import { useEffect, useState } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import {
  merchantApi,
  type MerchantApprovalResponse,
  type MerchantStatus,
  type SriLankanDistrict
} from '@/services/api/merchantApi';
import { SRI_LANKAN_DISTRICTS } from '@/services/api/authApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { HighDensityDataGrid } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';
import { categoryLabel, volumeLabel } from '@/features/merchant/components/merchantApplication';

/** What the dispatcher can check: the registration number and/or the website or social page. */
function VerifyWith({ regNo, url }: { regNo?: string | null; url?: string | null }) {
  // Only http(s) links are clickable; the backend stores the scheme, so anything else is shown as text.
  const safeUrl = url && /^https?:\/\//i.test(url) ? url : null;
  if (!regNo && !url) return <>—</>;
  return (
    <span className="flex flex-col gap-0.5">
      {regNo && <span className="font-mono">{regNo}</span>}
      {safeUrl ? (
        <a
          href={safeUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="max-w-[16rem] truncate text-brand-primary hover:underline"
        >
          {safeUrl.replace(/^https?:\/\//i, '')}
        </a>
      ) : (
        url && <span>{url}</span>
      )}
    </span>
  );
}

function districtName(value?: string | null) {
  return value
    ? value
        .toLowerCase()
        .split('_')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ')
    : '—';
}

type Action = 'approve' | 'reject' | 'suspend' | 'reactivate';
const ACTION_PAST: Record<Action, string> = {
  approve: 'approved',
  reject: 'rejected',
  suspend: 'suspended',
  reactivate: 'reactivated'
};

/**
 * Merchant applications. The dispatcher of each district reviews the businesses that collect from it;
 * a system admin sees every district and can suspend or reactivate approved merchants.
 */
export function MerchantApprovalsPage() {
  const role = useAuthStore((s) => s.user?.role);
  const ownDistrict = useAuthStore((s) => s.user?.district);
  const isAdmin = role === 'SYSTEM_ADMIN';
  const canReview = isAdmin || role === 'DISPATCHER';
  const [status, setStatus] = useState<MerchantStatus | ''>('PENDING_APPROVAL');
  const [district, setDistrict] = useState<SriLankanDistrict | ''>('');
  const [rows, setRows] = useState<MerchantApprovalResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [revision, setRevision] = useState(0);
  const [decision, setDecision] = useState<{
    merchant: MerchantApprovalResponse;
    action: Action;
  } | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!canReview) return;
    let cancelled = false;
    setLoading(true);
    setError('');
    setRows([]);
    merchantApi
      .listApplications(status || undefined, (isAdmin && district) || undefined)
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
  }, [status, district, revision, canReview, isAdmin]);
  const confirm = async () => {
    if (!decision?.merchant.merchantId || busy) return;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const id = decision.merchant.merchantId;
      if (decision.action === 'approve') await merchantApi.approve(id);
      else if (decision.action === 'reject') await merchantApi.reject(id, reason.trim());
      else if (decision.action === 'suspend') await merchantApi.suspend(id);
      else await merchantApi.reactivate(id);
      setMessage(
        `${decision.merchant.businessName} ${ACTION_PAST[decision.action]}.` +
          (decision.action === 'approve' || decision.action === 'reject'
            ? ' The owner has been emailed.'
            : '')
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
  if (!canReview)
    return (
      <main className="p-6">
        <StatusBanner tone="warning" title="Operations access required">
          Only dispatchers and system administrators can review merchant applications.
        </StatusBanner>
      </main>
    );
  return (
    <main className="space-y-4 p-4 md:p-6" data-density="compact">
      <header>
        <p className="text-xs font-semibold uppercase tracking-widest text-brand-primary">
          {isAdmin ? 'All districts' : `${districtName(ownDistrict)} district`}
        </p>
        <h1 className="text-h1 text-primary">Merchant applications</h1>
        <p className="text-sm text-secondary">
          {isAdmin
            ? 'Each district dispatcher reviews their own businesses. You can decide for any district and suspend approved merchants.'
            : 'Businesses that want SwiftDrop to collect from your district. Check the details and the pickup address before approving.'}
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
        {isAdmin && (
          <label className="text-sm text-primary">
            District{' '}
            <select
              className="rounded-lg border border-border-subtle bg-card p-2"
              value={district}
              disabled={busy}
              onChange={(e) => setDistrict(e.target.value as SriLankanDistrict | '')}
            >
              <option value="">All districts</option>
              {SRI_LANKAN_DISTRICTS.map((d) => (
                <option key={d} value={d}>
                  {districtName(d)}
                </option>
              ))}
            </select>
          </label>
        )}
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
            {
              key: 'businessCategory',
              label: 'Sells',
              render: (r) => categoryLabel(r.businessCategory)
            },
            {
              key: 'monthlyParcelVolume',
              label: 'Parcels / month',
              render: (r) => volumeLabel(r.monthlyParcelVolume)
            },
            {
              key: 'businessRegNo',
              label: 'Verify with',
              render: (r) => <VerifyWith regNo={r.businessRegNo} url={r.websiteUrl} />
            },
            { key: 'contactEmail', label: 'Email' },
            { key: 'contactPhone', label: 'Phone' },
            {
              key: 'pickupAddress',
              label: 'Pickup address',
              render: (r) =>
                [r.pickupAddress, r.pickupCity, r.pickupPostalCode].filter(Boolean).join(', ')
            },
            {
              key: 'operatingDistrict',
              label: 'District',
              render: (r) => districtName(r.operatingDistrict)
            },
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
                ) : isAdmin && (r.status === 'APPROVED' || r.status === 'SUSPENDED') ? (
                  <Button
                    variant="outline"
                    disabled={busy}
                    onClick={() => {
                      setError('');
                      setDecision({
                        merchant: r,
                        action: r.status === 'APPROVED' ? 'suspend' : 'reactivate'
                      });
                    }}
                  >
                    {r.status === 'APPROVED' ? 'Suspend' : 'Reactivate'}
                  </Button>
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
        title={
          decision
            ? `${decision.action.charAt(0).toUpperCase()}${decision.action.slice(1)} business`
            : 'Business'
        }
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
              {decision?.action === 'approve'
                ? 'This enables API keys and order ingestion for this merchant and emails the owner.'
                : decision?.action === 'suspend'
                  ? "The merchant's API keys stop working and it cannot upload orders until reactivated."
                  : 'The merchant can use its API keys and upload orders again.'}
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
