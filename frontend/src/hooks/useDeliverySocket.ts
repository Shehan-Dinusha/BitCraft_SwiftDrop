import {useEffect} from 'react';
import {createTrackingClient} from '@/services/websocket/stompTrackingClient';
import {useActiveDeliveryStore} from '@/store/useActiveDeliveryStore';
import type {LocationUpdatedEvent} from '@/types/contracts';

export function useDeliverySocket(trackingId = 'unknown') {
  const setLocation = useActiveDeliveryStore(s => s.setLocation);
  useEffect(() => {
    const client = createTrackingClient(trackingId, message => {
      const update = JSON.parse(message.body) as {
        latitude?: number | null;
        longitude?: number | null;
      };
      if (typeof update.latitude === 'number' && typeof update.longitude === 'number') {
        const location: LocationUpdatedEvent = {
          eventId: '',
          correlationId: '',
          deliveryId: trackingId,
          courierId: '',
          latitude: update.latitude,
          longitude: update.longitude,
          bearingDegrees: 0,
          speedKmh: 0,
          accuracy: 0,
          timestamp: new Date().toISOString()
        };
        setLocation(location);
      }
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [setLocation, trackingId]);
}
