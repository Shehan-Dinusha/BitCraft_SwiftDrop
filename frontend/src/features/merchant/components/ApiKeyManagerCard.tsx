import { useState, useEffect, useCallback } from 'react';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { Modal } from '@/design-system/primitives/Modal';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { merchantApi, type ApiKeyResponse } from '@/services/api/merchantApi';
import { Key, Plus, Trash2, Copy, Check, ShieldAlert, Clock, RefreshCw } from 'lucide-react';

export interface ApiKeyManagerCardProps {
  merchantId: string;
}

function usageExamples(secret: string) {
  const url = `${window.location.origin}/api/v1/b2b/orders`;
  const payload = {
    externalRef: 'SHOP-001',
    pickup: {
      addressLine1: '10 Galle Road',
      city: 'Colombo',
      district: 'COLOMBO',
      postalCode: '00300',
      contactName: 'Test Shop',
      contactPhone: '+94771234567'
    },
    delivery: {
      addressLine1: '25 Kandy Road',
      city: 'Gampaha',
      district: 'GAMPAHA',
      postalCode: '11000',
      contactName: 'Test Recipient',
      contactPhone: '+94711234567'
    },
    parcel: { weightGrams: 1500, prohibitedItemsDeclared: true, itemDescription: 'Books' },
    speed: 'STANDARD'
  };
  return {
    curl: `curl --request POST '${url}' --header 'X-API-Key: ${secret}' --header 'Content-Type: application/json' --data '${JSON.stringify(payload)}'`,
    postman: JSON.stringify(
      {
        info: {
          name: 'SwiftDrop merchant order',
          schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
        },
        item: [
          {
            name: 'Create order',
            request: {
              method: 'POST',
              url,
              header: [
                { key: 'X-API-Key', value: secret },
                { key: 'Content-Type', value: 'application/json' }
              ],
              body: {
                mode: 'raw',
                raw: JSON.stringify(payload, null, 2),
                options: { raw: { language: 'json' } }
              }
            }
          }
        ]
      },
      null,
      2
    )
  };
}

export function ApiKeyManagerCard({ merchantId }: ApiKeyManagerCardProps) {
  const [mutating, setMutating] = useState(false);
  const [keys, setKeys] = useState<ApiKeyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [keyName, setKeyName] = useState('');
  const [expiresInDays, setExpiresInDays] = useState<number | ''>(90);
  const [generating, setGenerating] = useState(false);
  const [newlyCreatedKey, setNewlyCreatedKey] = useState<ApiKeyResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const fetchKeys = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMsg(null);
      const res = await merchantApi.listApiKeys(merchantId);
      setKeys(res || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch API keys';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  }, [merchantId]);

  useEffect(() => {
    fetchKeys();
  }, [fetchKeys]);

  const handleGenerateKey = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyName.trim()) {
      setErrorMsg('Key name is required');
      return;
    }
    if (expiresInDays !== '' && (!Number.isInteger(expiresInDays) || expiresInDays < 1)) {
      setErrorMsg('Expiration must be a positive whole number of days.');
      return;
    }

    try {
      setGenerating(true);
      setErrorMsg(null);
      const res = await merchantApi.generateApiKey(merchantId, {
        keyName: keyName.trim(),
        expiresInDays: expiresInDays ? Number(expiresInDays) : undefined
      });
      setNewlyCreatedKey(res);
      setKeyName('');
      setExpiresInDays(90);
      setSuccessMsg('API key created! Copy the raw secret token now.');
      await fetchKeys();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to generate API key';
      setErrorMsg(msg);
    } finally {
      setGenerating(false);
    }
  };

  const handleRevokeKey = async (keyId?: string) => {
    if (!keyId || mutating) return;
    if (
      !window.confirm(
        'Are you sure you want to revoke this API key? This will immediately disconnect any active integrations.'
      )
    ) {
      return;
    }

    try {
      setMutating(true);
      setErrorMsg(null);
      await merchantApi.revokeApiKey(merchantId, keyId);
      setSuccessMsg('API key revoked successfully');
      await fetchKeys();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to revoke API key';
      setErrorMsg(msg);
    } finally {
      setMutating(false);
    }
  };

  const handleRegenerateKey = async (keyId?: string) => {
    if (
      !keyId ||
      mutating ||
      !window.confirm('Replace this key? The previous secret will stop working immediately.')
    )
      return;
    setMutating(true);
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      const replacement = await merchantApi.regenerateApiKey(merchantId, keyId);
      setNewlyCreatedKey(replacement);
      setCopied(false);
      setIsModalOpen(true);
      await fetchKeys();
    } catch (err) {
      setErrorMsg(apiErrorMessage(err, 'Unable to regenerate API key.'));
    } finally {
      setMutating(false);
    }
  };

  const handleCopySecret = async (secret?: string) => {
    if (!secret) return;
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setErrorMsg('Clipboard unavailable. Select and copy the text manually.');
    }
  };

  return (
    <SurfaceCard className="space-y-4 p-4" data-density="compact">
      <div className="flex items-center justify-between border-b border-border-subtle pb-3">
        <div className="flex items-center gap-2">
          <Key className="h-4 w-4 text-indigo-500" />
          <h3 className="text-sm font-bold text-slate-900">API Keys & Machine Integration</h3>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            className="h-8 px-2 text-xs"
            onClick={fetchKeys}
            disabled={loading || mutating}
            aria-label="Refresh API keys"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          </Button>
          <Button
            variant="primary"
            className="h-8 px-3 text-xs"
            onClick={() => {
              setNewlyCreatedKey(null);
              setIsModalOpen(true);
            }}
            disabled={mutating || generating}
          >
            <Plus className="mr-1 h-3.5 w-3.5" />
            Generate New Key
          </Button>
        </div>
      </div>

      {errorMsg && (
        <StatusBanner tone="danger" title="API Key Error" onDismiss={() => setErrorMsg(null)}>
          {errorMsg}
        </StatusBanner>
      )}
      {successMsg && (
        <StatusBanner tone="success" title="Success" onDismiss={() => setSuccessMsg(null)}>
          {successMsg}
        </StatusBanner>
      )}

      {/* API Keys Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-border-subtle text-slate-500">
              <th className="pb-2 font-semibold">Key Name</th>
              <th className="pb-2 font-semibold">Key Prefix</th>
              <th className="pb-2 font-semibold">Status</th>
              <th className="pb-2 font-semibold">Expires</th>
              <th className="pb-2 font-semibold">Last Used</th>
              <th className="pb-2 text-right font-semibold">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-subtle">
            {keys.length === 0 ? (
              <tr>
                <td colSpan={6} className="py-6 text-center text-slate-500">
                  {loading
                    ? 'Loading API keys…'
                    : 'No API keys found. Generate a key to connect your integration.'}
                </td>
              </tr>
            ) : (
              keys.map((k) => (
                <tr key={k.keyId} className="hover:bg-slate-50/50">
                  <td className="py-2.5 font-medium text-slate-900">{k.keyName}</td>
                  <td className="py-2.5 font-mono text-slate-600">{k.keyPrefix}</td>
                  <td className="py-2.5">
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                        k.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-700'
                      }`}
                    >
                      {!k.active
                        ? 'REVOKED'
                        : k.expiresAt && new Date(k.expiresAt).getTime() <= Date.now()
                          ? 'EXPIRED'
                          : 'ACTIVE'}
                    </span>
                  </td>
                  <td className="py-2.5 text-slate-500">
                    {k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'Never'}
                  </td>
                  <td className="py-2.5 text-slate-500">
                    {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}
                  </td>
                  <td className="py-2.5 text-right">
                    {k.active && (!k.expiresAt || new Date(k.expiresAt).getTime() > Date.now()) && (
                      <Button
                        variant="outline"
                        disabled={mutating}
                        onClick={() => handleRegenerateKey(k.keyId)}
                      >
                        Regenerate
                      </Button>
                    )}
                    {k.active && (
                      <button
                        type="button"
                        onClick={() => handleRevokeKey(k.keyId)}
                        disabled={mutating}
                        className="inline-flex items-center gap-1 rounded p-1 text-rose-600 hover:bg-rose-50 hover:text-rose-700"
                        title="Revoke Key"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        <span className="text-[11px]">Revoke</span>
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Key Generation Modal */}
      <Modal
        open={isModalOpen}
        onClose={() => {
          setIsModalOpen(false);
          setNewlyCreatedKey(null);
        }}
        title={newlyCreatedKey ? 'API Key Generated' : 'Create Merchant API Key'}
        description={
          newlyCreatedKey
            ? 'Make sure to copy your secret key now. You will not be able to see it again.'
            : 'Generate a secure API key with live permissions for bulk ingestion.'
        }
        size="md"
      >
        {newlyCreatedKey ? (
          <div className="space-y-4 pt-2">
            <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-800">
              <div className="flex items-center gap-2 font-bold">
                <ShieldAlert className="h-4 w-4" />
                <span>Save Your Secret Token</span>
              </div>
              <p className="mt-1">
                SwiftDrop stores only a SHA-256 hash. Copy this secret now; closing this dialog
                removes it from view.
              </p>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-700">Secret API Key Token:</label>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  readOnly
                  value={newlyCreatedKey.rawApiKey || ''}
                  className="flex-1 rounded-lg border border-border-subtle bg-slate-100 p-2 font-mono text-xs text-slate-800 select-all"
                />
                <Button
                  variant="primary"
                  className="h-9 px-3 text-xs"
                  onClick={() => handleCopySecret(newlyCreatedKey.rawApiKey)}
                >
                  {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
                  <span className="ml-1">{copied ? 'Copied' : 'Copy'}</span>
                </Button>
              </div>
            </div>

            <div className="space-y-2 text-xs">
              <p className="font-semibold">Usage examples</p>
              <p>
                Replace the sample business and parcel details before submitting. Keep your secret
                out of shared files.
              </p>
              <textarea
                aria-label="cURL example"
                className="w-full rounded border border-border-subtle bg-card p-2 font-mono"
                rows={6}
                readOnly
                value={usageExamples(newlyCreatedKey.rawApiKey || '').curl}
              />
              <Button
                variant="outline"
                onClick={() =>
                  handleCopySecret(usageExamples(newlyCreatedKey.rawApiKey || '').curl)
                }
              >
                Copy cURL
              </Button>
              <textarea
                aria-label="Postman collection"
                className="w-full rounded border border-border-subtle bg-card p-2 font-mono"
                rows={5}
                readOnly
                value={usageExamples(newlyCreatedKey.rawApiKey || '').postman}
              />
              <Button
                variant="outline"
                onClick={() =>
                  handleCopySecret(usageExamples(newlyCreatedKey.rawApiKey || '').postman)
                }
              >
                Copy Postman collection
              </Button>
              {errorMsg && (
                <p role="alert" className="text-status-danger">
                  {errorMsg}
                </p>
              )}
            </div>
            <div className="flex justify-end pt-2">
              <Button
                variant="outline"
                className="h-8 text-xs"
                onClick={() => {
                  setIsModalOpen(false);
                  setNewlyCreatedKey(null);
                }}
              >
                Done
              </Button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleGenerateKey} className="space-y-4 pt-2">
            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-700">
                Key Label / Description *
              </label>
              <Input
                placeholder="e.g. ERP Ingestion Key (Warehouse A)"
                value={keyName}
                onChange={(e) => setKeyName(e.target.value)}
                required
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-700">Expiration (Days)</label>
              <div className="relative">
                <Input
                  inputMode="numeric"
                  pattern="[0-9]+"
                  placeholder="e.g. 90 (or leave blank for non-expiring)"
                  value={expiresInDays}
                  onChange={(e) =>
                    setExpiresInDays(e.target.value === '' ? '' : Number(e.target.value))
                  }
                />
                <Clock className="absolute right-3 top-2.5 h-4 w-4 text-slate-400" />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                className="h-8 text-xs"
                onClick={() => setIsModalOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="primary"
                className="h-8 text-xs"
                loading={generating}
                disabled={generating || !keyName.trim()}
              >
                Generate Key
              </Button>
            </div>
          </form>
        )}
      </Modal>
    </SurfaceCard>
  );
}
