import { useEffect, useId, useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronDown } from 'lucide-react';
import { Field } from '@/design-system/primitives/Field';
import { Button } from '@/design-system/primitives/Button';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes } from '@/design-system/tokens/density';
import {
  merchantApi,
  type MerchantApprovalResponse,
  type SriLankanDistrict
} from '@/services/api/merchantApi';
import { SRI_LANKAN_DISTRICTS } from '@/services/api/authApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { useAuthStore } from '@/store/useAuthStore';
import { normalisePhoneDigits } from '@/features/customer/components/booking/bookingForm';
import { cn } from '@/utils/cn';
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

function districtName(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

const fieldId = (field: ApplicationField) => `merchant-${field}`;

/** A native select styled like the design-system `Field`, with the same label, hint and error slots. */
function SelectField({
  id,
  label,
  value,
  onChange,
  onBlur,
  error,
  hint,
  placeholder,
  options
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  error?: string;
  hint?: string;
  placeholder: string;
  options: { value: string; label: string }[];
}) {
  const recipe = densityRecipes[useDensityMode()];
  const messageId = `${id}-message`;
  return (
    <div className="flex w-full flex-col gap-2">
      <label htmlFor={id} className={cn('font-semibold text-primary', recipe.caption)}>
        {label} <span className="text-status-danger">*</span>
      </label>
      <div className="relative">
        <select
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onBlur={onBlur}
          aria-invalid={Boolean(error)}
          aria-describedby={error || hint ? messageId : undefined}
          className={cn(
            'block h-10 w-full appearance-none rounded-lg border bg-card pl-3 pr-9 text-sm outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus',
            error ? 'border-status-danger' : 'border-border-subtle',
            value ? 'text-primary' : 'text-muted'
          )}
        >
          <option value="" disabled>
            {placeholder}
          </option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown
          aria-hidden="true"
          className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted"
        />
      </div>
      {error ? (
        <span id={messageId} className={cn('text-status-danger', recipe.caption)}>
          {error}
        </span>
      ) : hint ? (
        <span id={messageId} className={cn('text-secondary', recipe.caption)}>
          {hint}
        </span>
      ) : null}
    </div>
  );
}

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
              <SelectField
                id={fieldId('businessCategory')}
                label="What do you sell?"
                placeholder="Choose a category"
                options={BUSINESS_CATEGORIES}
                value={form.businessCategory}
                error={errors.businessCategory}
                onChange={(value) => change('businessCategory', value)}
                onBlur={() => blur('businessCategory')}
              />
              <SelectField
                id={fieldId('monthlyParcelVolume')}
                label="Parcels a month"
                placeholder="Choose a rough number"
                options={MONTHLY_VOLUMES}
                value={form.monthlyParcelVolume}
                error={errors.monthlyParcelVolume}
                onChange={(value) => change('monthlyParcelVolume', value)}
                onBlur={() => blur('monthlyParcelVolume')}
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
                placeholder="facebook.com/yourshop"
                hint="Required if you have no registration number"
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
                placeholder="77 123 4567"
                {...text('contactPhone')}
                onChange={(event) =>
                  change('contactPhone', normalisePhoneDigits(event.target.value))
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
                placeholder="10250"
                {...text('pickupPostalCode')}
                onChange={(event) =>
                  change('pickupPostalCode', event.target.value.replace(/\D/g, '').slice(0, 5))
                }
              />
              <SelectField
                id={fieldId('operatingDistrict')}
                label="Pickup district"
                placeholder="Choose a district"
                hint="This district's dispatcher reviews your application"
                options={SRI_LANKAN_DISTRICTS.map((d) => ({ value: d, label: districtName(d) }))}
                value={form.operatingDistrict}
                error={errors.operatingDistrict}
                onChange={(value) => change('operatingDistrict', value)}
                onBlur={() => blur('operatingDistrict')}
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
