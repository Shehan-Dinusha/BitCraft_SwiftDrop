import { useCallback, useEffect, useState } from 'react';
import type { LiveIndicatorState } from '@/design-system';
import {
  getTrackingView,
  isTerminalPublicStatus,
  parseTrackingView,
  subscribeToTrackingStream,
  TrackingApiError,
  type PublicTrackingView
} from '@/services/api/trackingHubApi';
import {
  createTrackingClient,
  type TrackingClient
} from '@/services/websocket/stompTrackingClient';

const POLL_INTERVAL_MS = 10_000;
const RECONNECT_DELAYS_MS = [1000, 2000, 5000, 10000];

export interface UseTrackingStreamResult {
  view: PublicTrackingView | null;
  connection: LiveIndicatorState;
  error: Error | null;
  isLoading: boolean;
  retry: () => void;
}

function reconnectDelay(failures: number): number {
  return RECONNECT_DELAYS_MS[Math.min(failures - 1, RECONNECT_DELAYS_MS.length - 1)] ?? 1000;
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

function isFatalTrackingError(error: TrackingApiError): boolean {
  return error.status === 404 || error.status === 429;
}

export function useTrackingStream(trackingNumber: string | undefined): UseTrackingStreamResult {
  const [view, setView] = useState<PublicTrackingView | null>(null);
  const [connection, setConnection] = useState<LiveIndicatorState>('offline');
  const [error, setError] = useState<Error | null>(null);
  const [isLoading, setIsLoading] = useState(Boolean(trackingNumber));
  const [attempt, setAttempt] = useState(0);

  const retry = useCallback(() => setAttempt((current) => current + 1), []);

  useEffect(() => {
    if (!trackingNumber) {
      setView(null);
      setError(null);
      setConnection('offline');
      setIsLoading(false);
      return undefined;
    }

    let cancelled = false;
    let phase: 'sse' | 'ws' | 'poll' | 'done' = 'sse';
    let eventSource: EventSource | null = null;
    let wsClient: TrackingClient | null = null;
    let reconnectTimer: number | undefined;
    let pollTimer: number | undefined;
    let sseFailures = 0;
    let wsFailures = 0;
    const abortController = new AbortController();

    setView(null);
    setError(null);
    setIsLoading(true);
    setConnection('reconnecting');

    const clearTimers = () => {
      if (reconnectTimer !== undefined) {
        window.clearTimeout(reconnectTimer);
        reconnectTimer = undefined;
      }
      if (pollTimer !== undefined) {
        window.clearTimeout(pollTimer);
        pollTimer = undefined;
      }
    };

    const closeTransports = () => {
      eventSource?.close();
      eventSource = null;
      if (wsClient) {
        void wsClient.deactivate();
        wsClient = null;
      }
    };

    const stop = () => {
      phase = 'done';
      clearTimers();
      closeTransports();
    };

    const schedule = (callback: () => void, delay: number) => {
      reconnectTimer = window.setTimeout(callback, delay);
    };

    const applyView = (next: PublicTrackingView) => {
      if (cancelled) return;
      setView(next);
      setError(null);
      if (isTerminalPublicStatus(next.currentPublicStatus)) {
        setConnection('offline');
        setIsLoading(false);
        stop();
      }
    };

    const startPolling = () => {
      if (cancelled || phase === 'done') return;
      phase = 'poll';
      setConnection('offline');

      const poll = async () => {
        if (cancelled) return;
        try {
          const next = await getTrackingView(trackingNumber, { signal: abortController.signal });
          if (cancelled) return;
          applyView(next);
          if (phase === 'poll' && !isTerminalPublicStatus(next.currentPublicStatus)) {
            pollTimer = window.setTimeout(() => void poll(), POLL_INTERVAL_MS);
          }
        } catch (cause) {
          if (cancelled || isAbortError(cause)) return;
          const nextError =
            cause instanceof Error ? cause : new Error('Unable to refresh tracking.');
          setError(nextError);
          setConnection('offline');
          if (cause instanceof TrackingApiError && isFatalTrackingError(cause)) {
            setIsLoading(false);
            stop();
            return;
          }
          pollTimer = window.setTimeout(() => void poll(), POLL_INTERVAL_MS);
        }
      };

      void poll();
    };

    const startWebSocket = () => {
      if (cancelled || phase === 'done') return;
      phase = 'ws';
      setConnection('reconnecting');

      wsClient = createTrackingClient(
        trackingNumber,
        (message) => {
          try {
            applyView(parseTrackingView(message.body));
          } catch {
            /* Ignore malformed frames; the next valid update will replace this one. */
          }
        },
        {
          onOpen: () => {
            if (cancelled) return;
            wsFailures = 0;
            setConnection('live');
          },
          onClose: () => {
            if (cancelled || phase !== 'ws') return;
            if (wsClient) {
              void wsClient.deactivate();
              wsClient = null;
            }
            wsFailures += 1;
            setConnection('reconnecting');
            if (wsFailures >= 2) {
              startPolling();
              return;
            }
            schedule(startWebSocket, reconnectDelay(wsFailures));
          }
        }
      );
      wsClient.activate();
    };

    const startSse = () => {
      if (cancelled || phase === 'done') return;
      phase = 'sse';
      setConnection('reconnecting');

      eventSource = subscribeToTrackingStream(trackingNumber, applyView, {
        onOpen: () => {
          if (cancelled) return;
          sseFailures = 0;
          setConnection('live');
        },
        onError: (event) => {
          if (cancelled || phase !== 'sse') return;
          const source = event.target;
          if (source instanceof EventSource) source.close();
          eventSource = null;
          sseFailures += 1;
          setConnection('reconnecting');
          if (sseFailures >= 2) {
            schedule(startWebSocket, reconnectDelay(sseFailures));
            return;
          }
          schedule(startSse, reconnectDelay(sseFailures));
        }
      });
    };

    const loadInitial = async () => {
      try {
        const next = await getTrackingView(trackingNumber, { signal: abortController.signal });
        if (cancelled) return;
        setIsLoading(false);
        applyView(next);
        if (!isTerminalPublicStatus(next.currentPublicStatus)) {
          startSse();
        }
      } catch (cause) {
        if (cancelled || isAbortError(cause)) return;
        setIsLoading(false);
        setConnection('offline');
        setError(cause instanceof Error ? cause : new Error('Unable to load tracking.'));
        stop();
      }
    };

    void loadInitial();

    return () => {
      cancelled = true;
      abortController.abort();
      stop();
    };
  }, [attempt, trackingNumber]);

  return { view, connection, error, isLoading, retry };
}
