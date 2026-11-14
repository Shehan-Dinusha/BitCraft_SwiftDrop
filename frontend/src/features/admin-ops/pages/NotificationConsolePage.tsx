import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Bell,
  CheckCircle2,
  Filter,
  Mail,
  RefreshCw,
  Search,
  Smartphone,
  XCircle,
  AlertTriangle,
  Send,
  ShieldCheck
} from 'lucide-react';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import {
  notificationApi,
  type NotificationDispatchLogResponse
} from '@/services/api/notificationApi';

export function NotificationConsolePage() {
  const [channelFilter, setChannelFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedLog, setSelectedLog] = useState<NotificationDispatchLogResponse | null>(null);

  const {
    data: logs = [],
    isLoading,
    refetch,
    isFetching
  } = useQuery({
    queryKey: ['notifications', 'admin-console-logs', channelFilter, statusFilter],
    queryFn: async () => {
      const channel = channelFilter === 'ALL' ? undefined : channelFilter;
      const status = statusFilter === 'ALL' ? undefined : statusFilter;
      return notificationApi.listLogs({ channel, status });
    },
    refetchInterval: 10_000, // 10s live poll for demo inspection
    staleTime: 5_000
  });

  const filteredLogs = logs.filter((log) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      log.templateCode?.toLowerCase().includes(q) ||
      log.destination?.toLowerCase().includes(q) ||
      log.referenceId?.toLowerCase().includes(q) ||
      log.subject?.toLowerCase().includes(q)
    );
  });

  const totalLogs = logs.length;
  const sentCount = logs.filter((l) => l.status === 'SENT').length;
  const suppressedCount = logs.filter((l) => l.status === 'SUPPRESSED').length;
  const failedCount = logs.filter((l) => l.status === 'FAILED').length;

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
      case 'EMAIL':
        return <Mail className="size-3.5 text-purple-600" />;
      default:
        return <Smartphone className="size-3.5 text-emerald-600" />;
    }
  };

  return (
    <main className="space-y-5 p-4 md:p-6" data-density="compact">
      {/* Header */}
      <header className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
        <div>
          <div className="flex items-center gap-2">
            <Bell className="size-5 text-brand-primary" />
            <h1 className="text-2xl font-bold tracking-tight text-primary">
              Notification Audit Console
            </h1>
          </div>
          <p className="mt-1 text-caption text-secondary">
            Inspect real-time multi-channel dispatch logs, PII masking compliance, and template deliveries.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="h-8 gap-1.5 text-caption"
          >
            <RefreshCw className={`size-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh Logs
          </Button>
        </div>
      </header>

      {/* KPI Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-caption text-secondary">
            <span>Total Events</span>
            <Send className="size-4 text-indigo-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-primary">{totalLogs}</div>
          <p className="text-[11px] text-muted">Audited dispatches</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-caption text-emerald-600">
            <span>Sent Successfully</span>
            <CheckCircle2 className="size-4 text-emerald-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-emerald-600">{sentCount}</div>
          <p className="text-[11px] text-muted">Delivered via mock gateway</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-caption text-amber-600">
            <span>Suppressed</span>
            <AlertTriangle className="size-4 text-amber-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-amber-600">{suppressedCount}</div>
          <p className="text-[11px] text-muted">User opt-out / preferences</p>
        </SurfaceCard>

        <SurfaceCard className="p-3">
          <div className="flex items-center justify-between text-caption text-rose-600">
            <span>Failed</span>
            <XCircle className="size-4 text-rose-500" />
          </div>
          <div className="mt-1 text-2xl font-bold text-rose-600">{failedCount}</div>
          <p className="text-[11px] text-muted">Network / format error</p>
        </SurfaceCard>
      </div>

      {/* Privacy Notice Banner */}
      <div className="flex items-center gap-2 rounded-lg border border-border-subtle bg-card px-4 py-2.5 text-caption text-secondary">
        <ShieldCheck className="size-4 text-emerald-600 shrink-0" />
        <span>
          <strong>PII Protection Active:</strong> Recipient phone numbers (e.g. <code>07X***5678</code>) and emails (e.g. <code>a***@domain.com</code>) are masked at the dispatcher boundary in compliance with Stage 12.
        </span>
      </div>

      {/* Main Grid: Filters + Table + Detail Panel */}
      <div className="space-y-4">
        {/* Filter Bar */}
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border-subtle bg-card p-3">
          <div className="flex flex-1 flex-wrap items-center gap-3">
            <div className="relative min-w-[200px] flex-1 max-w-xs">
              <Search className="absolute left-2.5 top-2 size-3.5 text-muted" />
              <input
                type="text"
                placeholder="Search template, tracking#, recipient..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full rounded-lg border border-border-subtle bg-canvas pl-8 pr-3 py-1.5 text-caption text-primary focus:outline-none focus:ring-1 focus:ring-border-focus"
              />
            </div>

            <div className="flex items-center gap-1.5 text-caption text-secondary">
              <Filter className="size-3.5" />
              <span>Channel:</span>
            </div>
            <select
              value={channelFilter}
              onChange={(e) => setChannelFilter(e.target.value)}
              className="rounded-lg border border-border-subtle bg-canvas px-2.5 py-1 text-caption text-primary focus:outline-none focus:ring-1 focus:ring-border-focus"
            >
              <option value="ALL">All Channels</option>
              <option value="EMAIL">Email</option>
              <option value="PUSH">Push</option>
            </select>

            <div className="flex items-center gap-1.5 text-caption text-secondary">
              <span>Status:</span>
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-lg border border-border-subtle bg-canvas px-2.5 py-1 text-caption text-primary focus:outline-none focus:ring-1 focus:ring-border-focus"
            >
              <option value="ALL">All Statuses</option>
              <option value="SENT">Sent</option>
              <option value="SUPPRESSED">Suppressed</option>
              <option value="FAILED">Failed</option>
            </select>
          </div>
        </div>

        {/* Audit Log Table & Inspector */}
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          {/* Table (7 cols) */}
          <div className="overflow-hidden rounded-xl border border-border-subtle bg-card shadow-sm lg:col-span-7">
            {isLoading ? (
              <div className="flex h-72 flex-col items-center justify-center gap-2">
                <RefreshCw className="size-6 animate-spin text-muted" />
                <span className="text-caption text-muted">Loading notification logs...</span>
              </div>
            ) : filteredLogs.length === 0 ? (
              <div className="flex h-72 flex-col items-center justify-center gap-2 p-8 text-center">
                <Bell className="size-8 text-muted opacity-40" />
                <p className="text-body-strong text-primary">No notification records found</p>
                <p className="text-caption text-muted">
                  Logs appear automatically when parcel status transitions occur.
                </p>
              </div>
            ) : (
              <div className="max-h-[540px] overflow-y-auto">
                <table className="w-full text-left text-caption">
                  <thead className="sticky top-0 border-b border-border-subtle bg-elevated text-muted">
                    <tr>
                      <th className="px-3 py-2.5 font-medium">Time</th>
                      <th className="px-3 py-2.5 font-medium">Channel</th>
                      <th className="px-3 py-2.5 font-medium">Template</th>
                      <th className="px-3 py-2.5 font-medium">Masked Recipient</th>
                      <th className="px-3 py-2.5 font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border-subtle">
                    {filteredLogs.map((log) => {
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
                          <td className="whitespace-nowrap px-3 py-2.5 font-mono text-[11px] text-muted">
                            {formattedTime}
                          </td>
                          <td className="px-3 py-2.5">
                            <span className="inline-flex items-center gap-1.5 font-medium text-primary">
                              {getChannelIcon(log.channel)}
                              {log.channel}
                            </span>
                          </td>
                          <td className="px-3 py-2.5 font-mono text-[11px] text-secondary">
                            {log.templateCode ?? 'N/A'}
                          </td>
                          <td className="px-3 py-2.5 font-mono text-[11px] text-primary">
                            {log.destination || 'Masked Recipient'}
                          </td>
                          <td className="px-3 py-2.5">{getStatusBadge(log.status)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Inspector Panel (5 cols) */}
          <div className="flex flex-col rounded-xl border border-border-subtle bg-card p-4 shadow-sm lg:col-span-5">
            {selectedLog ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between border-b border-border-subtle pb-3">
                  <div>
                    <h2 className="text-body-strong text-primary">Dispatch Audit Record</h2>
                    <span className="font-mono text-[11px] text-muted">{selectedLog.logId}</span>
                  </div>
                  {getStatusBadge(selectedLog.status)}
                </div>

                <div className="grid grid-cols-2 gap-3 text-caption">
                  <div>
                    <span className="text-muted">Template Code:</span>
                    <p className="font-mono font-medium text-primary">{selectedLog.templateCode}</p>
                  </div>
                  <div>
                    <span className="text-muted">Dispatch Channel:</span>
                    <p className="font-medium text-primary">{selectedLog.channel}</p>
                  </div>
                  <div>
                    <span className="text-muted">Masked Destination:</span>
                    <p className="font-mono font-medium text-primary">
                      {selectedLog.destination || 'Masked'}
                    </p>
                  </div>
                  <div>
                    <span className="text-muted">Tracking / Ref ID:</span>
                    <p className="font-mono font-medium text-primary">
                      {selectedLog.referenceId || 'N/A'}
                    </p>
                  </div>
                  <div className="col-span-2">
                    <span className="text-muted">Dispatch Timestamp:</span>
                    <p className="text-secondary">
                      {selectedLog.sentAt ? new Date(selectedLog.sentAt).toLocaleString() : 'N/A'}
                    </p>
                  </div>
                </div>

                {selectedLog.subject && (
                  <div className="border-t border-border-subtle pt-3">
                    <span className="text-caption text-muted">Subject:</span>
                    <p className="text-caption font-medium text-primary">{selectedLog.subject}</p>
                  </div>
                )}

                <div className="border-t border-border-subtle pt-3">
                  <span className="text-caption text-muted">Rendered Content Body:</span>
                  <pre className="mt-1.5 whitespace-pre-wrap rounded-lg bg-canvas p-3 font-mono text-[11px] text-primary border border-border-subtle">
                    {selectedLog.content || '(Empty content)'}
                  </pre>
                </div>

                {selectedLog.failureReason && (
                  <div className="rounded-lg bg-rose-50 p-3 text-caption text-rose-800 border border-rose-200">
                    <span className="font-semibold">Dispatch Failure Reason:</span>
                    <p className="mt-1">{selectedLog.failureReason}</p>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex h-72 flex-col items-center justify-center gap-2 text-center text-muted">
                <Bell className="size-8 opacity-40" />
                <p className="text-body font-medium text-secondary">No record selected</p>
                <p className="text-caption text-muted">
                  Click any row in the audit log to inspect rendered template body, masked phone/email, and status.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}

export default NotificationConsolePage;
