import type { PublicOrderStatus } from '@/types/contracts';

export type { PublicOrderStatus };

export type SriLankanDistrict =
  | 'COLOMBO'
  | 'GAMPAHA'
  | 'KALUTARA'
  | 'KANDY'
  | 'MATALE'
  | 'NUWARA_ELIYA'
  | 'GALLE'
  | 'MATARA'
  | 'HAMBANTOTA'
  | 'JAFFNA'
  | 'KILINOCHCHI'
  | 'MANNAR'
  | 'VAVUNIYA'
  | 'MULLAITIVU'
  | 'BATTICALOA'
  | 'AMPARA'
  | 'TRINCOMALEE'
  | 'KURUNEGALA'
  | 'PUTTALAM'
  | 'ANURADHAPURA'
  | 'POLONNARUWA'
  | 'BADULLA'
  | 'MONARAGALA'
  | 'RATNAPURA'
  | 'KEGALLE';

export type PublicTrackingTimelineEntry = {
  status: PublicOrderStatus;
  district: SriLankanDistrict | null;
  timestamp: string;
};

export type PublicTrackingView = {
  trackingNumber: string;
  currentPublicStatus: PublicOrderStatus;
  originDistrict: SriLankanDistrict | null;
  destinationDistrict: SriLankanDistrict | null;
  statusTimeline: PublicTrackingTimelineEntry[];
};

const gatewayOrigin = 'http://localhost:8080';

export async function getTrackingView(trackingNumber: string): Promise<PublicTrackingView> {
  const response = await fetch(
    `${gatewayOrigin}/api/v1/public/track/${encodeURIComponent(trackingNumber)}`
  );
  if (!response.ok) {
    throw new Error(`Tracking request failed with status ${response.status}`);
  }
  return response.json() as Promise<PublicTrackingView>;
}

export function subscribeToTrackingStream(
  trackingNumber: string,
  onUpdate: (view: PublicTrackingView) => void,
  onError?: (event: Event) => void
): EventSource {
  const source = new EventSource(
    `${gatewayOrigin}/api/v1/public/track/${encodeURIComponent(trackingNumber)}/stream`
  );
  source.onmessage = (event) => onUpdate(JSON.parse(event.data) as PublicTrackingView);
  source.onerror = (event) => onError?.(event);
  return source;
}
