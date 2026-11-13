import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Bell,
  CheckCircle2,
  Clock,
  Filter,
  Mail,
  MessageSquare,
  RefreshCw,
  Smartphone,
  XCircle,
  AlertTriangle
} from 'lucide-react';
import { ModalDrawerShell } from '@/design-system';
import { Button } from '@/design-system/primitives/Button';
import {
  notificationApi,
  type NotificationDispatchLogResponse
} from '@/services/api/notificationApi';

export interface NotificationLogModalProps {
  open: boolean;
  onClose: () => void;
}

export function NotificationLogModal({ open, onClose }: NotificationLogModalProps) {
  const [channelFilter, setChannelFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [selectedLog, setSelectedLog] = useState<NotificationDispatchLogResponse | null>(null);

  const {
    data: logs = [],
    isLoading,
    refetch,
    isFetching
  } = useQuery({
    queryKey: ['notifications', 'logs', channelFilter, statusFilter],
    queryFn: async () => {
      const channel = channelFilter === 'ALL' ? undefined : channelFilter;
      const status = statusFilter === 'ALL' ? undefined : statusFilter;
      return notificationApi.listLogs({ channel, status });
    },
    enabled: open,
    refetchInterval: open ? 10_000 : false, // Auto-refresh every 10s while open for demo inspection
    staleTime: 5_000
  });

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case 'SENT':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-caption font-medium text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="size-3" />
            SENT
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-caption font-medium text-rose-700 border border-rose-200">
            <XCircle className="size-3" />
            FAILED
          </span>
        );
      case 'SUPPRESSED':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-caption font-medium text-amber-700 border border-amber-200">
            <AlertTriangle className="size-3" />
            SUPPRESSED
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-slate-50 px-2 py-0.5 text-caption font-medium text-slate-700 border border-slate-200">
            {status ?? 'UNKNOWN'}
          </span>
        );
    }
  };

  const getChannelIcon = (channel?: string) => {
    switch (channel?.toUpperCase()) {
      case 'SMS':
        return <MessageSquare className="size-3.5 text-blue-600" />;
      case 'EMAIL':
        return <Mail className="size-3.5 text-purple-600" />;
      default:
        return <Smartphone className="size-3.5 text-emerald-600" />;
    }
  };

  return (
    <ModalDrawerShell
      mode="modal"
      size="lg"
      open={open}
      onClose={onClose}
      title="Notification Dispatch Audit Logs"
      description="Inspect real-time dispatch events, masked recipients, and template output across SMS, Email, and Push channels."
      footer={
        <div className="flex w-full items-center justify-between">
          <span className="text-caption text-muted">
            {logs.length} audit {logs.length === 1 ? 'record' : 'records'} found · Auto-refreshing every 10s
          </span>
          <Button variant="outline" className="h-8 text-caption" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4 p-4 sm:p-6">
        {/* Controls Bar */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-subtle pb-4">
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1.5 text-caption text-secondary">
              <Filter className="size-3.5" />
              <span>Channel:</span>
            </div>
            <select
              value={channelFilter}
              onChange={(e) => setChannelFilter(e.target.value)}
              className="rounded-lg border border-border-subtle bg-card px-2.5 py-1 text-caption text-primary focus:outline-none focus:ring-1 focus:ring-border-focus"
            >
              <option value="ALL">All Channels</option>
              <option value="SMS">SMS</option>
              <option value="EMAIL">Email</option>
              <option value="PUSH">Push</option>
            </select>

            <div className="flex items-center gap-1.5 pl-2 text-caption text-secondary">
              <span>Status:</span>
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-lg border border-border-subtle bg-card px-2.5 py-1 text-caption text-primary focus:outline-none focus:ring-1 focus:ring-border-focus"
            >
              <option value="ALL">All Statuses</option>
              <option value="SENT">Sent</option>
              <option value="SUPPRESSED">Suppressed</option>
              <option value="FAILED">Failed</option>
            </select>
          </div>

          <Button
            variant="outline"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="h-8 gap-1.5 text-caption"
          >
            <RefreshCw className={`size-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>

        {/* Content Body */}
        {isLoading ? (
          <div className="flex h-64 flex-col items-center justify-center gap-2">
            <RefreshCw className="size-6 animate-spin text-muted" />
            <span className="text-caption text-muted">Loading audit logs...</span>
          </div>
        ) : logs.length === 0 ? (
          <div className="flex h-64 flex-col items-center justify-center gap-2 text-center">
            <Bell className="size-8 text-muted opacity-40" />
            <p className="text-body-strong text-primary">No notification logs recorded</p>
            <p className="text-caption text-muted">
              Trigger a parcel status update or order creation to generate simulated dispatch logs.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
            {/* Logs List Table (7 cols) */}
            <div className="max-h-[460px] overflow-y-auto rounded-lg border border-border-subtle lg:col-span-7">
              <table className="w-full text-left text-caption">
                <thead className="sticky top-0 border-b border-border-subtle bg-card text-muted">
                  <tr>
                    <th className="px-3 py-2 font-medium">Time</th>
                    <th className="px-3 py-2 font-medium">Channel</th>
                    <th className="px-3 py-2 font-medium">Template</th>
                    <th className="px-3 py-2 font-medium">Masked Recipient</th>
                    <th className="px-3 py-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-subtle bg-card">
                  {logs.map((log) => {
                    const isSelected = selectedLog?.logId === log.logId;
                    const time = log.sentAt || log.createdAt;
                    const formattedTime = time ? new Date(time).toLocaleTimeString() : '—';
                    return (
                      <tr
                        key={log.logId}
                        onClick={() => setSelectedLog(log)}
                        className={`cursor-pointer transition-colors hover:bg-elevated ${
                          isSelected ? 'bg-brand-subtle' : ''
                        }`}
                      >
                        <td className="whitespace-nowrap px-3 py-2 font-mono text-[11px] text-muted">
                          {formattedTime}
                        </td>
                        <td className="px-3 py-2">
                          <span className="inline-flex items-center gap-1.5 font-medium text-primary">
                            {getChannelIcon(log.channel)}
                            {log.channel}
                          </span>
                        </td>
                        <td className="px-3 py-2 font-mono text-[11px] text-secondary">
                          {log.templateCode ?? 'N/A'}
                        </td>
                        <td className="px-3 py-2 font-mono text-[11px] text-primary">
                          {log.destination || 'Masked Recipient'}
                        </td>
                        <td className="px-3 py-2">{getStatusBadge(log.status)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Selected Log Inspector Panel (5 cols) */}
            <div className="flex max-h-[460px] flex-col overflow-y-auto rounded-lg border border-border-subtle bg-card p-4 lg:col-span-5">
              {selectedLog ? (
                <div className="space-y-3">
                  <div className="flex items-center justify-between border-b border-border-subtle pb-2">
                    <span className="text-body-strong text-primary">Audit Inspector</span>
                    {getStatusBadge(selectedLog.status)}
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-caption">
                    <div>
                      <span className="text-muted">Template:</span>
                      <p className="font-mono text-primary">{selectedLog.templateCode}</p>
                    </div>
                    <div>
                      <span className="text-muted">Channel:</span>
                      <p className="font-medium text-primary">{selectedLog.channel}</p>
                    </div>
                    <div>
                      <span className="text-muted">Masked Recipient:</span>
                      <p className="font-mono text-primary">{selectedLog.destination || 'Masked'}</p>
                    </div>
                    <div>
                      <span className="text-muted">Reference:</span>
                      <p className="font-mono text-primary">{selectedLog.referenceId || 'N/A'}</p>
                    </div>
                    <div className="col-span-2">
                      <span className="text-muted">Dispatched At:</span>
                      <p className="text-secondary">
                        {selectedLog.sentAt ? new Date(selectedLog.sentAt).toLocaleString() : 'N/A'}
                      </p>
                    </div>
                  </div>

                  {selectedLog.subject && (
                    <div className="border-t border-border-subtle pt-2">
                      <span className="text-caption text-muted">Subject:</span>
                      <p className="text-caption font-medium text-primary">{selectedLog.subject}</p>
                    </div>
                  )}

                  <div className="border-t border-border-subtle pt-2">
                    <span className="text-caption text-muted">Rendered Content:</span>
                    <pre className="mt-1 whitespace-pre-wrap rounded-md bg-elevated p-2.5 font-mono text-[11px] text-primary border border-border-subtle">
                      {selectedLog.content || '(Empty body)'}
                    </pre>
                  </div>

                  {selectedLog.failureReason && (
                    <div className="rounded-md bg-rose-50 p-2.5 text-caption text-rose-800 border border-rose-200">
                      <span className="font-semibold">Failure Reason:</span>
                      <p className="mt-0.5">{selectedLog.failureReason}</p>
                    </div>
                  )}
                </div>
              ) : (
                <div className="flex h-full flex-col items-center justify-center gap-1.5 text-center text-muted py-12">
                  <Clock className="size-6 opacity-40" />
                  <p className="text-caption">Select any record from the list to inspect payload & headers</p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </ModalDrawerShell>
  );
}

export default NotificationLogModal;
