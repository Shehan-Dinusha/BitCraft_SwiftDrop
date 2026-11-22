import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { Button, HighDensityDataGrid, Skeleton, StatusBanner } from '@/design-system';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { webhookApi, type FailedDeliveryResponse } from '@/services/api/webhookApi';

const KEY = ['webhooks', 'failed-deliveries'] as const;
/** Replays are sent in the background, so the list keeps itself up to date while one is on its way. */
const REFRESH_MS = 5_000;

type Row = FailedDeliveryResponse & { id: string };

const sending = (row: FailedDeliveryResponse) =>
  row.replayStatus === 'PENDING' || row.replayStatus === 'IN_FLIGHT' || row.replayStatus === 'RETRY';

/** Never replayed, or the last replay failed too. */
const canReplay = (row: FailedDeliveryResponse) => !row.replayStatus || row.replayStatus === 'DEAD';

function stateLabel(row: FailedDeliveryResponse) {
  if (row.replayStatus === 'SUCCEEDED') return { text: 'Delivered on replay', tone: 'success' };
  if (sending(row)) return { text: 'Sending again', tone: 'info' };
  return { text: row.replayStatus === 'DEAD' ? 'Replay failed' : 'Failed', tone: 'danger' };
}

const TONES: Record<string, string> = {
  success: 'bg-status-success-bg text-status-success-fg',
  info: 'bg-status-info-bg text-status-info-fg',
  danger: 'bg-status-danger-bg text-status-danger-fg'
};

const when = (value?: string) =>
  value
    ? new Date(value).toLocaleString('en-GB', {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit'
      })
    : '—';

/** Events we stopped trying to send, with Replay. The backend limits them to the caller's own business. */
export function FailedDeliveriesTable() {
  const client = useQueryClient();
  const [notice, setNotice] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const failed = useQuery({
    queryKey: KEY,
    queryFn: webhookApi.listFailedDeliveries,
    retry: 1,
    refetchInterval: ({ state }) => (state.data?.some(sending) ? REFRESH_MS : false)
  });
  const onError = (error: unknown) =>
    setNotice({ tone: 'danger', text: apiErrorMessage(error, 'The event could not be sent again.') });
  const refresh = () => client.invalidateQueries({ queryKey: KEY });

  const replay = useMutation({
    mutationFn: (deliveryId: string) => webhookApi.replayFailedDelivery(deliveryId),
    onSuccess: () => setNotice(null),
    onError,
    onSettled: refresh
  });
  const replayAll = useMutation({
    mutationFn: webhookApi.replayAllFailedDeliveries,
    onSuccess: (count) =>
      setNotice({
        tone: 'success',
        text: `Sending ${count} ${count === 1 ? 'event' : 'events'} again.`
      }),
    onError,
    onSettled: refresh
  });
  const busy = replay.isPending || replayAll.isPending;

  if (failed.isLoading) return <Skeleton preset="table-row" />;
  if (failed.isError && !failed.data) {
    return (
      <StatusBanner tone="danger" title="Failed events could not be loaded">
        {apiErrorMessage(failed.error, 'Please try again.')}
      </StatusBanner>
    );
  }

  const rows: Row[] = (failed.data ?? []).map((row) => ({ ...row, id: row.deliveryId ?? '' }));
  const waiting = rows.filter(canReplay).length;

  return (
    <div className="space-y-3">
      {notice ? (
        <StatusBanner
          tone={notice.tone}
          title={notice.tone === 'success' ? 'Replay started' : 'Not replayed'}
          onDismiss={() => setNotice(null)}
        >
          {notice.text}
        </StatusBanner>
      ) : null}
      {waiting > 1 ? (
        <div className="flex justify-end">
          <Button
            density="compact"
            variant="outline"
            leftIcon={<RotateCcw />}
            disabled={busy}
            loading={replayAll.isPending}
            onClick={() => replayAll.mutate()}
          >
            Replay all ({waiting})
          </Button>
        </div>
      ) : null}
      <HighDensityDataGrid<Row>
        rows={rows}
        label="Failed events"
        density="compact"
        searchable={rows.length > 10}
        filterPlaceholder="Search by tracking number, event or reason"
        emptyMessage="No failed events."
        columns={[
          { key: 'eventType', label: 'Event' },
          { key: 'trackingNumber', label: 'Parcel', render: (row) => row.trackingNumber ?? '—' },
          { key: 'errorMessage', label: 'Why it failed' },
          { key: 'failedAt', label: 'Gave up', render: (row) => when(row.failedAt) },
          {
            key: 'replayStatus',
            label: 'Status',
            render: (row) => {
              const state = stateLabel(row);
              return (
                <span
                  className={`rounded-full px-2 py-0.5 text-caption font-semibold ${TONES[state.tone]}`}
                >
                  {state.text}
                </span>
              );
            }
          },
          {
            key: 'id',
            label: 'Action',
            align: 'right',
            render: (row) =>
              canReplay(row) ? (
                <Button
                  density="compact"
                  variant="outline"
                  disabled={busy}
                  aria-label={`Replay ${row.eventType} for ${row.trackingNumber ?? 'this parcel'}`}
                  onClick={() => replay.mutate(row.id)}
                >
                  Replay
                </Button>
              ) : null
          }
        ]}
      />
    </div>
  );
}

export default FailedDeliveriesTable;
