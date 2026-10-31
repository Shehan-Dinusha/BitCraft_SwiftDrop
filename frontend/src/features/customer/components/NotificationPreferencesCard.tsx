import { useState, useEffect } from 'react';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { Field } from '@/design-system/primitives/Field';
import {
  notificationApi,
  type NotificationDispatchLogResponse,
} from '@/services/api/notificationApi';
import { useAuthStore } from '@/store/useAuthStore';
import {
  Bell,
  Mail,
  Smartphone,
  CheckCircle2,
  AlertCircle,
  Clock,
  Send,
  Loader2,
  ShieldCheck,
} from 'lucide-react';

export function NotificationPreferencesCard() {
  const user = useAuthStore((s) => s.user);
  const [logs, setLogs] = useState<NotificationDispatchLogResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isDispatching, setIsDispatching] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Form states
  const [emailEnabled, setEmailEnabled] = useState(true);
  const [smsEnabled, setSmsEnabled] = useState(true);
  const [pushEnabled, setPushEnabled] = useState(true);
  const [emailAddress, setEmailAddress] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [languagePreference, setLanguagePreference] = useState('en');

  const recipientId = user?.id;

  useEffect(() => {
    loadPreferencesAndLogs();
  }, [recipientId]);

  const loadPreferencesAndLogs = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      if (recipientId) {
        const pref = await notificationApi.getPreferences(recipientId);
        setEmailEnabled(pref.emailEnabled ?? true);
        setSmsEnabled(pref.smsEnabled ?? true);
        setPushEnabled(pref.pushEnabled ?? true);
        setEmailAddress(pref.emailAddress ?? user?.email ?? '');
        setPhoneNumber(pref.phoneNumber ?? '');
        setLanguagePreference(pref.languagePreference ?? 'en');

        // Load logs
        const logData = await notificationApi.listLogs({ recipientId });
        setLogs(logData ?? []);
      } else {
        const pref = await notificationApi.getCurrentPreferences();
        setEmailEnabled(pref.emailEnabled ?? true);
        setSmsEnabled(pref.smsEnabled ?? true);
        setPushEnabled(pref.pushEnabled ?? true);
        setEmailAddress(pref.emailAddress ?? '');
        setPhoneNumber(pref.phoneNumber ?? '');
        setLanguagePreference(pref.languagePreference ?? 'en');
      }
    } catch (err: unknown) {
      console.warn('Failed to load preferences, using defaults:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSavePreferences = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recipientId) return;

    setIsSaving(true);
    setSaveSuccess(false);
    setErrorMessage(null);

    try {
      await notificationApi.updatePreferences(recipientId, {
        emailEnabled,
        smsEnabled,
        pushEnabled,
        emailAddress,
        phoneNumber,
        languagePreference,
      });
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 4000);
    } catch (err: unknown) {
      const errObj = err as { response?: { data?: { message?: string } }; message?: string };
      setErrorMessage(errObj?.response?.data?.message ?? errObj?.message ?? 'Failed to update preferences');
    } finally {
      setIsSaving(false);
    }
  };

  const handleSendTestNotification = async () => {
    if (!recipientId) return;

    setIsDispatching(true);
    setErrorMessage(null);

    try {
      const channel = smsEnabled && phoneNumber ? 'SMS' : 'EMAIL';
      const destination = channel === 'SMS' ? phoneNumber : emailAddress;

      await notificationApi.dispatch({
        recipientId,
        channel,
        templateCode: 'PARCEL_OUT_FOR_DELIVERY',
        destination,
        templateParams: {
          trackingNumber: 'SD-TEST-' + Math.floor(1000 + Math.random() * 9000),
          courierName: 'SwiftDrop Express Courier',
        },
        referenceId: 'TEST-DISPATCH',
      });

      // Reload logs
      const updatedLogs = await notificationApi.listLogs({ recipientId });
      setLogs(updatedLogs ?? []);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 4000);
    } catch (err: unknown) {
      const errObj = err as { response?: { data?: { message?: string } }; message?: string };
      setErrorMessage(errObj?.response?.data?.message ?? errObj?.message ?? 'Failed to send test alert');
    } finally {
      setIsDispatching(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center p-12 space-y-3">
        <Loader2 className="size-7 animate-spin text-brand-primary" />
        <p className="text-xs text-secondary font-medium">Loading notification preferences...</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Top Intro */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-border-subtle pb-4">
        <div>
          <h2 className="text-lg font-bold text-primary flex items-center gap-2">
            <Bell className="size-5 text-brand-primary" />
            Omnichannel Notification Preferences
          </h2>
          <p className="text-xs text-secondary mt-0.5">
            Choose how you receive real-time updates for courier pickups, transit checkpoints, and proof of delivery.
          </p>
        </div>

        <Button
          type="button"
          variant="outline"
          onClick={handleSendTestNotification}
          disabled={isDispatching}
          leftIcon={<Send className="size-3.5" />}
          className="self-start sm:self-auto"
        >
          {isDispatching ? 'Transmitting...' : 'Send Test Alert'}
        </Button>
      </div>

      {saveSuccess && (
        <div className="flex items-center gap-2 p-3.5 rounded-xl bg-success/10 border border-success/30 text-success text-xs font-semibold">
          <CheckCircle2 className="size-4 shrink-0" />
          <span>Notification preferences saved and synchronized successfully!</span>
        </div>
      )}

      {errorMessage && (
        <div className="flex items-center gap-2 p-3.5 rounded-xl bg-danger/10 border border-danger/30 text-danger text-xs font-semibold">
          <AlertCircle className="size-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      <form onSubmit={handleSavePreferences} className="space-y-6">
        {/* Channel Toggles Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* SMS Toggle */}
          <div
            onClick={() => setSmsEnabled(!smsEnabled)}
            className={`cursor-pointer rounded-2xl border p-4 transition-all duration-200 ${
              smsEnabled
                ? 'bg-brand-primary/5 border-brand-primary shadow-sm'
                : 'bg-elevated/40 border-border-subtle opacity-70 hover:opacity-100'
            }`}
          >
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2.5">
                <div
                  className={`p-2 rounded-xl ${
                    smsEnabled ? 'bg-brand-primary text-brand-on' : 'bg-muted text-secondary'
                  }`}
                >
                  <Smartphone className="size-4" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-primary">SMS Alerts</h4>
                  <p className="text-[11px] text-secondary">Instant dispatch & PIN alerts</p>
                </div>
              </div>
              <input
                type="checkbox"
                checked={smsEnabled}
                onChange={() => {}} // Controlled by parent card click
                className="size-4 rounded text-brand-primary cursor-pointer accent-brand-primary"
              />
            </div>
            <p className="text-[11px] text-secondary mt-3 leading-relaxed">
              Receive live SMS notifications with courier contact details, arrival ETAs, and verification codes.
            </p>
          </div>

          {/* Email Toggle */}
          <div
            onClick={() => setEmailEnabled(!emailEnabled)}
            className={`cursor-pointer rounded-2xl border p-4 transition-all duration-200 ${
              emailEnabled
                ? 'bg-brand-primary/5 border-brand-primary shadow-sm'
                : 'bg-elevated/40 border-border-subtle opacity-70 hover:opacity-100'
            }`}
          >
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2.5">
                <div
                  className={`p-2 rounded-xl ${
                    emailEnabled ? 'bg-brand-primary text-brand-on' : 'bg-muted text-secondary'
                  }`}
                >
                  <Mail className="size-4" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-primary">Email Digest</h4>
                  <p className="text-[11px] text-secondary">Waybills & receipts</p>
                </div>
              </div>
              <input
                type="checkbox"
                checked={emailEnabled}
                onChange={() => {}}
                className="size-4 rounded text-brand-primary cursor-pointer accent-brand-primary"
              />
            </div>
            <p className="text-[11px] text-secondary mt-3 leading-relaxed">
              Full electronic waybills, breakdown receipts, and digital Proof of Delivery attachments sent directly to your inbox.
            </p>
          </div>

          {/* Push Notifications Toggle */}
          <div
            onClick={() => setPushEnabled(!pushEnabled)}
            className={`cursor-pointer rounded-2xl border p-4 transition-all duration-200 ${
              pushEnabled
                ? 'bg-brand-primary/5 border-brand-primary shadow-sm'
                : 'bg-elevated/40 border-border-subtle opacity-70 hover:opacity-100'
            }`}
          >
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2.5">
                <div
                  className={`p-2 rounded-xl ${
                    pushEnabled ? 'bg-brand-primary text-brand-on' : 'bg-muted text-secondary'
                  }`}
                >
                  <Bell className="size-4" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-primary">Browser Push</h4>
                  <p className="text-[11px] text-secondary">Live tracking checkpoints</p>
                </div>
              </div>
              <input
                type="checkbox"
                checked={pushEnabled}
                onChange={() => {}}
                className="size-4 rounded text-brand-primary cursor-pointer accent-brand-primary"
              />
            </div>
            <p className="text-[11px] text-secondary mt-3 leading-relaxed">
              Desktop and mobile push banners when the courier is within a 2 km radius of the delivery address.
            </p>
          </div>
        </div>

        {/* Contact Coordinates & Language */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
          <Field label="Contact Phone for SMS" hint="Format: +947XXXXXXXX or 07XXXXXXXX">
            <Input
              variant="phone"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
              placeholder="+94771234567"
            />
          </Field>

          <Field label="Notification Email" hint="For delivery receipts & attachments">
            <Input
              variant="text"
              value={emailAddress}
              onChange={(e) => setEmailAddress(e.target.value)}
              placeholder="customer@example.com"
            />
          </Field>

          <Field label="Preferred Notification Language" hint="Template language">
            <div className="relative">
              <select
                value={languagePreference}
                onChange={(e) => setLanguagePreference(e.target.value)}
                className="w-full h-10 px-3 py-2 text-xs font-semibold rounded-xl bg-card border border-border-subtle focus:outline-none focus:ring-2 focus:ring-brand-primary text-primary"
              >
                <option value="en">English (Default)</option>
                <option value="si">Sinhala (සිංහල)</option>
                <option value="ta">Tamil (தமிழ்)</option>
              </select>
            </div>
          </Field>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t border-border-subtle">
          <Button
            type="submit"
            variant="primary"
            disabled={isSaving}
            leftIcon={isSaving ? <Loader2 className="size-4 animate-spin" /> : <ShieldCheck className="size-4" />}
          >
            {isSaving ? 'Saving Preferences...' : 'Save Notification Preferences'}
          </Button>
        </div>
      </form>

      {/* Audit History Log */}
      <div className="space-y-4 pt-6 border-t border-border-subtle">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold text-primary flex items-center gap-2">
            <Clock className="size-4 text-brand-primary" />
            Recent Notification Audit Logs
          </h3>
          <span className="text-[11px] text-secondary font-medium">
            {logs.length} logged dispatches
          </span>
        </div>

        {logs.length === 0 ? (
          <div className="text-center py-8 rounded-2xl border border-dashed border-border-subtle bg-elevated/20">
            <p className="text-xs text-secondary font-medium">No recent notification dispatches logged yet.</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-border-subtle bg-card">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border-subtle bg-elevated/40 text-[11px] text-secondary uppercase font-semibold">
                <tr>
                  <th className="px-4 py-2.5">Time</th>
                  <th className="px-4 py-2.5">Channel</th>
                  <th className="px-4 py-2.5">Template</th>
                  <th className="px-4 py-2.5">Destination</th>
                  <th className="px-4 py-2.5">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle">
                {logs.map((logItem) => (
                  <tr key={logItem.logId} className="hover:bg-elevated/20 transition-colors">
                    <td className="px-4 py-3 text-secondary font-mono text-[11px]">
                      {logItem.createdAt ? new Date(logItem.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'}
                    </td>
                    <td className="px-4 py-3 font-semibold">
                      <span className="inline-flex items-center gap-1 text-[11px] font-mono px-2 py-0.5 rounded-full bg-elevated border border-border-subtle">
                        {logItem.channel}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-medium text-primary">
                      {logItem.templateCode}
                    </td>
                    <td className="px-4 py-3 text-secondary font-mono text-[11px]">
                      {logItem.destination}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                          logItem.status === 'SENT'
                            ? 'bg-status-success/15 text-status-success border border-status-success/30'
                            : logItem.status === 'SUPPRESSED'
                            ? 'bg-status-warning/15 text-status-warning border border-status-warning/30'
                            : 'bg-status-danger/15 text-status-danger border border-status-danger/30'
                        }`}
                      >
                        {logItem.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
