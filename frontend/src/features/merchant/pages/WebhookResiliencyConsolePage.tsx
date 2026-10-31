import { useState, useEffect, useCallback } from 'react';
import { useAuthStore } from '@/store/useAuthStore';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { WebhookDlqReplayTable } from '../components/WebhookDlqReplayTable';
import {
  webhookApi,
  type WebhookEndpointResponse,
  type CreateWebhookEndpointRequest,
  type WebhookDeliveryAttemptResponse
} from '@/services/api/webhookApi';
import {
  Webhook,
  Plus,
  Play,
  Copy,
  Check,
  Eye,
  EyeOff,
  Trash2,
  Power,
  Activity,
  ShieldCheck,
  History,
  AlertTriangle,
  RefreshCw
} from 'lucide-react';

export function WebhookResiliencyConsolePage() {
  const user = useAuthStore((s) => s.user);
  const merchantId = user?.id || '22222222-2222-2222-2222-222222222222';

  const [endpoints, setEndpoints] = useState<WebhookEndpointResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [banner, setBanner] = useState<{ tone: 'success' | 'danger'; message: string } | null>(null);

  // Endpoint Creation Form
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [targetUrl, setTargetUrl] = useState('');
  const [subscribedEvents, setSubscribedEvents] = useState('parcel.status.changed');
  const [description, setDescription] = useState('');
  const [creating, setCreating] = useState(false);

  // Secret Key Visibility & Copied State
  const [revealedSecrets, setRevealedSecrets] = useState<Record<string, boolean>>({});
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Ping Testing State
  const [pingingId, setPingingId] = useState<string | null>(null);
  const [pingResult, setPingResult] = useState<{ endpointId: string; status: number; success: boolean } | null>(null);

  // Attempts History Modal
  const [selectedEndpoint, setSelectedEndpoint] = useState<WebhookEndpointResponse | null>(null);
  const [attempts, setAttempts] = useState<WebhookDeliveryAttemptResponse[]>([]);
  const [attemptsLoading, setAttemptsLoading] = useState(false);
  const [isAttemptsModalOpen, setIsAttemptsModalOpen] = useState(false);

  const fetchEndpoints = useCallback(async () => {
    try {
      setLoading(true);
      const data = await webhookApi.listEndpoints(merchantId);
      setEndpoints(data || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch webhook endpoints';
      setBanner({ tone: 'danger', message: msg });
    } finally {
      setLoading(false);
    }
  }, [merchantId]);

  useEffect(() => {
    fetchEndpoints();
  }, [fetchEndpoints]);

  const handleCreateEndpoint = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetUrl.trim()) return;

    try {
      setCreating(true);
      const request: CreateWebhookEndpointRequest = {
        merchantId,
        targetUrl: targetUrl.trim(),
        subscribedEvents: subscribedEvents.trim() || 'parcel.status.changed',
        description: description.trim() || undefined
      };

      const created = await webhookApi.createEndpoint(request);
      setEndpoints((prev) => [created, ...prev]);
      setBanner({ tone: 'success', message: 'Webhook endpoint registered with generated HMAC-SHA256 secret.' });
      setIsCreateModalOpen(false);
      setTargetUrl('');
      setDescription('');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to register webhook endpoint';
      setBanner({ tone: 'danger', message: msg });
    } finally {
      setCreating(false);
    }
  };

  const handleToggleActive = async (endpoint: WebhookEndpointResponse) => {
    if (!endpoint.endpointId) return;
    try {
      const updated = await webhookApi.toggleEndpointActive(endpoint.endpointId, !endpoint.active);
      setEndpoints((prev) =>
        prev.map((ep) => (ep.endpointId === updated.endpointId ? updated : ep))
      );
      setBanner({
        tone: 'success',
        message: `Endpoint ${updated.active ? 'activated' : 'paused'} successfully.`
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update endpoint status';
      setBanner({ tone: 'danger', message: msg });
    }
  };

  const handleDeleteEndpoint = async (endpointId: string) => {
    if (!window.confirm('Are you sure you want to delete this webhook subscription?')) return;
    try {
      await webhookApi.deleteEndpoint(endpointId);
      setEndpoints((prev) => prev.filter((ep) => ep.endpointId !== endpointId));
      setBanner({ tone: 'success', message: 'Webhook endpoint deleted.' });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to delete endpoint';
      setBanner({ tone: 'danger', message: msg });
    }
  };

  const handleTestPing = async (endpointId: string) => {
    try {
      setPingingId(endpointId);
      setPingResult(null);
      const res = await webhookApi.testPing(endpointId);
      setPingResult({
        endpointId,
        status: res.httpStatusCode || 500,
        success: res.success || false
      });
      setBanner({
        tone: res.success ? 'success' : 'danger',
        message: `Test ping response: HTTP ${res.httpStatusCode || 500} (${res.success ? 'Success' : 'Failed'})`
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to execute test ping';
      setBanner({ tone: 'danger', message: msg });
    } finally {
      setPingingId(null);
    }
  };

  const handleViewAttempts = async (endpoint: WebhookEndpointResponse) => {
    if (!endpoint.endpointId) return;
    try {
      setSelectedEndpoint(endpoint);
      setIsAttemptsModalOpen(true);
      setAttemptsLoading(true);
      const data = await webhookApi.listAttempts(endpoint.endpointId);
      setAttempts(data || []);
    } catch (err: unknown) {
      console.error('Failed to load attempts history', err);
    } finally {
      setAttemptsLoading(false);
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const toggleRevealSecret = (id: string) => {
    setRevealedSecrets((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const activeCount = endpoints.filter((e) => e.active).length;

  return (
    <main className="space-y-5 p-4 md:p-6" data-density="compact">
      {/* Header */}
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <div className="flex items-center gap-2">
            <Webhook className="h-6 w-6 text-brand-primary" />
            <h1 className="text-xl font-bold tracking-tight text-slate-900">
              Webhook Resiliency Console
            </h1>
            <span className="rounded-full bg-indigo-100 px-2.5 py-0.5 text-xs font-semibold text-indigo-800">
              HMAC-SHA256 Signed
            </span>
          </div>
          <p className="text-xs text-slate-500">
            Real-time event streaming, 3-attempt exponential backoff retries, and manual Dead-Letter Queue (DLQ) replay.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            className="h-8 text-xs"
            onClick={fetchEndpoints}
            disabled={loading}
          >
            <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>

          <Button
            variant="primary"
            className="h-8 text-xs"
            onClick={() => setIsCreateModalOpen(true)}
          >
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            Register Webhook
          </Button>
        </div>
      </header>

      {banner && (
        <StatusBanner
          tone={banner.tone}
          title={banner.tone === 'success' ? 'Success' : 'Error'}
          onDismiss={() => setBanner(null)}
        >
          {banner.message}
        </StatusBanner>
      )}

      {/* KPI Highlights */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SurfaceCard className="p-3">
          <span className="text-xs text-secondary">Registered Endpoints</span>
          <p className="mt-1 text-xl font-bold text-primary">{endpoints.length}</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <span className="text-xs text-secondary">Active Listeners</span>
          <p className="mt-1 text-xl font-bold text-emerald-600">{activeCount}</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <span className="text-xs text-secondary">Backoff Retry Policy</span>
          <p className="mt-1 text-xs font-semibold text-primary">3 Retries (2s, 4s exp)</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <span className="text-xs text-secondary">Payload Security</span>
          <p className="mt-1 text-xs font-semibold text-indigo-600">X-SwiftDrop-Signature</p>
        </SurfaceCard>
      </div>

      {/* Registered Endpoints Section */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-sm font-bold text-primary">
            <Activity className="h-4 w-4 text-brand-primary" />
            Registered Webhook Endpoints
          </h2>
          <span className="text-xs text-secondary">{endpoints.length} configured</span>
        </div>

        {endpoints.length === 0 ? (
          <SurfaceCard className="p-8 text-center">
            <Webhook className="mx-auto h-8 w-8 text-secondary opacity-40" />
            <p className="mt-2 text-sm font-semibold text-primary">No webhook endpoints registered</p>
            <p className="mt-1 text-xs text-secondary">
              Configure an HTTPS endpoint to receive live parcel status transitions and order events.
            </p>
            <Button
              variant="outline"
              className="mt-3 text-xs"
              onClick={() => setIsCreateModalOpen(true)}
            >
              <Plus className="mr-1 h-3.5 w-3.5" />
              Register Your First Endpoint
            </Button>
          </SurfaceCard>
        ) : (
          <div className="space-y-3">
            {endpoints.map((ep) => {
              const isRevealed = ep.endpointId ? revealedSecrets[ep.endpointId] : false;
              const isCopied = ep.endpointId ? copiedId === ep.endpointId : false;
              const isPinging = ep.endpointId ? pingingId === ep.endpointId : false;

              return (
                <SurfaceCard key={ep.endpointId} className="p-4">
                  <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-sm font-bold text-primary">{ep.targetUrl}</span>
                        {ep.active ? (
                          <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-800">
                            ACTIVE
                          </span>
                        ) : (
                          <span className="rounded-full bg-slate-200 px-2 py-0.5 text-[11px] font-semibold text-slate-600">
                            PAUSED
                          </span>
                        )}
                        {ep.description && (
                          <span className="text-xs text-secondary">? {ep.description}</span>
                        )}
                      </div>

                      {/* Subscribed Events */}
                      <div className="flex flex-wrap items-center gap-1.5 pt-1">
                        <span className="text-[11px] text-secondary">Events:</span>
                        {ep.subscribedEvents?.split(',').map((ev) => (
                          <span
                            key={ev.trim()}
                            className="rounded bg-brand-subtle px-1.5 py-0.5 font-mono text-[10px] font-semibold text-brand-primary"
                          >
                            {ev.trim()}
                          </span>
                        ))}
                      </div>

                      {/* Secret Key Display */}
                      <div className="flex items-center gap-2 pt-1">
                        <span className="text-[11px] text-secondary flex items-center gap-1">
                          <ShieldCheck className="h-3 w-3 text-emerald-500" />
                          HMAC Secret:
                        </span>
                        <code className="rounded bg-surface-subtle px-2 py-0.5 font-mono text-[11px] text-primary">
                          {isRevealed
                            ? ep.secretKey
                            : ep.secretKey?.slice(0, 10) + '????????????????????'}
                        </code>

                        {ep.endpointId && (
                          <>
                            <button
                              type="button"
                              onClick={() => ep.endpointId && toggleRevealSecret(ep.endpointId)}
                              className="text-secondary hover:text-primary"
                              title={isRevealed ? 'Hide secret' : 'Reveal secret'}
                            >
                              {isRevealed ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                            </button>

                            <button
                              type="button"
                              onClick={() => ep.secretKey && ep.endpointId && copyToClipboard(ep.secretKey, ep.endpointId)}
                              className="text-secondary hover:text-primary"
                              title="Copy signing secret"
                            >
                              {isCopied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                            </button>
                          </>
                        )}
                      </div>
                    </div>

                    {/* Endpoint Control Actions */}
                    <div className="flex flex-wrap items-center gap-2 self-start sm:self-auto">
                      {ep.endpointId && pingResult?.endpointId === ep.endpointId && (
                        <span
                          className={`rounded px-2 py-0.5 text-[11px] font-bold ${
                            pingResult.success
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-rose-100 text-rose-800'
                          }`}
                        >
                          HTTP {pingResult.status}
                        </span>
                      )}

                      <Button
                        variant="outline"
                        className="h-7 px-2.5 text-xs"
                        disabled={isPinging || !ep.active}
                        onClick={() => ep.endpointId && handleTestPing(ep.endpointId)}
                      >
                        <Play className={`mr-1 h-3 w-3 ${isPinging ? 'animate-spin' : ''}`} />
                        Test Ping
                      </Button>

                      <Button
                        variant="ghost"
                        className="h-7 px-2.5 text-xs"
                        onClick={() => handleViewAttempts(ep)}
                      >
                        <History className="mr-1 h-3 w-3" />
                        Attempts
                      </Button>

                      <Button
                        variant="ghost"
                        className={`h-7 px-2 text-xs ${ep.active ? 'text-amber-600' : 'text-emerald-600'}`}
                        onClick={() => handleToggleActive(ep)}
                        title={ep.active ? 'Pause Webhook' : 'Activate Webhook'}
                      >
                        <Power className="mr-1 h-3 w-3" />
                        {ep.active ? 'Pause' : 'Activate'}
                      </Button>

                      <Button
                        variant="ghost"
                        className="h-7 px-2 text-xs text-rose-600 hover:text-rose-700"
                        onClick={() => ep.endpointId && handleDeleteEndpoint(ep.endpointId)}
                        title="Delete Webhook"
                      >
                        <Trash2 className="h-3 w-3" />
                      </Button>
                    </div>
                  </div>
                </SurfaceCard>
              );
            })}
          </div>
        )}
      </section>

      {/* Dead-Letter Queue (DLQ) & Manual Replay Section */}
      <section className="space-y-3 pt-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="flex items-center gap-2 text-sm font-bold text-primary">
              <AlertTriangle className="h-4 w-4 text-rose-500" />
              Dead-Letter Queue (DLQ) & Manual Replay Hub
            </h2>
            <p className="text-xs text-secondary">
              Dispatches that exhausted all 3 exponential retries are captured here for failure diagnostics and one-click replay.
            </p>
          </div>
        </div>

        <WebhookDlqReplayTable merchantId={merchantId} />
      </section>

      {/* Registration Modal */}
      <Modal
        open={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        title="Register New Webhook Endpoint"
        size="md"
      >
        <form onSubmit={handleCreateEndpoint} className="space-y-4 p-4 text-xs">
          <div>
            <label className="block font-medium text-primary">Target Endpoint URL (HTTPS)</label>
            <input
              type="url"
              required
              placeholder="https://api.yourdomain.com/webhooks/swiftdrop"
              value={targetUrl}
              onChange={(e) => setTargetUrl(e.target.value)}
              className="mt-1 w-full rounded-lg border border-border-subtle bg-canvas px-3 py-2 text-xs text-primary focus:border-brand-primary focus:outline-none"
            />
            <p className="mt-1 text-[11px] text-secondary">
              Must be a valid HTTP or HTTPS endpoint accessible from the SwiftDrop cluster.
            </p>
          </div>

          <div>
            <label className="block font-medium text-primary">Subscribed Event Patterns</label>
            <input
              type="text"
              required
              placeholder="parcel.status.changed,order.created"
              value={subscribedEvents}
              onChange={(e) => setSubscribedEvents(e.target.value)}
              className="mt-1 w-full rounded-lg border border-border-subtle bg-canvas px-3 py-2 text-xs text-primary focus:border-brand-primary focus:outline-none"
            />
            <p className="mt-1 text-[11px] text-secondary">
              Comma-separated topics (e.g. <code>parcel.status.changed</code>, <code>order.created</code>, or <code>*</code>).
            </p>
          </div>

          <div>
            <label className="block font-medium text-primary">Description (Optional)</label>
            <input
              type="text"
              placeholder="e.g. Production ERP System Webhook Listener"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="mt-1 w-full rounded-lg border border-border-subtle bg-canvas px-3 py-2 text-xs text-primary focus:border-brand-primary focus:outline-none"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setIsCreateModalOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={creating}>
              {creating ? 'Registering...' : 'Save & Generate Secret'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Attempts History Modal */}
      <Modal
        open={isAttemptsModalOpen}
        onClose={() => setIsAttemptsModalOpen(false)}
        title={`Delivery Attempts History - ${selectedEndpoint?.description || selectedEndpoint?.targetUrl}`}
        size="lg"
      >
        <div className="space-y-3 p-4 text-xs">
          {attemptsLoading ? (
            <div className="flex items-center justify-center p-8">
              <RefreshCw className="h-6 w-6 animate-spin text-brand-primary" />
            </div>
          ) : attempts.length === 0 ? (
            <p className="p-6 text-center text-secondary">No delivery attempts recorded for this endpoint yet.</p>
          ) : (
            <div className="max-h-96 space-y-2 overflow-y-auto">
              {attempts.map((att) => (
                <div
                  key={att.attemptId}
                  className="flex items-center justify-between rounded-lg border border-border-subtle bg-surface-subtle p-2.5"
                >
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2">
                      <span className="font-mono font-semibold text-brand-primary">{att.eventType}</span>
                      <span
                        className={`rounded px-1.5 py-0.2 text-[10px] font-bold ${
                          att.success
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-rose-100 text-rose-800'
                        }`}
                      >
                        HTTP {att.httpStatusCode}
                      </span>
                      <span className="text-[10px] text-secondary">Attempt #{att.attemptNumber}</span>
                    </div>
                    {att.errorMessage && (
                      <p className="font-mono text-[10px] text-rose-600">{att.errorMessage}</p>
                    )}
                  </div>
                  <span className="text-[10px] text-secondary">
                    {att.attemptedAt ? new Date(att.attemptedAt).toLocaleTimeString() : 'N/A'}
                  </span>
                </div>
              ))}
            </div>
          )}

          <div className="flex justify-end pt-2">
            <Button variant="ghost" onClick={() => setIsAttemptsModalOpen(false)}>
              Close
            </Button>
          </div>
        </div>
      </Modal>
    </main>
  );
}
