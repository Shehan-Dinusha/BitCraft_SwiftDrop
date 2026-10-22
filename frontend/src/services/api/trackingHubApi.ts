export type PublicTrackingView = {
  trackingId: string;
  status: string;
  latitude: number | null;
  longitude: number | null;
  milestones: string[];
  updatedAt: string;
};

const gatewayOrigin = 'http://localhost:8080';

export async function getTrackingView(trackingId: string): Promise<PublicTrackingView> {
  const response = await fetch(`${gatewayOrigin}/api/v1/tracking/${encodeURIComponent(trackingId)}`);
  if (!response.ok) {
    throw new Error(`Tracking request failed with status ${response.status}`);
  }
  return response.json() as Promise<PublicTrackingView>;
}

export function subscribeToTrackingStream(
  trackingId: string,
  onUpdate: (view: PublicTrackingView) => void,
  onError?: (event: Event) => void
): EventSource {
  const source = new EventSource(
    `${gatewayOrigin}/api/v1/tracking/${encodeURIComponent(trackingId)}/stream`
  );
  source.onmessage = event => onUpdate(JSON.parse(event.data) as PublicTrackingView);
  source.onerror = event => onError?.(event);
  return source;
}
