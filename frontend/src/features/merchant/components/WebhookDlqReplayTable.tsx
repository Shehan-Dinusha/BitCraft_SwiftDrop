import { useState, useEffect, useCallback } from 'react';
import { HighDensityDataGrid, type DataGridColumn } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { webhookApi, type DeadLetterWebhookResponse } from '@/services/api/webhookApi';
import { RefreshCw, Play, Eye, AlertOctagon, CheckCircle2, History } from 'lucide-react';

interface GridDlqRow extends DeadLetterWebhookResponse {
  id: string;
}

interface WebhookDlqReplayTableProps {
  merchantId?: string;
}

export function WebhookDlqReplayTable({ merchantId }: WebhookDlqReplayTableProps) {
  const [records, setRecords] = useState<DeadLetterWebhookResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [unreplayedOnly, setUnreplayedOnly] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<DeadLetterWebhookResponse | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [replayLoadingId, setReplayLoadingId] = useState<string | null>(null);
  const [batchLoading, setBatchLoading] = useState(false);
  const [banner, setBanner] = useState<{ tone: 'success' | 'danger'; message: string } | null>(null);

  const fetchRecords = useCallback(async () => {
    try {
      setLoading(true);
      const data = await webhookApi.listDlq(merchantId, unreplayedOnly);
      setRecords(data || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch DLQ records';
      setBanner({ tone: 'danger', message: msg });
    } finally {
      setLoading(false);
    }
  }, [merchantId, unreplayedOnly]);

  useEffect(() => {
    fetchRecords();
  }, [fetchRecords]);

  const handleSingleReplay = async (dlqId: string) => {
    try {
      setReplayLoadingId(dlqId);
      setBanner(null);
      const res = await webhookApi.replayDlq(dlqId);
      if (res.status === 'SUCCESS') {
        setBanner({ tone: 'success', message: `Webhook replayed successfully (Status: ${res.statusCode})` });
      } else {
        setBanner({ tone: 'danger', message: `Replay failed: ${res.message || 'Unknown error'}` });
      }
      await fetchRecords();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to replay webhook';
      setBanner({ tone: 'danger', message: msg });
    } finally {
      setReplayLoadingId(null);
    }
  };

  const handleBatchReplay = async () => {
    try {
      setBatchLoading(true);
      setBanner(null);
      const results = await webhookApi.replayAllDlq(merchantId);
      const successCount = results.filter((r) => r.status === 'SUCCESS').length;
      setBanner({
        tone: 'success',
        message: `Batch replay completed: ${successCount}/${results.length} succeeded.`
      });
      await fetchRecords();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Batch replay failed';
      setBanner({ tone: 'danger', message: msg });
    } finally {
      setBatchLoading(false);
    }
  };

  const rows: GridDlqRow[] = records.map((r) => ({
    ...r,
    id: r.dlqId || String(Math.random())
  }));

  const columns: DataGridColumn<GridDlqRow>[] = [
    {
      key: 'eventType',
      label: 'Event Type',
      render: (row) => (
        <div className="flex flex-col">
          <span className="font-mono text-xs font-semibold text-indigo-600 dark:text-indigo-400">
            {row.eventType || 'webhook.event'}
          </span>
          <span className="text-[10px] text-slate-400">
            {row.createdAt ? new Date(row.createdAt).toLocaleString() : 'N/A'}
          </span>
        </div>
      )
    },
    {
      key: 'failureReason',
      label: 'Failure Diagnostic',
      render: (row) => (
        <span
          className="block max-w-[240px] truncate text-xs text-rose-600 dark:text-rose-400"
          title={row.failureReason || 'Dispatch failure'}
        >
          {row.failureReason || 'Failed delivery attempts'}
        </span>
      )
    },
    {
      key: 'totalAttempts',
      label: 'Retries',
      render: (row) => (
        <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300">
          {row.totalAttempts || 3}/3
        </span>
      )
    },
    {
      key: 'replayed',
      label: 'State',
      render: (row) =>
        row.replayed ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
            <CheckCircle2 className="h-3 w-3" />
            REPLAYED
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 rounded-full bg-rose-100 px-2 py-0.5 text-[11px] font-semibold text-rose-800 dark:bg-rose-950 dark:text-rose-300">
            <AlertOctagon className="h-3 w-3" />
            DEAD-LETTERED
          </span>
        )
    },
    {
      key: 'id',
      label: 'Actions',
      align: 'right',
      render: (row) => (
        <div className="flex items-center justify-end gap-1.5">
          <Button
            variant="ghost"
            className="h-7 px-2 text-xs"
            onClick={() => {
              setSelectedRecord(row);
              setIsModalOpen(true);
            }}
          >
            <Eye className="mr-1 h-3 w-3" />
            Payload
          </Button>

          <Button
            variant="outline"
            className="h-7 px-2 text-xs"
            disabled={row.replayed || replayLoadingId === row.dlqId}
            onClick={() => row.dlqId && handleSingleReplay(row.dlqId)}
          >
            <Play className={`mr-1 h-3 w-3 ${replayLoadingId === row.dlqId ? 'animate-spin' : ''}`} />
            {row.replayed ? 'Replayed' : 'Replay'}
          </Button>
        </div>
      )
    }
  ];

  const unreplayedCount = records.filter((r) => !r.replayed).length;

  return (
    <div className="space-y-3" data-density="compact">
      {banner && (
        <StatusBanner
          tone={banner.tone}
          title={banner.tone === 'success' ? 'Success' : 'Error'}
          onDismiss={() => setBanner(null)}
        >
          {banner.message}
        </StatusBanner>
      )}

      {/* Control Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border-subtle bg-card p-3">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 text-xs text-secondary">
            <History className="h-4 w-4 text-brand-primary" />
            <span className="font-semibold text-primary">{records.length}</span> Total Records
            {unreplayedCount > 0 && (
              <span className="ml-1 rounded-full bg-rose-100 px-2 py-0.5 text-[11px] font-bold text-rose-800 dark:bg-rose-950 dark:text-rose-300">
                {unreplayedCount} Pending
              </span>
            )}
          </div>

          <label className="flex cursor-pointer items-center gap-1.5 text-xs text-secondary">
            <input
              type="checkbox"
              checked={unreplayedOnly}
              onChange={(e) => setUnreplayedOnly(e.target.checked)}
              className="rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
            />
            <span>Unreplayed Only</span>
          </label>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            className="h-7 text-xs"
            onClick={fetchRecords}
            disabled={loading}
          >
            <RefreshCw className={`mr-1 h-3 w-3 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>

          {unreplayedCount > 0 && (
            <Button
              variant="primary"
              className="h-7 text-xs"
              onClick={handleBatchReplay}
              disabled={batchLoading}
            >
              <Play className={`mr-1 h-3 w-3 ${batchLoading ? 'animate-spin' : ''}`} />
              Replay All Pending ({unreplayedCount})
            </Button>
          )}
        </div>
      </div>

      {/* High Density Table */}
      <HighDensityDataGrid
        rows={rows}
        columns={columns}
        label="Dead-Letter Webhook Queue"
        filterPlaceholder="Search DLQ records by event type, reason..."
        emptyMessage="No dead-lettered webhook events recorded."
        density="compact"
      />

      {/* Payload Diagnostic Modal */}
      <Modal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Dead-Letter Webhook Diagnostics"
        size="lg"
      >
        {selectedRecord && (
          <div className="space-y-3 p-4 text-xs">
            <div className="grid grid-cols-2 gap-2 rounded-lg bg-surface-subtle p-3">
              <div>
                <span className="text-secondary">DLQ ID:</span>
                <p className="font-mono font-medium text-primary">{selectedRecord.dlqId}</p>
              </div>
              <div>
                <span className="text-secondary">Endpoint ID:</span>
                <p className="font-mono font-medium text-primary">{selectedRecord.endpointId}</p>
              </div>
              <div>
                <span className="text-secondary">Event Type:</span>
                <p className="font-mono font-semibold text-brand-primary">{selectedRecord.eventType}</p>
              </div>
              <div>
                <span className="text-secondary">Logged At:</span>
                <p className="text-primary">{selectedRecord.createdAt ? new Date(selectedRecord.createdAt).toLocaleString() : 'N/A'}</p>
              </div>
            </div>

            <div>
              <span className="font-semibold text-rose-600 dark:text-rose-400">Terminal Failure Reason:</span>
              <p className="mt-1 rounded bg-rose-50 p-2 font-mono text-rose-800 dark:bg-rose-950 dark:text-rose-200">
                {selectedRecord.failureReason || 'Failed after maximum backoff retries'}
              </p>
            </div>

            <div>
              <span className="font-semibold text-secondary">JSON Payload Content:</span>
              <pre className="mt-1 max-h-60 overflow-auto rounded-lg bg-slate-900 p-3 font-mono text-[11px] text-emerald-400">
                {(() => {
                  try {
                    return JSON.stringify(JSON.parse(selectedRecord.payload || '{}'), null, 2);
                  } catch {
                    return selectedRecord.payload || '{}';
                  }
                })()}
              </pre>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={() => setIsModalOpen(false)}>
                Close
              </Button>
              <Button
                variant="primary"
                disabled={selectedRecord.replayed || replayLoadingId === selectedRecord.dlqId}
                onClick={() => {
                  if (selectedRecord.dlqId) {
                    handleSingleReplay(selectedRecord.dlqId);
                    setIsModalOpen(false);
                  }
                }}
              >
                <Play className="mr-1 h-3.5 w-3.5" />
                Replay Now
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
