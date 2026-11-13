import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Field } from '@/design-system/primitives/Field';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import {
  merchantApi,
  type RegisterMerchantRequest,
  type MerchantApprovalResponse,
  type SriLankanDistrict
} from '@/services/api/merchantApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';

const districts: SriLankanDistrict[] = [
  'COLOMBO',
  'GAMPAHA',
  'KALUTARA',
  'KANDY',
  'MATALE',
  'NUWARA_ELIYA',
  'GALLE',
  'MATARA',
  'HAMBANTOTA',
  'JAFFNA',
  'KILINOCHCHI',
  'MANNAR',
  'VAVUNIYA',
  'MULLAITIVU',
  'BATTICALOA',
  'AMPARA',
  'TRINCOMALEE',
  'KURUNEGALA',
  'PUTTALAM',
  'ANURADHAPURA',
  'POLONNARUWA',
  'BADULLA',
  'MONARAGALA',
  'RATNAPURA',
  'KEGALLE'
];

export function MerchantRegistrationPage() {
  const [form, setForm] = useState<RegisterMerchantRequest>({
    businessName: '',
    businessRegNo: '',
    contactEmail: '',
    contactPhone: '',
    pickupAddress: '',
    operatingDistrict: 'COLOMBO'
  });
  const [result, setResult] = useState<MerchantApprovalResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const request = {
        ...form,
        businessName: form.businessName.trim(),
        businessRegNo: form.businessRegNo.trim(),
        contactEmail: form.contactEmail.trim(),
        contactPhone: form.contactPhone.trim(),
        pickupAddress: form.pickupAddress.trim()
      };
      if (Object.values(request).some((value) => !value))
        throw new Error('Complete all business details.');
      setResult(await merchantApi.register(request));
    } catch (err) {
      setError(apiErrorMessage(err, 'Unable to submit your business application.'));
    } finally {
      setBusy(false);
    }
  };
  return (
    <main className="mx-auto max-w-3xl space-y-4 p-4 md:p-6" data-density="compact">
      <header>
        <h1 className="text-h1 text-primary">Register your business</h1>
        <p className="text-sm text-secondary">
          Apply for merchant access. API keys and order ingestion become available after approval.
        </p>
      </header>
      {error && (
        <StatusBanner tone="danger" title="Application not submitted">
          {error}
        </StatusBanner>
      )}
      {result ? (
        <SurfaceCard className="space-y-4 p-5">
          <StatusBanner tone="success" title="Application submitted">
            {result.businessName} is pending administrator approval.
          </StatusBanner>
          <p className="text-sm text-secondary">
            Application reference:{' '}
            <span className="font-mono tabular-nums">{result.merchantId}</span>
          </p>
          <p className="text-sm text-secondary">
            After approval, sign in again to refresh your merchant access.
          </p>
          <Link className="text-sm font-semibold text-brand-primary underline" to="/merchant">
            Return to merchant portal
          </Link>
        </SurfaceCard>
      ) : (
        <SurfaceCard className="p-5">
          <form onSubmit={submit} className="space-y-4">
            <fieldset disabled={busy} className="grid gap-4 sm:grid-cols-2">
              <legend className="sr-only">Business details</legend>
              <Field
                label="Business name"
                required
                maxLength={150}
                value={form.businessName}
                onChange={(e) => setForm({ ...form, businessName: e.target.value })}
              />
              <Field
                label="Business registration number"
                required
                maxLength={64}
                value={form.businessRegNo}
                onChange={(e) => setForm({ ...form, businessRegNo: e.target.value })}
              />
              <Field
                label="Business email"
                required
                maxLength={100}
                inputMode="email"
                pattern="[^\s@]+@[^\s@]+\.[^\s@]+"
                autoComplete="email"
                value={form.contactEmail}
                onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
              />
              <Field
                label="Business phone"
                required
                variant="phone"
                maxLength={30}
                autoComplete="tel"
                value={form.contactPhone}
                onChange={(e) => setForm({ ...form, contactPhone: e.target.value })}
              />
              <Field
                label="Pickup address"
                required
                maxLength={255}
                autoComplete="street-address"
                value={form.pickupAddress}
                onChange={(e) => setForm({ ...form, pickupAddress: e.target.value })}
              />
              <label className="space-y-1 text-sm text-primary">
                Operating district
                <select
                  className="block w-full rounded-lg border border-border-subtle bg-card p-2"
                  value={form.operatingDistrict}
                  onChange={(e) =>
                    setForm({ ...form, operatingDistrict: e.target.value as SriLankanDistrict })
                  }
                >
                  {districts.map((d) => (
                    <option key={d} value={d}>
                      {d.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </label>
            </fieldset>
            <Button type="submit" loading={busy} disabled={busy}>
              Submit application
            </Button>
          </form>
        </SurfaceCard>
      )}
    </main>
  );
}
export default MerchantRegistrationPage;
