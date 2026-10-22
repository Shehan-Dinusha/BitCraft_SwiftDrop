import type { GpsTelemetry } from '@/types/telemetry';
export function GpsTelemetryPublisher({ onUpdate }: { onUpdate: (t: GpsTelemetry) => void }) {
  return (
    <button
      className="text-xs"
      onClick={() =>
        onUpdate({
          latitude: 0,
          longitude: 0,
          bearingDegrees: 0,
          speedKmh: 0,
          accuracy: 0,
          capturedAt: new Date().toISOString()
        })
      }
    >
      GPS telemetry active
    </button>
  );
}
