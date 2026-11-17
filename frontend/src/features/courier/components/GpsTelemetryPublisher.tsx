import type { TrackingBreadcrumbResponse } from '@/services/api/deliveryApi';

export interface GpsTelemetryPublisherProps {
  deliveryId: string;
  courierId: string;
  onBreadcrumbAdded?: (breadcrumb: TrackingBreadcrumbResponse) => void;
}

export function GpsTelemetryPublisher({
  deliveryId: _deliveryId,
  courierId: _courierId,
  onBreadcrumbAdded: _onBreadcrumbAdded
}: GpsTelemetryPublisherProps) {
  return null;
}

