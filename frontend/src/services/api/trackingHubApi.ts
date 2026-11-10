import type { components } from '@/types/generated/api-gateway';

type Schemas = components['schemas'];

export type PublicOrderStatus = Schemas['PublicOrderStatus'];
export type SriLankanDistrict = Schemas['SriLankanDistrict'];
export type PublicTrackingTimelineEntry = Schemas['PublicTrackingTimelineEntry'];
export type PublicTrackingView = Schemas['PublicTrackingView'];

const TRACKING_BASE_PATH = '/api/v1/public/track';
const TERMINAL_STATUSES: PublicOrderStatus[] = [
  'DELIVERED',
  'FAILED',
  'CANCELLED',
  'RETURNED_TO_SENDER'
];

export class TrackingApiError extends Error {
  readonly status?: number;
  readonly retryAfterSeconds?: number;

  constructor(
    message: string,
    options: { status?: number; retryAfterSeconds?: number; cause?: unknown } = {}
  ) {
    super(message, { cause: options.cause });
    this.name = 'TrackingApiError';
    this.status = options.status;
    this.retryAfterSeconds = options.retryAfterSeconds;
  }
}

export function isTerminalPublicStatus(status: PublicOrderStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}

export function getTrackingStreamUrl(trackingNumber: string): string {
  return `${TRACKING_BASE_PATH}/${encodeURIComponent(trackingNumber)}/stream`;
}

export async function getTrackingView(
  trackingNumber: string,
  options: { signal?: AbortSignal } = {}
): Promise<PublicTrackingView> {
  let response: Response;
  try {
    response = await fetch(`${TRACKING_BASE_PATH}/${encodeURIComponent(trackingNumber)}`, {
      headers: { Accept: 'application/json' },
      signal: options.signal
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new TrackingApiError('Unable to reach the tracking service.', { cause });
  }

  if (!response.ok) {
    throw await toTrackingError(response);
  }

  return (await response.json()) as PublicTrackingView;
}

export interface TrackingStreamHandlers {
  onOpen?: () => void;
  onError?: (event: Event) => void;
}

export function subscribeToTrackingStream(
  trackingNumber: string,
  onUpdate: (view: PublicTrackingView) => void,
  handlers: TrackingStreamHandlers = {}
): EventSource {
  const source = new EventSource(getTrackingStreamUrl(trackingNumber));
  source.onopen = () => handlers.onOpen?.();
  source.onmessage = (event) => {
    try {
      onUpdate(parseTrackingView(event.data));
    } catch {
      /* Ignore malformed frames; the next valid update will replace this one. */
    }
  };
  source.onerror = (event) => handlers.onError?.(event);
  return source;
}

export function parseTrackingView(payload: string): PublicTrackingView {
  return JSON.parse(payload) as PublicTrackingView;
}

async function toTrackingError(response: Response): Promise<TrackingApiError> {
  const retryAfterSeconds = parseRetryAfter(response.headers.get('Retry-After'));
  const message =
    response.status === 404
      ? 'Tracking number not found.'
      : response.status === 429
        ? 'Too many tracking requests. Please wait and try again.'
        : `Tracking request failed with status ${response.status}.`;

  return new TrackingApiError(message, {
    status: response.status,
    retryAfterSeconds
  });
}

function parseRetryAfter(value: string | null): number | undefined {
  if (!value) return undefined;

  const seconds = Number(value);
  if (Number.isFinite(seconds)) {
    return Math.max(0, Math.ceil(seconds));
  }

  const retryAt = Date.parse(value);
  if (Number.isNaN(retryAt)) return undefined;
  return Math.max(0, Math.ceil((retryAt - Date.now()) / 1000));
}
