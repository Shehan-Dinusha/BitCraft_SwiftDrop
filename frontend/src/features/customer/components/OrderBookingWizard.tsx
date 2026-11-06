import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, CheckCircle2 } from 'lucide-react';
import { Alert, Button } from '@/design-system';
import { orderApi, readOrderApiError, type CustomerOrderResponse } from '@/services/api/orderApi';
import {
  initialBookingForm,
  toCreateOrderRequest,
  toFeeEstimateRequest,
  type BookingField,
  type BookingForm
} from './booking/bookingForm';
import {
  firstInvalidStep,
  parseServerFieldErrors,
  stepOfField,
  validateField,
  validateStep,
  type BookingErrors,
  type BookingStepId
} from './booking/validation';
import { useFeeEstimate } from './booking/useFeeEstimate';
import { StepIndicator, type BookingStepDef } from './booking/StepIndicator';
import { PickupStep } from './booking/PickupStep';
import { DropoffStep } from './booking/DropoffStep';
import { ParcelStep } from './booking/ParcelStep';
import { ReviewStep } from './booking/ReviewStep';
import { BookingSuccess } from './booking/BookingSuccess';

const STEPS: BookingStepDef[] = [
  { id: 'pickup', label: 'Pickup' },
  { id: 'dropoff', label: 'Drop-off' },
  { id: 'parcel', label: 'Parcel' },
  { id: 'review', label: 'Review' }
];

interface WizardProps {
  onOrderCreated?: (order: CustomerOrderResponse) => void;
  /** Opens the customer's parcel list. Defaults to the `/customer/orders` page. */
  onViewOrders?: () => void;
}

type SubmitProblem = 'validation' | 'invalid' | 'pricing' | 'session' | 'failed';

const SUBMIT_COPY: Record<SubmitProblem, { title: string; message: string }> = {
  validation: {
    title: 'Some details need fixing',
    message: 'We took you to the first field that needs a change.'
  },
  invalid: {
    title: 'Some details need fixing',
    message: 'Check your details and try again.'
  },
  pricing: {
    title: 'Pricing is temporarily unavailable',
    message: 'Your parcel was not booked. Try again in a moment.'
  },
  session: {
    title: 'Please sign in again',
    message: 'Your session ended, so the parcel was not booked. Sign in and book again.'
  },
  failed: {
    title: "We couldn't book your parcel",
    // The request may have reached the server (e.g. a timeout), so do not promise nothing was booked.
    message: 'Check My parcels before trying again, in case the booking went through.'
  }
};

/**
 * Four-step booking: pickup → drop-off → parcel → review (with the live fee), then the result. One
 * form object holds every value, so moving between steps never loses data.
 */
export function OrderBookingWizard({ onOrderCreated, onViewOrders }: WizardProps) {
  const navigate = useNavigate();
  const [form, setForm] = useState<BookingForm>(initialBookingForm);
  const [step, setStep] = useState<BookingStepId>('pickup');
  const [completed, setCompleted] = useState<Set<BookingStepId>>(new Set());
  const [errors, setErrors] = useState<BookingErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitProblem, setSubmitProblem] = useState<SubmitProblem | null>(null);
  const [createdOrder, setCreatedOrder] = useState<CustomerOrderResponse | null>(null);
  const [quotedFee, setQuotedFee] = useState<number | undefined>();
  const [focusRequest, setFocusRequest] = useState<'invalid' | 'top' | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const submittingRef = useRef(false);

  // Only price the parcel on the review step, and only once the inputs the fee depends on are valid.
  const estimateRequest = useMemo(() => {
    if (step !== 'review' || !form.pickupDistrict || !form.deliveryDistrict) return null;
    const parcelErrors = validateStep('parcel', form);
    return Object.keys(parcelErrors).length === 0 ? toFeeEstimateRequest(form) : null;
  }, [step, form]);
  const {
    estimate,
    status: estimateStatus,
    retry: retryEstimate
  } = useFeeEstimate(estimateRequest);

  useEffect(() => {
    if (!focusRequest || !containerRef.current) return;
    const root = containerRef.current;
    const target =
      focusRequest === 'invalid'
        ? root.querySelector<HTMLElement>('[aria-invalid="true"]')
        : root.querySelector<HTMLElement>('[data-autofocus], legend, h2');
    if (target) {
      if (!target.matches('input, select, textarea, button, [tabindex]')) target.tabIndex = -1;
      target.focus({ preventScroll: true });
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      target.scrollIntoView({ block: 'center', behavior: reduceMotion ? 'auto' : 'smooth' });
    }
    setFocusRequest(null);
  }, [focusRequest, step, errors, createdOrder]);

  const updateField = <K extends BookingField>(field: K, value: BookingForm[K]) => {
    setSubmitProblem(null);
    // Functional update: the size field changes length, width and height in one event.
    setForm((previous) => ({ ...previous, [field]: value }));
    // Clear a shown error as soon as the value is fixed; new errors wait for blur or Next.
    // Each rule reads only its own field, so checking against `form` plus the new value is enough.
    // Postal codes have no blur event (SpecialField), so check them once all 5 digits are in.
    const complete = field.endsWith('PostalCode') && String(value).length === 5;
    setErrors((current) => {
      if (field === 'collectCash' && !value) return { ...current, codAmount: undefined };
      return current[field] || complete
        ? { ...current, [field]: validateField(field, { ...form, [field]: value }) }
        : current;
    });
  };

  const touchField = (field: BookingField) => {
    setErrors((current) => {
      const value = form[field];
      if (value === '' && !current[field]) return current;
      return { ...current, [field]: validateField(field, form) };
    });
  };

  const goTo = (target: BookingStepId) => {
    setSubmitProblem(null);
    setStep(target);
    setFocusRequest('top');
  };

  const handleNext = (event: FormEvent) => {
    event.preventDefault();
    const stepErrors = validateStep(step, form);
    if (Object.keys(stepErrors).length > 0) {
      setErrors((current) => ({ ...current, ...stepErrors }));
      setFocusRequest('invalid');
      return;
    }
    setCompleted((current) => new Set(current).add(step));
    const index = STEPS.findIndex((item) => item.id === step);
    if (step === 'review') {
      void submit();
    } else {
      goTo(STEPS[index + 1].id);
    }
  };

  const handleBack = () => {
    const index = STEPS.findIndex((item) => item.id === step);
    if (index > 0) goTo(STEPS[index - 1].id);
  };

  const submit = async () => {
    if (submittingRef.current) return;
    const invalidStep = firstInvalidStep(form);
    if (invalidStep) {
      setErrors((current) => ({ ...current, ...validateStep(invalidStep, form) }));
      setStep(invalidStep);
      setFocusRequest('invalid');
      return;
    }
    if (estimateStatus !== 'ready') return;

    submittingRef.current = true;
    setSubmitting(true);
    setSubmitProblem(null);
    try {
      const order = await orderApi.createOrder(toCreateOrderRequest(form));
      // The server prices the order again; remember what the customer saw so a change can be shown.
      setQuotedFee(estimate?.estimatedFee);
      setCreatedOrder(order);
      setFocusRequest('top');
      onOrderCreated?.(order);
    } catch (error) {
      const { status, message } = readOrderApiError(error);
      if (status === 400) {
        const serverErrors = parseServerFieldErrors(message);
        const fields = Object.keys(serverErrors) as BookingField[];
        setErrors((current) => ({ ...current, ...serverErrors }));
        setSubmitProblem(fields.length > 0 ? 'validation' : 'invalid');
        if (fields.length > 0) {
          setStep(stepOfField(fields[0]));
          setFocusRequest('invalid');
        }
      } else if (status === 503 || status === 502) {
        setSubmitProblem('pricing');
      } else if (status === 401) {
        setSubmitProblem('session');
      } else {
        setSubmitProblem('failed');
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const reset = () => {
    setForm(initialBookingForm);
    setErrors({});
    setCompleted(new Set());
    setCreatedOrder(null);
    setSubmitProblem(null);
    setStep('pickup');
    setFocusRequest('top');
  };

  if (createdOrder) {
    return (
      <div ref={containerRef}>
        <BookingSuccess order={createdOrder} quotedFee={quotedFee} onBookAnother={reset} />
      </div>
    );
  }

  const stepProps = { form, errors, onChange: updateField, onBlur: touchField };
  const isFirst = step === 'pickup';
  const isReview = step === 'review';
  const nextLabel =
    step === 'pickup'
      ? 'Continue to drop-off'
      : step === 'dropoff'
        ? 'Continue to parcel'
        : 'Review booking';

  return (
    <div ref={containerRef} className="flex flex-col gap-6">
      <StepIndicator steps={STEPS} current={step} completed={completed} onSelect={goTo} />

      <form noValidate onSubmit={handleNext} className="flex flex-col gap-6">
        {step === 'pickup' ? <PickupStep {...stepProps} /> : null}
        {step === 'dropoff' ? <DropoffStep {...stepProps} /> : null}
        {step === 'parcel' ? <ParcelStep {...stepProps} /> : null}
        {isReview ? (
          <ReviewStep
            form={form}
            errors={errors}
            estimate={estimate}
            estimateStatus={estimateStatus}
            onRetryEstimate={retryEstimate}
            onDeclare={(value) => updateField('prohibitedItemsDeclared', value)}
            onEdit={goTo}
          />
        ) : null}

        {submitProblem ? (
          <Alert
            tone={
              submitProblem === 'validation' || submitProblem === 'invalid' ? 'warning' : 'danger'
            }
            title={SUBMIT_COPY[submitProblem].title}
            action={
              submitProblem === 'session' ? (
                <Button type="button" variant="ghost" onClick={() => navigate('/login')}>
                  Sign in
                </Button>
              ) : submitProblem === 'failed' ? (
                <Button type="button" variant="ghost" onClick={() => (onViewOrders ? onViewOrders() : navigate('/customer/orders'))}>
                  Open My parcels
                </Button>
              ) : undefined
            }
          >
            {SUBMIT_COPY[submitProblem].message}
          </Alert>
        ) : null}

        <div className="flex flex-col-reverse gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:justify-between">
          {isFirst ? (
            <span />
          ) : (
            <Button
              type="button"
              variant="secondary"
              leftIcon={<ArrowLeft />}
              disabled={submitting}
              onClick={handleBack}
            >
              Back
            </Button>
          )}
          <Button
            type="submit"
            rightIcon={isReview ? <CheckCircle2 /> : <ArrowRight />}
            loading={submitting || (isReview && estimateStatus === 'loading')}
            loadingLabel={submitting ? 'Booking your parcel' : 'Getting your price'}
            disabled={isReview && estimateStatus !== 'ready'}
            aria-describedby={
              isReview && (estimateStatus === 'unavailable' || estimateStatus === 'error')
                ? 'confirm-blocked'
                : undefined
            }
          >
            {isReview ? 'Confirm and book' : nextLabel}
          </Button>
        </div>
        {isReview && (estimateStatus === 'unavailable' || estimateStatus === 'error') ? (
          <p id="confirm-blocked" className="-mt-3 text-caption text-secondary sm:text-right">
            You can confirm once the fee is shown.
          </p>
        ) : null}
      </form>
    </div>
  );
}
