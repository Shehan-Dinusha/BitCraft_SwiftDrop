import { useCallback, useEffect, useRef, useState } from 'react';
import {
  orderApi,
  readOrderApiError,
  type FeeEstimateRequest,
  type FeeEstimateResponse
} from '@/services/api/orderApi';

export type FeeEstimateStatus = 'idle' | 'loading' | 'ready' | 'unavailable' | 'error';

const DEBOUNCE_MS = 400;

/**
 * Fetches the fee for `request` (pass `null` while the inputs are incomplete). Calls are debounced
 * and only the latest response is kept, so a slow reply for an old weight cannot overwrite the price
 * for the current one. A 503 from the order service means the pricing service is down.
 */
export function useFeeEstimate(request: FeeEstimateRequest | null) {
  const [estimate, setEstimate] = useState<FeeEstimateResponse | null>(null);
  const [status, setStatus] = useState<FeeEstimateStatus>('idle');
  const [attempt, setAttempt] = useState(0);
  const latest = useRef(0);
  const key = request ? JSON.stringify(request) : null;

  useEffect(() => {
    if (!key) {
      latest.current += 1;
      setEstimate(null);
      setStatus('idle');
      return;
    }

    const id = ++latest.current;
    setStatus('loading');
    const timer = window.setTimeout(() => {
      orderApi
        .estimateFee(JSON.parse(key) as FeeEstimateRequest)
        .then((result) => {
          if (id !== latest.current) return;
          setEstimate(result);
          setStatus('ready');
        })
        .catch((error: unknown) => {
          if (id !== latest.current) return;
          setEstimate(null);
          setStatus(readOrderApiError(error).status === 503 ? 'unavailable' : 'error');
        });
    }, DEBOUNCE_MS);

    return () => window.clearTimeout(timer);
  }, [key, attempt]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);

  return { estimate, status, retry };
}
