export type TrackingMessage = {
  body: string;
};

type TrackingClient = {
  activate: () => void;
  deactivate: () => Promise<void>;
};

const gatewayOrigin = 'http://localhost:8080';

export const createTrackingClient = (
  trackingId: string,
  onMessage: (message: TrackingMessage) => void
): TrackingClient => {
  let socket: WebSocket | undefined;

  return {
    activate: () => {
      const protocol = gatewayOrigin.startsWith('https') ? 'wss' : 'ws';
      const host = gatewayOrigin.replace(/^https?:\/\//, '');
      socket = new WebSocket(
        `${protocol}://${host}/ws/tracking?trackingId=${encodeURIComponent(trackingId)}`
      );
      socket.onmessage = (event) => onMessage({ body: event.data });
    },
    deactivate: async () => {
      socket?.close();
      socket = undefined;
    }
  };
};
