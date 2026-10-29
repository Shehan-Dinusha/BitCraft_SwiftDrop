import { useState, useEffect } from 'react';
import {
  Button,
  Input,
  Field,
  SurfaceCard,
  StatusBanner,
  SelectDropdown,
  type SelectOption
} from '@/design-system';
import {
  orderApi,
  type CreateOrderRequest,
  type FeeEstimateResponse,
  type CustomerOrderResponse,
  type SriLankanDistrict
} from '@/services/api/orderApi';
import {
  Package,
  MapPin,
  User,
  CheckCircle2,
  FileDown,
  ArrowRight,
  ArrowLeft,
  Sparkles
} from 'lucide-react';

const SRI_LANKAN_DISTRICTS: SelectOption[] = [
  { value: 'COLOMBO', label: 'Colombo' },
  { value: 'GAMPAHA', label: 'Gampaha' },
  { value: 'KALUTARA', label: 'Kalutara' },
  { value: 'KANDY', label: 'Kandy' },
  { value: 'MATALE', label: 'Matale' },
  { value: 'NUWARA_ELIYA', label: 'Nuwara Eliya' },
  { value: 'GALLE', label: 'Galle' },
  { value: 'MATARA', label: 'Matara' },
  { value: 'HAMBANTOTA', label: 'Hambantota' },
  { value: 'JAFFNA', label: 'Jaffna' },
  { value: 'KILINOCHCHI', label: 'Kilinochchi' },
  { value: 'MANNAR', label: 'Mannar' },
  { value: 'VAVUNIYA', label: 'Vavuniya' },
  { value: 'MULLAITIVU', label: 'Mullaitivu' },
  { value: 'BATTICALOA', label: 'Batticaloa' },
  { value: 'AMPARA', label: 'Ampara' },
  { value: 'TRINCOMALEE', label: 'Trincomalee' },
  { value: 'KURUNEGALA', label: 'Kurunegala' },
  { value: 'PUTTALAM', label: 'Puttalam' },
  { value: 'ANURADHAPURA', label: 'Anuradhapura' },
  { value: 'POLONNARUWA', label: 'Polonnaruwa' },
  { value: 'BADULLA', label: 'Badulla' },
  { value: 'MONARAGALA', label: 'Monaragala' },
  { value: 'RATNAPURA', label: 'Ratnapura' },
  { value: 'KEGALLE', label: 'Kegalle' }
];

interface WizardProps {
  onOrderCreated?: (order: CustomerOrderResponse) => void;
}

export function OrderBookingWizard({ onOrderCreated }: WizardProps) {
  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);

  // Form State
  const [formData, setFormData] = useState<CreateOrderRequest>({
    pickupContactName: '',
    pickupContactPhone: '',
    pickupAddressLine1: '',
    pickupAddressLine2: '',
    pickupCity: '',
    pickupDistrict: 'COLOMBO' as SriLankanDistrict,
    pickupPostalCode: '',
    recipientName: '',
    recipientPhone: '',
    recipientEmail: '',
    deliveryAddressLine1: '',
    deliveryAddressLine2: '',
    deliveryCity: '',
    deliveryDistrict: 'KANDY' as SriLankanDistrict,
    deliveryPostalCode: '',
    lengthCm: 25,
    widthCm: 20,
    heightCm: 10,
    actualWeightKg: 1.5,
    speed: 'STANDARD',
    itemDescription: '',
    declaredValue: 0,
    codAmount: 0,
    prohibitedItemsDeclared: false
  });

  // Quotation & Feedback State
  const [estimate, setEstimate] = useState<FeeEstimateResponse | null>(null);
  const [isEstimating, setIsEstimating] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [createdOrder, setCreatedOrder] = useState<CustomerOrderResponse | null>(null);

  // Auto-calculate fee estimate whenever routing, dimensions, weight or speed change
  useEffect(() => {
    if (formData.pickupDistrict && formData.deliveryDistrict && formData.actualWeightKg > 0) {
      let cancelled = false;
      setIsEstimating(true);
      setErrorMsg(null);

      orderApi
        .estimateFee({
          pickupDistrict: formData.pickupDistrict,
          deliveryDistrict: formData.deliveryDistrict,
          actualWeightKg: Number(formData.actualWeightKg),
          lengthCm: Number(formData.lengthCm),
          widthCm: Number(formData.widthCm),
          heightCm: Number(formData.heightCm),
          speed: formData.speed
        })
        .then((res) => {
          if (!cancelled) setEstimate(res);
        })
        .catch((err) => {
          if (!cancelled) {
            console.error('Estimate error', err);
          }
        })
        .finally(() => {
          if (!cancelled) setIsEstimating(false);
        });

      return () => {
        cancelled = true;
      };
    }
  }, [
    formData.pickupDistrict,
    formData.deliveryDistrict,
    formData.actualWeightKg,
    formData.lengthCm,
    formData.widthCm,
    formData.heightCm,
    formData.speed
  ]);

  const updateField = <K extends keyof CreateOrderRequest>(field: K, value: CreateOrderRequest[K]) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const handleNextStep1 = (e: React.FormEvent) => {
    e.preventDefault();
    if (
      !formData.pickupContactName ||
      !formData.pickupContactPhone ||
      !formData.pickupAddressLine1 ||
      !formData.pickupCity ||
      !formData.pickupPostalCode
    ) {
      setErrorMsg('Please complete all required origin fields.');
      return;
    }
    setErrorMsg(null);
    setStep(2);
  };

  const handleNextStep2 = (e: React.FormEvent) => {
    e.preventDefault();
    if (
      !formData.recipientName ||
      !formData.recipientPhone ||
      !formData.recipientEmail ||
      !formData.deliveryAddressLine1 ||
      !formData.deliveryCity ||
      !formData.deliveryPostalCode
    ) {
      setErrorMsg('Please complete all required recipient fields, including valid email.');
      return;
    }
    setErrorMsg(null);
    setStep(3);
  };

  const handleFinalSubmit = async () => {
    if (!formData.prohibitedItemsDeclared) {
      setErrorMsg('You must declare that the parcel does not contain any prohibited items.');
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      const order = await orderApi.createOrder({
        ...formData,
        lengthCm: Number(formData.lengthCm),
        widthCm: Number(formData.widthCm),
        heightCm: Number(formData.heightCm),
        actualWeightKg: Number(formData.actualWeightKg),
        declaredValue: Number(formData.declaredValue || 0),
        codAmount: Number(formData.codAmount || 0)
      });
      setCreatedOrder(order);
      setStep(4);
      if (onOrderCreated) onOrderCreated(order);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setErrorMsg(error.response?.data?.message || error.message || 'Failed to create order');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Step Indicator Header */}
      <div className="flex items-center justify-between border-b border-border-subtle pb-4">
        {[
          { num: 1, label: 'Sender & Pickup' },
          { num: 2, label: 'Recipient & Drop-off' },
          { num: 3, label: 'Package & Quotation' },
          { num: 4, label: 'Waybill Ready' }
        ].map(({ num, label }) => {
          const isActive = step === num;
          const isDone = step > num;
          return (
            <div key={num} className="flex items-center gap-2">
              <span
                className={`grid size-8 place-items-center rounded-full text-xs font-bold transition-all ${
                  isDone
                    ? 'bg-emerald-500 text-white'
                    : isActive
                      ? 'bg-brand-primary text-brand-on shadow-md ring-2 ring-brand-primary/20'
                      : 'bg-elevated text-secondary'
                }`}
              >
                {isDone ? <CheckCircle2 className="size-4" /> : num}
              </span>
              <span
                className={`hidden text-xs font-semibold sm:inline ${
                  isActive ? 'text-primary' : 'text-secondary'
                }`}
              >
                {label}
              </span>
            </div>
          );
        })}
      </div>

      {errorMsg && (
        <StatusBanner
          tone="danger"
          title="Validation Notice"
        >
          {errorMsg}
        </StatusBanner>
      )}

      {/* STEP 1: SENDER & PICKUP DETAILS */}
      {step === 1 && (
        <form onSubmit={handleNextStep1} className="space-y-4">
          <div className="flex items-center gap-2 text-primary font-semibold">
            <User className="size-4 text-brand-primary" />
            <span>Pickup & Sender Information</span>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Pickup Contact Name" required>
              <Input
                placeholder="e.g. Sunil Perera"
                value={formData.pickupContactName}
                onChange={(e) => updateField('pickupContactName', e.target.value)}
              />
            </Field>

            <Field label="Pickup Contact Phone" required>
              <Input
                placeholder="+94 77 123 4567"
                value={formData.pickupContactPhone}
                onChange={(e) => updateField('pickupContactPhone', e.target.value)}
              />
            </Field>
          </div>

          <Field label="Address Line 1" required>
            <Input
              placeholder="Building, street name"
              value={formData.pickupAddressLine1}
              onChange={(e) => updateField('pickupAddressLine1', e.target.value)}
            />
          </Field>

          <Field label="Address Line 2 (Optional)">
            <Input
              placeholder="Apartment, suite, or landmark"
              value={formData.pickupAddressLine2 || ''}
              onChange={(e) => updateField('pickupAddressLine2', e.target.value)}
            />
          </Field>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="City" required>
              <Input
                placeholder="Colombo 03"
                value={formData.pickupCity}
                onChange={(e) => updateField('pickupCity', e.target.value)}
              />
            </Field>

            <Field label="District" required>
              <SelectDropdown
                options={SRI_LANKAN_DISTRICTS}
                value={formData.pickupDistrict}
                onChange={(val) => updateField('pickupDistrict', val as SriLankanDistrict)}
              />
            </Field>

            <Field label="Postal Code" required>
              <Input
                placeholder="00300"
                value={formData.pickupPostalCode}
                onChange={(e) => updateField('pickupPostalCode', e.target.value)}
              />
            </Field>
          </div>

          <div className="flex justify-end pt-4">
            <Button type="submit" variant="primary" rightIcon={<ArrowRight className="size-4" />}>
              Continue to Recipient
            </Button>
          </div>
        </form>
      )}

      {/* STEP 2: RECIPIENT & DELIVERY DETAILS */}
      {step === 2 && (
        <form onSubmit={handleNextStep2} className="space-y-4">
          <div className="flex items-center gap-2 text-primary font-semibold">
            <MapPin className="size-4 text-brand-primary" />
            <span>Recipient & Delivery Destination</span>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Recipient Full Name" required>
              <Input
                placeholder="e.g. Anura Fernando"
                value={formData.recipientName}
                onChange={(e) => updateField('recipientName', e.target.value)}
              />
            </Field>

            <Field label="Recipient Phone" required>
              <Input
                placeholder="+94 71 987 6543"
                value={formData.recipientPhone}
                onChange={(e) => updateField('recipientPhone', e.target.value)}
              />
            </Field>
          </div>

          <Field label="Recipient Email (Required for notifications)" required>
            <Input
              placeholder="recipient@example.com"
              value={formData.recipientEmail}
              onChange={(e) => updateField('recipientEmail', e.target.value)}
            />
          </Field>

          <Field label="Delivery Address Line 1" required>
            <Input
              placeholder="No. 18, Peradeniya Road"
              value={formData.deliveryAddressLine1}
              onChange={(e) => updateField('deliveryAddressLine1', e.target.value)}
            />
          </Field>

          <Field label="Delivery Address Line 2 (Optional)">
            <Input
              placeholder="Floor, building, or landmark"
              value={formData.deliveryAddressLine2 || ''}
              onChange={(e) => updateField('deliveryAddressLine2', e.target.value)}
            />
          </Field>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Destination City" required>
              <Input
                placeholder="Kandy"
                value={formData.deliveryCity}
                onChange={(e) => updateField('deliveryCity', e.target.value)}
              />
            </Field>

            <Field label="Destination District" required>
              <SelectDropdown
                options={SRI_LANKAN_DISTRICTS}
                value={formData.deliveryDistrict}
                onChange={(val) => updateField('deliveryDistrict', val as SriLankanDistrict)}
              />
            </Field>

            <Field label="Postal Code" required>
              <Input
                placeholder="20000"
                value={formData.deliveryPostalCode}
                onChange={(e) => updateField('deliveryPostalCode', e.target.value)}
              />
            </Field>
          </div>

          <div className="flex justify-between pt-4">
            <Button
              type="button"
              variant="secondary"
              leftIcon={<ArrowLeft className="size-4" />}
              onClick={() => setStep(1)}
            >
              Back
            </Button>
            <Button type="submit" variant="primary" rightIcon={<ArrowRight className="size-4" />}>
              Continue to Package Info
            </Button>
          </div>
        </form>
      )}

      {/* STEP 3: PACKAGE DETAILS, PROHIBITED DECLARATION & LIVE ESTIMATION */}
      {step === 3 && (
        <div className="space-y-6">
          <div className="flex items-center gap-2 text-primary font-semibold">
            <Package className="size-4 text-brand-primary" />
            <span>Package Metrics & Delivery Speed</span>
          </div>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Field label="Length (cm)" required>
              <input
                type="number"
                min="1"
                step="0.5"
                value={formData.lengthCm}
                onChange={(e) => updateField('lengthCm', Number(e.target.value))}
                className="w-full rounded-lg border border-border-subtle bg-card px-3 py-2 text-sm text-primary outline-none focus:border-brand-primary"
              />
            </Field>
            <Field label="Width (cm)" required>
              <input
                type="number"
                min="1"
                step="0.5"
                value={formData.widthCm}
                onChange={(e) => updateField('widthCm', Number(e.target.value))}
                className="w-full rounded-lg border border-border-subtle bg-card px-3 py-2 text-sm text-primary outline-none focus:border-brand-primary"
              />
            </Field>
            <Field label="Height (cm)" required>
              <input
                type="number"
                min="1"
                step="0.5"
                value={formData.heightCm}
                onChange={(e) => updateField('heightCm', Number(e.target.value))}
                className="w-full rounded-lg border border-border-subtle bg-card px-3 py-2 text-sm text-primary outline-none focus:border-brand-primary"
              />
            </Field>
            <Field label="Weight (kg)" required>
              <input
                type="number"
                min="0.1"
                step="0.1"
                value={formData.actualWeightKg}
                onChange={(e) => updateField('actualWeightKg', Number(e.target.value))}
                className="w-full rounded-lg border border-border-subtle bg-card px-3 py-2 text-sm text-primary outline-none focus:border-brand-primary"
              />
            </Field>
          </div>

          {/* Speed Selector */}
          <div className="space-y-2">
            <label className="text-sm font-semibold text-primary">Delivery Speed</label>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <button
                type="button"
                onClick={() => updateField('speed', 'STANDARD')}
                className={`flex items-start gap-3 rounded-xl border p-4 text-left transition-all ${
                  formData.speed === 'STANDARD'
                    ? 'border-brand-primary bg-brand-subtle ring-2 ring-brand-primary/20'
                    : 'border-border-subtle bg-elevated/40 hover:bg-elevated'
                }`}
              >
                <div className="mt-0.5 size-4 rounded-full border border-brand-primary flex items-center justify-center">
                  {formData.speed === 'STANDARD' && (
                    <div className="size-2 rounded-full bg-brand-primary" />
                  )}
                </div>
                <div>
                  <div className="font-semibold text-primary">STANDARD Delivery</div>
                  <div className="text-xs text-secondary">1–2 business days island-wide</div>
                </div>
              </button>

              <button
                type="button"
                onClick={() => updateField('speed', 'EXPRESS')}
                className={`flex items-start gap-3 rounded-xl border p-4 text-left transition-all ${
                  formData.speed === 'EXPRESS'
                    ? 'border-amber-500 bg-amber-500/10 ring-2 ring-amber-500/20'
                    : 'border-border-subtle bg-elevated/40 hover:bg-elevated'
                }`}
              >
                <div className="mt-0.5 size-4 rounded-full border border-amber-500 flex items-center justify-center">
                  {formData.speed === 'EXPRESS' && (
                    <div className="size-2 rounded-full bg-amber-500" />
                  )}
                </div>
                <div>
                  <div className="flex items-center gap-1.5 font-semibold text-primary">
                    <span>EXPRESS Priority</span>
                    <Sparkles className="size-3.5 text-amber-500" />
                  </div>
                  <div className="text-xs text-secondary">Next-day expedited transit (1.5x)</div>
                </div>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Merchandise Description">
              <Input
                placeholder="e.g. Documents, Tea, Clothing"
                value={formData.itemDescription || ''}
                onChange={(e) => updateField('itemDescription', e.target.value)}
              />
            </Field>
            <Field label="Cash On Delivery (COD Amount in LKR)">
              <input
                type="number"
                min="0"
                step="100"
                placeholder="0.00"
                value={formData.codAmount || 0}
                onChange={(e) => updateField('codAmount', Number(e.target.value))}
                className="w-full rounded-lg border border-border-subtle bg-card px-3 py-2 text-sm text-primary outline-none focus:border-brand-primary"
              />
            </Field>
          </div>

          {/* LIVE FEE ESTIMATE CARD (FR-C2) */}
          <SurfaceCard className="border-border-subtle bg-slate-900/5 p-4 rounded-xl">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-xs font-semibold uppercase tracking-wider text-secondary">
                  Instant Tariff Estimate (FR-C2)
                </span>
                <div className="text-2xl font-black text-primary">
                  {isEstimating ? (
                    <span className="text-sm font-medium text-secondary">Calculating quote...</span>
                  ) : estimate ? (
                    `LKR ${estimate.estimatedFee?.toFixed(2)}`
                  ) : (
                    '—'
                  )}
                </div>
              </div>
              {estimate && (
                <div className="text-right text-xs text-secondary">
                  <div>Billable Wt: <span className="font-semibold text-primary">{estimate.chargeableWeightKg} kg</span></div>
                  <div>Base: LKR {estimate.baseFee} + Extra: LKR {estimate.weightFee}</div>
                  <div className="text-[10px] text-muted">Rule: {estimate.pricingRuleVersionId}</div>
                </div>
              )}
            </div>
          </SurfaceCard>

          {/* PROHIBITED ITEMS DECLARATION (FR-C1) */}
          <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-4">
            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={formData.prohibitedItemsDeclared}
                onChange={(e) => updateField('prohibitedItemsDeclared', e.target.checked)}
                className="mt-1 size-4 rounded border-border-subtle text-brand-primary focus:ring-brand-primary"
              />
              <div className="text-xs text-primary leading-relaxed">
                <span className="font-semibold text-amber-600">
                  Mandatory Legal Declaration:
                </span>{' '}
                I hereby declare that this parcel contains no prohibited, restricted, flammable, explosive, or toxic items under the laws of Sri Lanka and SwiftDrop terms of carriage. I agree to digital inspection at pickup.
              </div>
            </label>
          </div>

          <div className="flex justify-between pt-4">
            <Button
              type="button"
              variant="secondary"
              leftIcon={<ArrowLeft className="size-4" />}
              onClick={() => setStep(2)}
            >
              Back
            </Button>
            <Button
              type="button"
              variant="primary"
              disabled={isSubmitting || !formData.prohibitedItemsDeclared}
              rightIcon={<CheckCircle2 className="size-4" />}
              onClick={handleFinalSubmit}
            >
              {isSubmitting ? 'Booking Order...' : 'Confirm & Book Delivery'}
            </Button>
          </div>
        </div>
      )}

      {/* STEP 4: SUCCESS & INSTANT PDF WAYBILL ACCESS (FR-C4, FR-C5) */}
      {step === 4 && createdOrder && (
        <div className="space-y-6 text-center py-6">
          <div className="mx-auto grid size-16 place-items-center rounded-full bg-emerald-500/10 text-emerald-500">
            <CheckCircle2 className="size-8" />
          </div>

          <div className="space-y-2">
            <h2 className="text-2xl font-bold text-primary">Booking Confirmed!</h2>
            <p className="text-sm text-secondary">
              Your parcel has been queued for courier pickup.
            </p>
            <div className="inline-block rounded-lg bg-brand-subtle px-4 py-2 font-mono text-xl font-black text-brand-primary tracking-wider">
              {createdOrder.trackingNumber}
            </div>
          </div>

          <div className="mx-auto max-w-md rounded-xl border border-border-subtle bg-elevated/40 p-4 text-left text-xs space-y-2">
            <div className="flex justify-between">
              <span className="text-secondary">Pickup:</span>
              <span className="font-semibold text-primary">{createdOrder.pickupCity}, {createdOrder.pickupDistrict}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-secondary">Destination:</span>
              <span className="font-semibold text-primary">{createdOrder.deliveryCity}, {createdOrder.deliveryDistrict}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-secondary">Speed:</span>
              <span className="font-semibold text-primary">{createdOrder.orderSpeed}</span>
            </div>
            <div className="flex justify-between border-t border-border-subtle pt-2">
              <span className="font-bold text-primary">Estimated Delivery Fee:</span>
              <span className="font-bold text-primary">LKR {createdOrder.estimatedFee?.toFixed(2)}</span>
            </div>
          </div>

          {/* Action Buttons: 2-page A5 PDF download */}
          <div className="flex flex-col gap-3 sm:flex-row sm:justify-center">
            <a
              href={orderApi.getShippingLabelUrl(createdOrder.orderId as string)}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center justify-center gap-2 rounded-lg bg-brand-primary px-5 py-2.5 text-sm font-semibold text-brand-on shadow transition-all hover:bg-brand-primary/90"
            >
              <FileDown className="size-4" />
              Download A5 Shipping Label & Receipt PDF (FR-C4/C5)
            </a>

            <Button
              variant="secondary"
              onClick={() => {
                setStep(1);
                setCreatedOrder(null);
              }}
            >
              Book Another Parcel
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
