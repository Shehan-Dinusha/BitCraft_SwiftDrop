import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Field, Modal, Skeleton, StatusBanner, SurfaceCard } from '@/design-system';
import {
  pricingApi,
  readPricingApiError,
  type TariffRuleRequest,
  type TariffRuleResponse
} from '@/services/api/pricingApi';

const ACTIVE_TARIFF_KEY = ['pricing', 'tariffs', 'active'] as const;
const TARIFFS_KEY = ['pricing', 'tariffs'] as const;

interface TariffForm {
  ruleVersionId: string;
  basePrice: string;
  weightStepKg: string;
  pricePerStep: string;
  volumetricDivisor: string;
}

const emptyForm: TariffForm = {
  ruleVersionId: '',
  basePrice: '',
  weightStepKg: '',
  pricePerStep: '',
  volumetricDivisor: ''
};

type FormErrors = Partial<Record<keyof TariffForm, string>>;

// The version id is left blank so the server generates a new one.
function toForm(tariff: TariffRuleResponse): TariffForm {
  return {
    ruleVersionId: '',
    basePrice: String(tariff.basePrice ?? ''),
    weightStepKg: String(tariff.weightStepKg ?? ''),
    pricePerStep: String(tariff.pricePerStep ?? ''),
    volumetricDivisor: String(tariff.volumetricDivisor ?? '')
  };
}

function validate(form: TariffForm): FormErrors {
  const errors: FormErrors = {};
  const isBlankOrInvalid = (raw: string) => raw.trim() === '' || Number.isNaN(Number(raw));

  if (isBlankOrInvalid(form.basePrice) || Number(form.basePrice) < 0) {
    errors.basePrice = 'Enter an amount of 0 or more';
  }
  if (isBlankOrInvalid(form.pricePerStep) || Number(form.pricePerStep) < 0) {
    errors.pricePerStep = 'Enter an amount of 0 or more';
  }
  if (isBlankOrInvalid(form.weightStepKg) || Number(form.weightStepKg) <= 0) {
    errors.weightStepKg = 'Enter a weight step greater than 0';
  }
  if (isBlankOrInvalid(form.volumetricDivisor) || Number(form.volumetricDivisor) <= 0) {
    errors.volumetricDivisor = 'Enter a divisor greater than 0';
  }
  return errors;
}

function toRequest(form: TariffForm): TariffRuleRequest {
  const versionId = form.ruleVersionId.trim();
  return {
    ...(versionId ? { ruleVersionId: versionId } : {}),
    basePrice: Number(form.basePrice),
    weightStepKg: Number(form.weightStepKg),
    pricePerStep: Number(form.pricePerStep),
    volumetricDivisor: Number(form.volumetricDivisor)
  };
}

export function TariffEditor() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<TariffForm>(emptyForm);
  const [errors, setErrors] = useState<FormErrors>({});
  const [confirming, setConfirming] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'danger'; text: string } | null>(
    null
  );

  const activeQuery = useQuery({
    queryKey: ACTIVE_TARIFF_KEY,
    queryFn: pricingApi.getActiveTariff,
    retry: false
  });

  const active = activeQuery.data;
  // A 404 only means no tariff has been published yet, so the form simply starts empty.
  const loadFailed = activeQuery.isError && readPricingApiError(activeQuery.error).status !== 404;

  useEffect(() => {
    if (active) setForm(toForm(active));
  }, [active]);

  const publish = useMutation({
    mutationFn: () => pricingApi.publishTariff(toRequest(form)),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ACTIVE_TARIFF_KEY });
      void queryClient.invalidateQueries({ queryKey: TARIFFS_KEY });
      setFeedback({
        tone: 'success',
        text: `Tariff version ${created.ruleVersionId ?? ''} is now active.`.replace('  ', ' ')
      });
      setConfirming(false);
    },
    onError: (error) => {
      const { status, message } = readPricingApiError(error);
      let text = message ?? 'Could not publish the tariff.';
      if (status === 403) text = 'Only dispatchers and system admins can publish tariffs.';
      if (status === 409) text = 'A tariff version with this ID already exists.';
      setFeedback({ tone: 'danger', text });
      setConfirming(false);
    }
  });

  const setField = (key: keyof TariffForm) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }));

  const requestPublish = () => {
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length === 0) setConfirming(true);
  };

  return (
    <SurfaceCard title="Default tariff">
      <div className="flex flex-col gap-3">
        {feedback ? (
          <StatusBanner
            tone={feedback.tone}
            title={feedback.tone === 'success' ? 'Done' : 'Something went wrong'}
          >
            {feedback.text}
          </StatusBanner>
        ) : null}

        {activeQuery.isLoading ? (
          <div aria-busy="true">
            <Skeleton preset="table-row" />
          </div>
        ) : loadFailed ? (
          <StatusBanner tone="danger" title="Could not load the active tariff">
            {readPricingApiError(activeQuery.error).message ?? 'Please try again.'}
          </StatusBanner>
        ) : (
          <>
            <p className="text-body text-secondary">
              {active
                ? `Active version: ${active.ruleVersionId ?? 'unknown'}. Publishing creates a new version.`
                : 'No tariff has been published yet.'}
            </p>

            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <Field
                label="Base price (LKR)"
                required
                value={form.basePrice}
                onChange={setField('basePrice')}
                error={errors.basePrice}
                inputMode="decimal"
              />
              <Field
                label="Weight step (kg)"
                required
                value={form.weightStepKg}
                onChange={setField('weightStepKg')}
                error={errors.weightStepKg}
                inputMode="decimal"
              />
              <Field
                label="Price per step (LKR)"
                required
                value={form.pricePerStep}
                onChange={setField('pricePerStep')}
                error={errors.pricePerStep}
                inputMode="decimal"
              />
              <Field
                label="Volumetric divisor"
                required
                value={form.volumetricDivisor}
                onChange={setField('volumetricDivisor')}
                error={errors.volumetricDivisor}
                inputMode="decimal"
              />
              <Field
                label="Version ID"
                value={form.ruleVersionId}
                onChange={setField('ruleVersionId')}
                hint="Optional. Generated automatically when left blank."
              />
            </div>

            <div className="flex justify-end">
              <Button variant="primary" onClick={requestPublish}>
                Publish tariff
              </Button>
            </div>
          </>
        )}
      </div>

      <Modal
        open={confirming}
        onClose={() => setConfirming(false)}
        title="Publish tariff"
        description="Publishing creates a new tariff version and the current version becomes inactive."
        footer={
          <span className="flex gap-2">
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={publish.isPending} onClick={() => publish.mutate()}>
              Publish
            </Button>
          </span>
        }
      >
        <p className="text-body text-secondary">
          New estimates will use the new version. Existing orders keep the pricing version they
          were quoted with.
        </p>
      </Modal>
    </SurfaceCard>
  );
}

export default TariffEditor;