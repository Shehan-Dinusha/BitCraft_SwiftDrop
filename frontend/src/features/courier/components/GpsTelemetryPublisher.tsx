import { useState, useEffect, useRef, useCallback } from 'react';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { deliveryApi } from '@/services/api/deliveryApi';
import type { TrackingBreadcrumbResponse } from '@/services/api/deliveryApi';
import { Navigation, Radio, MapPin, Gauge } from 'lucide-react';

export interface GpsTelemetryPublisherProps {
  deliveryId: string;
  courierId: string;
  onBreadcrumbAdded?: (breadcrumb: TrackingBreadcrumbResponse) => void;
}

export function GpsTelemetryPublisher({
  deliveryId,
  courierId,
  onBreadcrumbAdded
}: GpsTelemetryPublisherProps) {
  const [isAutoPinging, setIsAutoPinging] = useState(false);
  const [pinging, setPinging] = useState(false);
  const [lastCoords, setLastCoords] = useState<{ lat: number; lng: number; speed: number }>({
    lat: 6.9271,
    lng: 79.8612,
    speed: 32.5
  });
  const [breadcrumbsCount, setBreadcrumbsCount] = useState(0);
  const timerRef = useRef<number | null>(null);

  // Send single GPS location update
  const sendLocationPing = useCallback(async () => {
    try {
      setPinging(true);
      // Simulate minor coordinate movement along delivery route in Colombo
      const newLat = +(lastCoords.lat + (Math.random() - 0.48) * 0.002).toFixed(6);
      const newLng = +(lastCoords.lng + (Math.random() - 0.48) * 0.002).toFixed(6);
      const newSpeed = +(25 + Math.random() * 20).toFixed(1);

      const res = await deliveryApi.recordLocation(deliveryId, {
        latitude: newLat,
        longitude: newLng,
        bearingDegrees: +(Math.random() * 360).toFixed(1),
        speedKmh: newSpeed,
        accuracy: 4.5
      });

      setLastCoords({ lat: newLat, lng: newLng, speed: newSpeed });
      setBreadcrumbsCount((prev) => prev + 1);
      if (onBreadcrumbAdded) {
        onBreadcrumbAdded(res);
      }
    } catch (err) {
      console.error('Failed to publish GPS telemetry breadcrumb', err);
    } finally {
      setPinging(false);
    }
  }, [deliveryId, courierId, lastCoords, onBreadcrumbAdded]);

  useEffect(() => {
    if (isAutoPinging) {
      timerRef.current = window.setInterval(() => {
        sendLocationPing();
      }, 10000);
    } else if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    return () => {
      if (timerRef.current !== null) {
        window.clearInterval(timerRef.current);
      }
    };
  }, [isAutoPinging, sendLocationPing]);

  return (
    <SurfaceCard className="space-y-3 p-4" data-density="touch">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Radio className={`h-4 w-4 ${isAutoPinging ? 'animate-pulse text-emerald-500' : 'text-slate-400'}`} />
          <h4 className="text-sm font-bold text-slate-900">
            GPS Telemetry Streamer
          </h4>
        </div>
        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
          {breadcrumbsCount} Pings Sent
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3 text-xs">
        <div className="flex items-center gap-1.5 text-slate-600">
          <MapPin className="h-3.5 w-3.5 text-indigo-500" />
          <span>
            {lastCoords.lat.toFixed(4)}, {lastCoords.lng.toFixed(4)}
          </span>
        </div>
        <div className="flex items-center gap-1.5 text-slate-600">
          <Gauge className="h-3.5 w-3.5 text-cyan-500" />
          <span>{lastCoords.speed} km/h (In-Transit)</span>
        </div>
      </div>

      <div className="flex items-center gap-2 pt-1">
        <Button
          variant="outline"
          className="flex-1 min-h-[44px] text-xs font-medium"
          onClick={sendLocationPing}
          loading={pinging}
          disabled={pinging}
        >
          <Navigation className="mr-2 h-3.5 w-3.5 text-indigo-600" />
          Send Ping
        </Button>

        <Button
          variant={isAutoPinging ? 'destructive' : 'primary'}
          className="flex-1 min-h-[44px] text-xs font-medium"
          onClick={() => setIsAutoPinging(!isAutoPinging)}
        >
          {isAutoPinging ? 'Stop Auto-Ping' : 'Start Auto-Ping (10s)'}
        </Button>
      </div>
    </SurfaceCard>
  );
}
