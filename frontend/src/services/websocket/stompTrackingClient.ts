export type TrackingMessage = {
  body: string;
};

type TrackingClient = {
  activate: () => void;
  deactivate: () => Promise<void>;
};

export interface TrackingClientHandlers {
  onOpen?: () => void;
  onError?: (event: Event) => void;
  onClose?: (event: CloseEvent) => void;
}

function trackingSocketUrl(trackingId: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/ws/tracking?trackingId=${encodeURIComponent(trackingId)}`;
}

export const createTrackingClient = (
  trackingId: string,
  onMessage: (message: TrackingMessage) => void,
  handlers: TrackingClientHandlers = {}
): TrackingClient => {
  let socket: WebSocket | undefined;

  return {
    activate: () => {
      socket = new WebSocket(trackingSocketUrl(trackingId));
      socket.onopen = () => handlers.onOpen?.();
      socket.onmessage = (event) => onMessage({ body: event.data });
      socket.onerror = (event) => handlers.onError?.(event);
      socket.onclose = (event) => handlers.onClose?.(event);
    },
    deactivate: async () => {
      socket?.close();
      socket = undefined;
    }
  };
};
