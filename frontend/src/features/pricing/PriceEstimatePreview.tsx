import { useEffect, useRef, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { PriceBreakdown, Skeleton, StatusBanner } from '@/design-system';
import {
  pricingApi,
  readPricingApiError,
  type EstimateRequest,
  type EstimateResponse
} from '@/services/api/pricingApi';
import { cn } from '@/utils/cn';

const DEBOUNCE_MS = 400;

export interface PriceEstimatePreviewProps {
  /** Wizard form values. The preview waits until districts and a positive weight are present. */
  request: Partial<EstimateRequest>;
  /** Called whenever a new estimate arrives, e.g. to store the fee in the order form. */
  onResult?: (estimate: EstimateResponse) => void;
  className?: string;
}

function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

function isComplete(request: Partial<EstimateRequest>): request is EstimateRequest {
  return Boolean(
    request.pickupDistrict &&
      request.deliveryDistrict &&
      request.actualWeightKg !== undefined &&
      request.actualWeightKg > 0
  );
}

const speedLabels: Record<string, string> = {
  STANDARD: 'Standard',
  EXPRESS: 'Express',
  SAME_DAY: 'Same-day'
};

/** Live delivery fee preview. Debounces input by 400 ms and shows the fee breakdown. */
export function PriceEstimatePreview({ request, onResult, className }: PriceEstimatePreviewProps) {
  const debounced = useDebouncedValue(request, DEBOUNCE_MS);
  const ready = isComplete(debounced);

  const { data, error, isFetching } = useQuery({
    queryKey: ['pricing', 'estimate', debounced],
    queryFn: () => pricingApi.estimate(debounced as EstimateRequest),
    enabled: ready,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
    retry: false
  });

  const onResultRef = useRef(onResult);
  onResultRef.current = onResult;
  useEffect(() => {
    if (data) onResultRef.current?.(data);
  }, [data]);

  if (!ready) {
    return (
      <p className={cn('text-body text-secondary', className)}>
        Enter the pickup district, delivery district and weight to see the delivery fee.
      </p>
    );
  }

  if (error && !data) {
    const { message } = readPricingApiError(error);
    return (
      <StatusBanner tone="danger" title="Could not calculate the fee">
        {message ?? 'Please check the parcel details and try again.'}
      </StatusBanner>
    );
  }

  if (!data) {
    return (
      <div aria-busy="true" className={className}>
        <Skeleton preset="card" />
      </div>
    );
  }

  const speed = data.speed ?? 'STANDARD';
  const baseFee = data.baseFee ?? 0;
  const weightFee = data.weightFee ?? 0;
  const speedSurcharge = data.speedSurcharge ?? 0;

  return (
    <div aria-busy={isFetching} className={cn(isFetching && 'opacity-70', className)}>
      <PriceBreakdown
        type="customer"
        currency={data.currency ?? 'LKR'}
        lines={[
          {
            label: 'Base fee',
            note: data.intraDistrict ? 'Same district' : `${data.pickupZone} to ${data.deliveryZone}`,
            amount: baseFee
          },
          {
            label: 'Weight fee',
            note: `Chargeable ${(data.chargeableWeightKg ?? 0).toFixed(2)} kg`,
            amount: weightFee
          }
        ]}
        subtotal={baseFee + weightFee}
        surcharge={
          speedSurcharge > 0
            ? { label: `${speedLabels[speed] ?? speed} delivery`, amount: speedSurcharge }
            : undefined
        }
        total={data.estimatedFee ?? 0}
        footnote={data.pricingRuleVersionId ? `Pricing rule ${data.pricingRuleVersionId}` : undefined}
      />
    </div>
  );
}

export default PriceEstimatePreview;