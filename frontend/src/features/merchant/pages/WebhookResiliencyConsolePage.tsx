import { useState, useEffect, useCallback } from 'react';
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
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import {
  Webhook,
  Plus,
  Play,
  Copy,
  Check,
  Trash2,
  Power,
  Activity,
  History,
  AlertTriangle,
  RefreshCw
} from 'lucide-react';

/** "HTTP 500", or the failure class when no HTTP response was received (timeout, connection failure). */
function attemptOutcomeLabel(attempt: WebhookDeliveryAttemptResponse): string {
  if (attempt.httpStatusCode != null) return `HTTP ${attempt.httpStatusCode}`;
  return attempt.errorClass ?? (attempt.success ? 'OK' : 'NO RESPONSE');
}

export function WebhookResiliencyConsolePage() {
  const [endpoints, setEndpoints] = useState<WebhookEndpointResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [banner, setBanner] = useState<{ tone: 'success' | 'danger'; message: string } | null>(null);

  // Endpoint Creation Form
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [targetUrl, setTargetUrl] = useState('');
  const [subscribedEvents, setSubscribedEvents] = useState('parcel.status.changed');
  const [description, setDescription] = useState('');
  const [creating, setCreating] = useState(false);

  // One-time signing secret from the create response. Held only in component memory (never persisted) and
  // cleared when the dialog closes; GET/list responses no longer contain secrets, so it cannot reappear.
  const [oneTimeSecret, setOneTimeSecret] = useState<{ endpointId: string; secret: string } | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Ping Testing State
  const [pingingId, setPingingId] = useState<string | null>(null);
  const [pingResult, setPingResult] = useState<{ endpointId: string; label: string; success: boolean } | null>(null);

  // Attempts History Modal
  const [selectedEndpoint, setSelectedEndpoint] = useState<WebhookEndpointResponse | null>(null);
  const [attempts, setAttempts] = useState<WebhookDeliveryAttemptResponse[]>([]);
  const [attemptsLoading, setAttemptsLoading] = useState(false);
  const [isAttemptsModalOpen, setIsAttemptsModalOpen] = useState(false);

  const fetchEndpoints = useCallback(async () => {
    try {
      setLoading(true);
      // The backend scopes the list to the merchant in the caller's verified token.
      const data = await webhookApi.listEndpoints();
      setEndpoints(data || []);
    } catch (err: unknown) {
      const msg = apiErrorMessage(err, 'Failed to fetch webhook endpoints');
      setBanner({ tone: 'danger', message: msg });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchEndpoints();
  }, [fetchEndpoints]);

  const handleCreateEndpoint = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetUrl.trim()) return;

    try {
      setCreating(true);
      setCreateError(null);
      // No merchantId: the backend registers the endpoint under the caller's own merchant identity.
      const request: CreateWebhookEndpointRequest = {
        targetUrl: targetUrl.trim(),
        subscribedEvents: subscribedEvents.trim() || 'parcel.status.changed',
        description: description.trim() || undefined
      };

      const created = await webhookApi.createEndpoint(request);
      const endpoint = created.endpoint;
      if (endpoint) setEndpoints((prev) => [endpoint, ...prev]);
      if (endpoint?.endpointId && created.signingSecret) {
        setOneTimeSecret({ endpointId: endpoint.endpointId, secret: created.signingSecret });
      }
      setBanner({
        tone: 'success',
        message: 'Webhook endpoint registered. Copy its signing secret now: it is shown only once.'
      });
      setIsCreateModalOpen(false);
      setTargetUrl('');
      setDescription('');
    } catch (err: unknown) {
      // Shown inside the still-open dialog (a page-level banner would be hidden behind it), so the
      // merchant can read the backend's message, fix the input and resubmit.
      setCreateError(apiErrorMessage(err, 'Failed to register webhook endpoint'));
    } finally {
      setCreating(false);
    }
  };

  const closeCreateModal = () => {
    setIsCreateModalOpen(false);
    setCreateError(null);
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
      const msg = apiErrorMessage(err, 'Failed to update endpoint status');
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
      const msg = apiErrorMessage(err, 'Failed to delete endpoint');
      setBanner({ tone: 'danger', message: msg });
    }
  };

  const handleTestPing = async (endpointId: string) => {
    try {
      setPingingId(endpointId);
      setPingResult(null);
      const res = await webhookApi.testPing(endpointId);
      const label = attemptOutcomeLabel(res);
      setPingResult({ endpointId, label, success: res.success || false });
      const latency = res.latencyMs != null ? ` in ${res.latencyMs} ms` : '';
      const detail = res.success ? 'Success' : `Failed: ${res.errorMessage ?? 'unclassified failure'}`;
      setBanner({
        tone: res.success ? 'success' : 'danger',
        message: `Test ping result: ${label}${latency} (${detail})`
      });
    } catch (err: unknown) {
      const msg = apiErrorMessage(err, 'Failed to execute test ping');
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
      setIsAttemptsModalOpen(false);
      setBanner({ tone: 'danger', message: apiErrorMessage(err, 'Failed to load delivery attempts') });
    } finally {
      setAttemptsLoading(false);
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const activeCount = endpoints.filter((e) => e.active).length;

  return (
    <main className="space-y-5 p-4 md:p-6" data-density="compact">
      {/* Header */}
      <header className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
        <div>
          <div className="flex items-center gap-2">
            <Webhook className="h-6 w-6 text-brand-primary" />
            <h1 className="text-xl font-bold tracking-tight text-primary">
              Webhooks
            </h1>
            <span className="rounded-full bg-brand-subtle px-2.5 py-0.5 text-xs font-semibold text-brand-primary">
              HMAC-SHA256 Signed
            </span>
          </div>
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
          <p className="mt-1 text-xl font-bold text-status-success-fg">{activeCount}</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <span className="text-xs text-secondary">Backoff Retry Policy</span>
          <p className="mt-1 text-xs font-semibold text-primary">3 Retries (2s, 4s exp)</p>
        </SurfaceCard>
        <SurfaceCard className="p-3">
          <span className="text-xs text-secondary">Payload Security</span>
          <p className="mt-1 text-xs font-semibold text-brand-primary">X-SwiftDrop-Signature</p>
        </SurfaceCard>
      </div>

      {/* Registered Endpoints Section */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-sm font-bold text-primary">
            <Activity className="h-4 w-4 text-brand-primary" />
            Endpoints
          </h2>
          <span className="text-xs text-secondary">{endpoints.length} configured</span>
        </div>

        {endpoints.length === 0 ? (
          <SurfaceCard className="p-8 text-center">
            <Webhook className="mx-auto h-8 w-8 text-secondary opacity-40" />
            <p className="mt-2 text-sm font-semibold text-primary">No endpoints yet</p>
            <p className="mt-1 text-xs text-secondary">
              Add an HTTPS URL to receive parcel and order events.
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
              const isPinging = ep.endpointId ? pingingId === ep.endpointId : false;

              return (
                <SurfaceCard key={ep.endpointId} className="p-4">
                  <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-sm font-bold text-primary">{ep.targetUrl}</span>
                        {ep.active ? (
                          <span className="rounded-full bg-status-success-bg px-2 py-0.5 text-[11px] font-semibold text-status-success-fg">
                            ACTIVE
                          </span>
                        ) : (
                          <span className="rounded-full bg-border-subtle px-2 py-0.5 text-[11px] font-semibold text-secondary">
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

                    </div>

                    {/* Endpoint Control Actions */}
                    <div className="flex flex-wrap items-center gap-2 self-start sm:self-auto">
                      {ep.endpointId && pingResult?.endpointId === ep.endpointId && (
                        <span
                          className={`rounded px-2 py-0.5 text-[11px] font-bold ${
                            pingResult.success
                              ? 'bg-status-success-bg text-status-success-fg'
                              : 'bg-status-danger-bg text-status-danger-fg'
                          }`}
                        >
                          {pingResult.label}
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
                        className={`h-7 px-2 text-xs ${ep.active ? 'text-status-warning-fg' : 'text-status-success-fg'}`}
                        onClick={() => handleToggleActive(ep)}
                        title={ep.active ? 'Pause Webhook' : 'Activate Webhook'}
                      >
                        <Power className="mr-1 h-3 w-3" />
                        {ep.active ? 'Pause' : 'Activate'}
                      </Button>

                      <Button
                        variant="ghost"
                        className="h-7 px-2 text-xs text-status-danger-fg hover:text-danger-hover"
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
              <AlertTriangle className="h-4 w-4 text-status-danger-fg" />
              Failed events
            </h2>
            <p className="text-xs text-secondary">Events that failed after 3 retries. Replay them here.</p>
          </div>
        </div>

        <WebhookDlqReplayTable />
      </section>

      {/* One-time signing secret (create response only) */}
      <Modal
        open={oneTimeSecret !== null}
        onClose={() => setOneTimeSecret(null)}
        title="Copy your signing secret"
        size="md"
      >
        {oneTimeSecret && (
          <div className="space-y-4 p-4 text-xs">
            <StatusBanner tone="warning" title="Shown only once">
              Copy it now and store it securely. You can&apos;t view it again.
            </StatusBanner>
            <div className="flex items-center gap-2">
              <code className="flex-1 break-all rounded bg-surface-subtle px-2 py-1.5 font-mono text-[11px] text-primary">
                {oneTimeSecret.secret}
              </code>
              <Button
                variant="outline"
                className="h-7 shrink-0 px-2.5 text-xs"
                onClick={() => copyToClipboard(oneTimeSecret.secret, oneTimeSecret.endpointId)}
              >
                {copiedId === oneTimeSecret.endpointId ? (
                  <Check className="mr-1 h-3 w-3 text-status-success-fg" />
                ) : (
                  <Copy className="mr-1 h-3 w-3" />
                )}
                {copiedId === oneTimeSecret.endpointId ? 'Copied' : 'Copy'}
              </Button>
            </div>
            <p className="text-[11px] text-secondary">
              Use it to verify the <span className="font-mono">X-SwiftDrop-Signature</span> header on every webhook
              request you receive.
            </p>
            <div className="flex justify-end">
              <Button onClick={() => setOneTimeSecret(null)}>I have stored the secret</Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Registration Modal */}
      <Modal
        open={isCreateModalOpen}
        onClose={closeCreateModal}
        title="Add endpoint"
        size="md"
      >
        <form onSubmit={handleCreateEndpoint} className="space-y-4 p-4 text-xs">
          {createError && (
            <StatusBanner tone="danger" title="Registration failed" onDismiss={() => setCreateError(null)}>
              {createError}
            </StatusBanner>
          )}

          <div>
            <label htmlFor="webhook-target-url" className="block font-medium text-primary">Target Endpoint URL (HTTPS)</label>
            <input id="webhook-target-url"
              type="url"
              required
              placeholder="https://api.yourdomain.com/webhooks/swiftdrop"
              value={targetUrl}
              onChange={(e) => setTargetUrl(e.target.value)}
              className="mt-1 w-full rounded-lg border border-border-subtle bg-canvas px-3 py-2 text-xs text-primary focus:border-brand-primary focus:outline-none"
            />
            <p className="mt-1 text-[11px] text-secondary">
              Public HTTPS URL only.
            </p>
          </div>

          <div>
            <label htmlFor="webhook-events" className="block font-medium text-primary">Subscribed Event Patterns</label>
            <input id="webhook-events"
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
            <label htmlFor="webhook-description" className="block font-medium text-primary">Description (Optional)</label>
            <input id="webhook-description"
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
              onClick={closeCreateModal}
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
        title={`Delivery attempts: ${selectedEndpoint?.description || selectedEndpoint?.targetUrl}`}
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
                            ? 'bg-status-success-bg text-status-success-fg'
                            : 'bg-status-danger-bg text-status-danger-fg'
                        }`}
                      >
                        {attemptOutcomeLabel(att)}
                      </span>
                      <span className="text-[10px] text-secondary">Attempt #{att.attemptNumber}</span>
                      {att.latencyMs != null && (
                        <span className="text-[10px] tabular-nums text-secondary">{att.latencyMs} ms</span>
                      )}
                    </div>
                    {att.errorMessage && (
                      <p className="font-mono text-[10px] text-status-danger-fg">{att.errorMessage}</p>
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
