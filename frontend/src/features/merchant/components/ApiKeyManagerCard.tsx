import { useState, useEffect, useCallback } from 'react';
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

export function ApiKeyManagerCard({ merchantId }: ApiKeyManagerCardProps) {
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
    if (!keyId) return;
    if (!window.confirm('Are you sure you want to revoke this API key? This will immediately disconnect any active integrations.')) {
      return;
    }

    try {
      setErrorMsg(null);
      await merchantApi.revokeApiKey(merchantId, keyId);
      setSuccessMsg('API key revoked successfully');
      await fetchKeys();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to revoke API key';
      setErrorMsg(msg);
    }
  };

  const handleCopySecret = async (secret?: string) => {
    if (!secret) return;
    await navigator.clipboard.writeText(secret);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <SurfaceCard className="space-y-4 p-4" data-density="compact">
      <div className="flex items-center justify-between border-b border-border-subtle pb-3">
        <div className="flex items-center gap-2">
          <Key className="h-4 w-4 text-indigo-500" />
          <h3 className="text-sm font-bold text-slate-900 dark:text-white">API Keys & Machine Integration</h3>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            className="h-8 px-2 text-xs"
            onClick={fetchKeys}
            disabled={loading}
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
            <tr className="border-b border-border-subtle text-slate-500 dark:text-slate-400">
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
                  No active API keys. Click "Generate New Key" to create an integration key for machine-to-machine order ingestion.
                </td>
              </tr>
            ) : (
              keys.map((k) => (
                <tr key={k.keyId} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/40">
                  <td className="py-2.5 font-medium text-slate-900 dark:text-white">
                    {k.keyName}
                  </td>
                  <td className="py-2.5 font-mono text-slate-600 dark:text-slate-300">
                    {k.keyPrefix}
                  </td>
                  <td className="py-2.5">
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                        k.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-400'
                      }`}
                    >
                      {k.active ? 'ACTIVE' : 'REVOKED'}
                    </span>
                  </td>
                  <td className="py-2.5 text-slate-500">
                    {k.expiresAt ? new Date(k.expiresAt).toLocaleDateString() : 'Never'}
                  </td>
                  <td className="py-2.5 text-slate-500">
                    {k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleDateString() : 'Never'}
                  </td>
                  <td className="py-2.5 text-right">
                    {k.active && (
                      <button
                        type="button"
                        onClick={() => handleRevokeKey(k.keyId)}
                        className="inline-flex items-center gap-1 rounded p-1 text-rose-600 hover:bg-rose-50 hover:text-rose-700 dark:hover:bg-rose-950/30"
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
            <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-800 dark:text-amber-200">
              <div className="flex items-center gap-2 font-bold">
                <ShieldAlert className="h-4 w-4" />
                <span>Save Your Secret Token</span>
              </div>
              <p className="mt-1">
                For security reasons, SwiftDrop stores only a salted SHA-256 hash. If lost, you must revoke this key and generate a new one.
              </p>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Secret API Key Token:
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  readOnly
                  value={newlyCreatedKey.rawApiKey || ''}
                  className="flex-1 rounded-lg border border-border-subtle bg-slate-100 p-2 font-mono text-xs text-slate-800 select-all dark:bg-slate-800 dark:text-slate-200"
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
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
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
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Expiration (Days)
              </label>
              <div className="relative">
                <Input
                  placeholder="e.g. 90 (or leave blank for non-expiring)"
                  value={expiresInDays}
                  onChange={(e) => setExpiresInDays(e.target.value === '' ? '' : Number(e.target.value))}
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
