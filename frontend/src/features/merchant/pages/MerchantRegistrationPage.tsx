import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
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
import { SRI_LANKAN_DISTRICTS } from '@/services/api/authApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { useAuthStore } from '@/store/useAuthStore';

function districtName(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

/**
 * Step 2 of a merchant sign-up: the business details, reviewed by the dispatcher of the pickup
 * district. After a rejection the same page shows the reason, pre-filled, so the merchant corrects the
 * details and submits again.
 */
export function MerchantRegistrationPage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [form, setForm] = useState<RegisterMerchantRequest>({
    businessName: '',
    businessRegNo: '',
    contactEmail: user?.email ?? '',
    contactPhone: '',
    pickupAddress: '',
    operatingDistrict: (user?.district as SriLankanDistrict) ?? 'COLOMBO'
  });
  const [previous, setPrevious] = useState<MerchantApprovalResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    merchantApi
      .myApplication()
      .then((application) => {
        if (cancelled) return;
        // Under review or already registered: the portal shows the status instead.
        if (application && application.status !== 'REJECTED') {
          navigate('/merchant', { replace: true });
          return;
        }
        if (application) {
          setPrevious(application);
          setForm({
            businessName: application.businessName ?? '',
            businessRegNo: application.businessRegNo ?? '',
            contactEmail: application.contactEmail ?? '',
            contactPhone: application.contactPhone ?? '',
            pickupAddress: application.pickupAddress ?? '',
            operatingDistrict: application.operatingDistrict ?? 'COLOMBO'
          });
        }
      })
      .catch((err) => !cancelled && setError(apiErrorMessage(err, 'Could not load your application.')))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [navigate]);

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
      await merchantApi.register(request);
      navigate('/merchant', { replace: true });
    } catch (err) {
      setError(apiErrorMessage(err, 'Unable to submit your business application.'));
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <main className="mx-auto max-w-3xl p-4 md:p-6" data-density="compact">
        <p role="status" className="py-12 text-center text-sm text-secondary">
          Loading your application…
        </p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-4 md:p-6" data-density="compact">
      <header>
        <p className="text-xs font-semibold uppercase tracking-widest text-brand-primary">
          {previous ? 'Resubmit your application' : 'Step 2 of 2'}
        </p>
        <h1 className="text-h1 text-primary">Tell us about your business</h1>
        <p className="text-sm text-secondary">
          The SwiftDrop team of your pickup district reviews these details. Once approved you can create API
          keys and send orders from your own system.
        </p>
      </header>
      {previous?.rejectionReason && (
        <StatusBanner tone="warning" title="Your application needs changes">
          {previous.rejectionReason} Correct the details below and submit again.
        </StatusBanner>
      )}
      {error && (
        <StatusBanner tone="danger" title="Application not submitted">
          {error}
        </StatusBanner>
      )}
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
              hint="As on your business registration certificate"
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
              hint="Where our couriers collect your parcels"
              value={form.pickupAddress}
              onChange={(e) => setForm({ ...form, pickupAddress: e.target.value })}
            />
            <label className="space-y-1 text-sm font-semibold text-primary">
              Pickup district
              <select
                className="block h-10 w-full rounded-lg border border-border-subtle bg-card px-2 font-normal"
                value={form.operatingDistrict}
                onChange={(e) =>
                  setForm({ ...form, operatingDistrict: e.target.value as SriLankanDistrict })
                }
              >
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {districtName(d)}
                  </option>
                ))}
              </select>
              <span className="block text-xs font-normal text-secondary">
                This district&apos;s team reviews your application
              </span>
            </label>
          </fieldset>
          <Button type="submit" loading={busy} disabled={busy}>
            {previous ? 'Resubmit application' : 'Submit application'}
          </Button>
        </form>
      </SurfaceCard>
    </main>
  );
}
export default MerchantRegistrationPage;
