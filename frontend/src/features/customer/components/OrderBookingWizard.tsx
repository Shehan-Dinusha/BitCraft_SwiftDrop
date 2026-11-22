import { phoneDigits } from '@/utils/phone';
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ArrowRight, CheckCircle2 } from 'lucide-react';
import { Alert, Button } from '@/design-system';
import { fetchCurrentUser } from '@/services/api/authApi';
import {
  addressBookApi,
  orderApi,
  readOrderApiError,
  type CustomerOrderResponse,
  type SavedAddress
} from '@/services/api/orderApi';
import { useAuthStore } from '@/store/useAuthStore';
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
import { FeePanel, ReviewStep } from './booking/ReviewStep';
import { SPEED_LABEL } from './orders/orderDisplay';
import { BookingSuccess } from './booking/BookingSuccess';
import {
  SaveAddressOption,
  SavedAddressPicker,
  type SaveChoice
} from './booking/SavedAddressPicker';
import {
  applySavedAddress,
  sideFields,
  toSavedAddressRequest,
  validateAddressLabel,
  type AddressSide
} from './booking/savedAddresses';
import { clearDraft, loadDraft, newClientRequestId, saveDraft } from './booking/bookingDraft';

const MAX_SAVED_ADDRESSES = 20;
const NO_SAVE: Record<AddressSide, SaveChoice> = {
  pickup: { save: false, label: '' },
  delivery: { save: false, label: '' }
};
const NO_SELECTION: Record<AddressSide, string | null> = { pickup: null, delivery: null };
const SIDE_OF_STEP: Partial<Record<BookingStepId, AddressSide>> = {
  pickup: 'pickup',
  dropoff: 'delivery'
};

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
  const queryClient = useQueryClient();
  // Drafts belong to a real account; a developer persona has no stable id and keeps nothing.
  const userId = useAuthStore((state) => (state.isDevSession ? null : (state.user?.id ?? null)));
  const [draft] = useState(() => (userId ? loadDraft(userId) : null));
  const [restored, setRestored] = useState(draft !== null);
  const [form, setForm] = useState<BookingForm>(draft?.form ?? initialBookingForm);
  const [step, setStep] = useState<BookingStepId>(draft?.step ?? 'pickup');
  const [completed, setCompleted] = useState<Set<BookingStepId>>(new Set(draft?.completed ?? []));
  const [clientRequestId, setClientRequestId] = useState(
    () => draft?.clientRequestId ?? newClientRequestId()
  );
  const [saveChoice, setSaveChoice] = useState(draft?.saveChoice ?? NO_SAVE);
  const [labelErrors, setLabelErrors] = useState<Partial<Record<AddressSide, string>>>({});
  const [selectedSaved, setSelectedSaved] = useState(draft?.selectedSaved ?? NO_SELECTION);
  const [errors, setErrors] = useState<BookingErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitProblem, setSubmitProblem] = useState<SubmitProblem | null>(null);
  const [createdOrder, setCreatedOrder] = useState<CustomerOrderResponse | null>(null);
  const [quotedFee, setQuotedFee] = useState<number | undefined>();
  const [focusRequest, setFocusRequest] = useState<'invalid' | 'top' | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const submittingRef = useRef(false);

  // Fill in the sender's name and phone from the profile, once, and never over something already typed.
  // The pickup district is not filled: the customer may send from anywhere, and the pickup district
  // decides which dispatcher handles the order. If the profile can't be loaded the form starts empty.
  const profile = useQuery({
    queryKey: ['current-user'],
    queryFn: fetchCurrentUser,
    retry: false,
    staleTime: 5 * 60 * 1000
  });
  const prefilled = useRef(draft !== null);
  useEffect(() => {
    if (!profile.data || prefilled.current) return;
    prefilled.current = true;
    const { firstName, lastName, phone } = profile.data;
    const name = [firstName, lastName].filter(Boolean).join(' ').trim();
    setForm((previous) => ({
      ...previous,
      pickupContactName: previous.pickupContactName || name,
      pickupContactPhone: previous.pickupContactPhone || phoneDigits(phone ?? '')
    }));
  }, [profile.data]);

  const savedAddresses = useQuery({
    queryKey: ['saved-addresses'],
    queryFn: addressBookApi.list,
    enabled: userId !== null,
    retry: false,
    staleTime: 60 * 1000
  });
  const addressList: SavedAddress[] = savedAddresses.data ?? [];

  // Keep the unfinished booking in this browser as it is typed (see bookingDraft.ts).
  useEffect(() => {
    if (!userId || createdOrder) return;
    const timer = window.setTimeout(
      () =>
        saveDraft(userId, {
          form,
          step,
          completed: [...completed],
          clientRequestId,
          selectedSaved,
          saveChoice
        }),
      400
    );
    return () => window.clearTimeout(timer);
  }, [userId, form, step, completed, clientRequestId, selectedSaved, saveChoice, createdOrder]);

  // Price the parcel on the parcel and review steps, once the inputs the fee depends on are valid.
  const estimateRequest = useMemo(() => {
    if ((step !== 'parcel' && step !== 'review') || !form.pickupDistrict || !form.deliveryDistrict)
      return null;
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
    // Changing a field that came from a saved address makes it a new address (which can be saved).
    (['pickup', 'delivery'] as AddressSide[]).forEach((side) => {
      if (selectedSaved[side] && sideFields(side).includes(field)) {
        setSelectedSaved((current) => ({ ...current, [side]: null }));
      }
    });
    // Clear a shown error as soon as the value is fixed; new errors wait for blur or Next.
    // Each rule reads only its own field, so checking against `form` plus the new value is enough.
    // Postal codes have no blur event (SpecialField), so check them once all 5 digits are in.
    const complete = field.endsWith('PostalCode') && String(value).length === 5;
    setErrors((current) => {
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
    const side = SIDE_OF_STEP[step];
    const labelError =
      side && saveChoice[side].save && !selectedSaved[side]
        ? validateAddressLabel(saveChoice[side].label)
        : undefined;
    if (side) setLabelErrors((current) => ({ ...current, [side]: labelError }));
    if (Object.keys(stepErrors).length > 0 || labelError) {
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
      const order = await orderApi.createOrder(toCreateOrderRequest(form, clientRequestId));
      if (userId) clearDraft(userId);
      void saveChosenAddresses();
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

  /** Saves the sides the customer ticked. The order is already placed, so a failure here is not shown. */
  const saveChosenAddresses = async () => {
    const sides = (['pickup', 'delivery'] as AddressSide[]).filter(
      (side) => saveChoice[side].save && !selectedSaved[side]
    );
    if (sides.length === 0) return;
    await Promise.allSettled(
      sides.map((side) =>
        addressBookApi.create(toSavedAddressRequest(form, side, saveChoice[side].label))
      )
    );
    void queryClient.invalidateQueries({ queryKey: ['saved-addresses'] });
  };

  const fillFromSavedAddress = (side: AddressSide, address: SavedAddress) => {
    setSubmitProblem(null);
    setForm((previous) => applySavedAddress(previous, side, address));
    setSelectedSaved((current) => ({ ...current, [side]: address.addressId ?? null }));
    setSaveChoice((current) => ({ ...current, [side]: { save: false, label: '' } }));
    setLabelErrors((current) => ({ ...current, [side]: undefined }));
    // Clear any errors on the fields the saved address just filled.
    setErrors((current) => {
      const next = { ...current };
      sideFields(side).forEach((field) => delete next[field]);
      return next;
    });
  };

  const addressPicker = (side: AddressSide) =>
    userId ? (
      <SavedAddressPicker
        addresses={addressList}
        selectedId={selectedSaved[side]}
        onSelect={(address) => fillFromSavedAddress(side, address)}
      />
    ) : null;

  // Hidden while the side is filled from a saved address, so the same address is not saved twice.
  const saveOption = (side: AddressSide) =>
    userId && !selectedSaved[side] ? (
      <SaveAddressOption
        saveChoice={saveChoice[side]}
        onSaveChoice={(choice) => {
          setSaveChoice((current) => ({ ...current, [side]: choice }));
          if (labelErrors[side]) {
            setLabelErrors((current) => ({
              ...current,
              [side]: choice.save ? validateAddressLabel(choice.label) : undefined
            }));
          }
        }}
        labelError={labelErrors[side]}
        bookFull={addressList.length >= MAX_SAVED_ADDRESSES}
      />
    ) : null;

  const reset = () => {
    if (userId) clearDraft(userId);
    setClientRequestId(newClientRequestId());
    setSaveChoice(NO_SAVE);
    setSelectedSaved(NO_SELECTION);
    setLabelErrors({});
    setRestored(false);
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
      {restored ? (
        <Alert
          tone="info"
          title="We kept your unfinished booking"
          onDismiss={() => setRestored(false)}
          action={
            <Button type="button" variant="ghost" onClick={reset}>
              Start over
            </Button>
          }
        >
          Pick up where you left off.
        </Alert>
      ) : null}
      <StepIndicator steps={STEPS} current={step} completed={completed} onSelect={goTo} />

      <form noValidate onSubmit={handleNext} className="flex flex-col gap-6">
        {step === 'pickup' ? (
          <PickupStep
            {...stepProps}
            addressPicker={addressPicker('pickup')}
            saveOption={saveOption('pickup')}
          />
        ) : null}
        {step === 'dropoff' ? (
          <DropoffStep
            {...stepProps}
            addressPicker={addressPicker('delivery')}
            saveOption={saveOption('delivery')}
          />
        ) : null}
        {step === 'parcel' ? (
          <>
            <ParcelStep {...stepProps} />
            {/* Orders are only created from the review step; this is just the live price. */}
            {estimateRequest ? (
              <FeePanel
                estimate={estimate}
                status={estimateStatus}
                onRetry={retryEstimate}
                speedLabel={SPEED_LABEL[form.speed]}
              />
            ) : (
              <p className="text-body text-secondary">
                Enter the weight and size to see the price.
              </p>
            )}
          </>
        ) : null}
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
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => (onViewOrders ? onViewOrders() : navigate('/customer/orders'))}
                >
                  Open My parcels
                </Button>
              ) : undefined
            }
          >
            {SUBMIT_COPY[submitProblem].message}
          </Alert>
        ) : null}

        <div
          data-assistant-avoid
          className="flex flex-col-reverse gap-3 border-t border-border-subtle pt-5 sm:flex-row sm:justify-between"
        >
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
