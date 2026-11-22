import { useEffect, useId, useState, type FormEvent, type ReactNode } from 'react';
import { PHONE_HINT, phoneDigits } from '@/utils/phone';
import { useNavigate } from 'react-router-dom';
import { Field } from '@/design-system/primitives/Field';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { SelectDropdown } from '@/design-system/components/SelectDropdown/SelectDropdown';
import {
  merchantApi,
  type MerchantApprovalResponse,
  type SriLankanDistrict
} from '@/services/api/merchantApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { useAuthStore } from '@/store/useAuthStore';
import {
  districtsOf,
  provinceOf,
  provinceOptions,
  type ProvinceId
} from '@/features/customer/components/booking/bookingForm';
import {
  BUSINESS_CATEGORIES,
  emptyApplication,
  FIELD_ORDER,
  fromApplication,
  MONTHLY_VOLUMES,
  toRequest,
  validateApplication,
  validateField,
  type ApplicationErrors,
  type ApplicationField,
  type ApplicationForm
} from '../components/merchantApplication';

const fieldId = (field: ApplicationField) => `merchant-${field}`;

function Section({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="space-y-4">
      <h2 id={headingId} className="text-h3 text-primary">
        {title}
      </h2>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </section>
  );
}

/**
 * Step 2 of a merchant sign-up: the business details, reviewed by the dispatcher of the pickup
 * district. After a rejection the same page shows the reason, pre-filled, so the merchant corrects the
 * details and submits again.
 */
export function MerchantRegistrationPage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const [form, setForm] = useState<ApplicationForm>(() =>
    emptyApplication(user?.email ?? '', (user?.district as SriLankanDistrict | undefined) ?? '')
  );
  const [errors, setErrors] = useState<ApplicationErrors>({});
  const [touched, setTouched] = useState<Partial<Record<ApplicationField, boolean>>>({});
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
          setForm(fromApplication(application));
        }
      })
      .catch(
        (err) => !cancelled && setError(apiErrorMessage(err, 'Could not load your application.'))
      )
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  /** Re-checks a touched field as it changes; the registration number and link depend on each other. */
  const revalidate = (next: ApplicationForm, field: ApplicationField, touchedNow = touched) => {
    const related: ApplicationField[] =
      field === 'businessRegNo' || field === 'websiteUrl'
        ? ['businessRegNo', 'websiteUrl']
        : [field];
    setErrors((current) => {
      const updated = { ...current };
      for (const f of related) {
        if (touchedNow[f] || f === field) updated[f] = validateField(f, next);
      }
      return updated;
    });
  };

  const change = (field: ApplicationField, value: string) => {
    const next = { ...form, [field]: value } as ApplicationForm;
    setForm(next);
    if (touched[field] || errors[field]) revalidate(next, field);
    else if (field === 'businessRegNo' && errors.websiteUrl) revalidate(next, field);
  };

  const blur = (field: ApplicationField) => {
    const nextTouched = { ...touched, [field]: true };
    setTouched(nextTouched);
    revalidate(form, field, nextTouched);
  };

  /** A dropdown choice is final, so it is checked straight away. */
  const pick = (field: ApplicationField, value: string) => {
    const next = { ...form, [field]: value } as ApplicationForm;
    const nextTouched = { ...touched, [field]: true };
    setForm(next);
    setTouched(nextTouched);
    revalidate(next, field, nextTouched);
  };

  /** A district from another province no longer fits, so it is cleared to be chosen again. */
  const pickProvince = (province: ProvinceId) => {
    const keep = form.operatingDistrict && provinceOf(form.operatingDistrict) === province;
    const next = {
      ...form,
      province,
      operatingDistrict: keep ? form.operatingDistrict : ''
    } as ApplicationForm;
    setForm(next);
    setTouched({ ...touched, province: true });
    setErrors((current) => ({
      ...current,
      province: undefined,
      operatingDistrict: keep ? current.operatingDistrict : undefined
    }));
  };

  const text = (field: ApplicationField) => ({
    id: fieldId(field),
    value: form[field],
    error: errors[field],
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => change(field, event.target.value),
    onBlur: () => blur(field)
  });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy) return;
    const found = validateApplication(form);
    setErrors(found);
    setTouched(Object.fromEntries(FIELD_ORDER.map((f) => [f, true])));
    const first = FIELD_ORDER.find((f) => found[f]);
    if (first) {
      setError('');
      document.getElementById(fieldId(first))?.focus();
      return;
    }
    setBusy(true);
    setError('');
    try {
      await merchantApi.register(toRequest(form));
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
          The dispatcher for your pickup district reviews this.
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
        <form onSubmit={submit} noValidate className="space-y-8">
          <fieldset disabled={busy} className="space-y-8">
            <legend className="sr-only">Business details</legend>

            <Section title="Your business">
              <Field
                label="Business name"
                required
                maxLength={150}
                autoComplete="organization"
                className="sm:col-span-2"
                {...text('businessName')}
              />
              <SelectDropdown
                id={fieldId('businessCategory')}
                label="What do you sell?"
                required
                placeholder="Choose a category"
                options={BUSINESS_CATEGORIES}
                value={form.businessCategory || undefined}
                error={errors.businessCategory}
                onChange={(value) => pick('businessCategory', value)}
              />
              <SelectDropdown
                id={fieldId('monthlyParcelVolume')}
                label="Parcels a month"
                required
                placeholder="Choose a rough number"
                options={MONTHLY_VOLUMES}
                value={form.monthlyParcelVolume || undefined}
                error={errors.monthlyParcelVolume}
                onChange={(value) => pick('monthlyParcelVolume', value)}
              />
              <Field
                label="Business registration number"
                maxLength={64}
                hint="Leave empty if your business is not registered"
                {...text('businessRegNo')}
              />
              <Field
                label="Website or social media page"
                maxLength={255}
                inputMode="url"
                autoComplete="url"
                hint="A link such as facebook.com/yourshop. Required if you have no registration number"
                {...text('websiteUrl')}
              />
            </Section>

            <Section title="Contact">
              <Field
                label="Business email"
                required
                maxLength={100}
                inputMode="email"
                autoComplete="email"
                {...text('contactEmail')}
              />
              <Field
                label="Business phone"
                required
                variant="phone"
                inputMode="numeric"
                autoComplete="tel-national"
                hint={PHONE_HINT}
                {...text('contactPhone')}
                onChange={(event) =>
                  change('contactPhone', phoneDigits(event.target.value))
                }
              />
            </Section>

            <Section title="Pickup location">
              <Field
                label="Pickup address"
                required
                maxLength={255}
                autoComplete="address-line1"
                hint="Where our couriers collect your parcels"
                className="sm:col-span-2"
                {...text('pickupAddress')}
              />
              <Field
                label="City"
                required
                maxLength={100}
                autoComplete="address-level2"
                {...text('pickupCity')}
              />
              <Field
                label="Postal code"
                required
                inputMode="numeric"
                maxLength={5}
                autoComplete="postal-code"
                hint="5 digits"
                {...text('pickupPostalCode')}
                onChange={(event) =>
                  change('pickupPostalCode', event.target.value.replace(/\D/g, '').slice(0, 5))
                }
              />
              <SelectDropdown
                id={fieldId('province')}
                label="Pickup province"
                required
                placeholder="Choose a province"
                options={provinceOptions}
                value={form.province || undefined}
                error={errors.province}
                onChange={(value) => pickProvince(value as ProvinceId)}
              />
              <SelectDropdown
                id={fieldId('operatingDistrict')}
                label="Pickup district"
                required
                searchable
                disabled={!form.province}
                placeholder={form.province ? 'Choose a district' : 'Choose a province first'}
                hint="This district's dispatcher reviews your application"
                options={districtsOf(form.province)}
                value={form.operatingDistrict || undefined}
                error={errors.operatingDistrict}
                onChange={(value) => pick('operatingDistrict', value)}
              />
            </Section>
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
