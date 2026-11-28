import { useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { dispatchApi } from '@/services/api/dispatchApi';
import { mergeActiveAssignments } from './dispatchQueues';

/** Every list refreshes itself at this pace while the browser tab is visible. */
const REFRESH_MS = 10_000;
const PAGE_SIZE = 100;
const ROOT = 'dispatch-console';

/** The dispatch lists of the dispatcher's own district (the server decides the district). */
export function useDispatchData(viewer: string | undefined) {
  const client = useQueryClient();
  const options = { refetchInterval: REFRESH_MS, retry: 1 };
  const key = (list: string) => [ROOT, list, viewer];

  const unassigned = useQuery({
    queryKey: key('unassigned'),
    queryFn: () => dispatchApi.getUnassignedParcels(),
    ...options
  });
  const assigned = useQuery({
    queryKey: key('assigned'),
    queryFn: async () => {
      const [first, moved] = await Promise.all([
        dispatchApi.getAssignments({ status: 'ASSIGNED', page: 0, size: PAGE_SIZE }),
        dispatchApi.getAssignments({ status: 'REASSIGNED', page: 0, size: PAGE_SIZE })
      ]);
      return {
        orders: mergeActiveAssignments(first.content, moved.content),
        total: first.totalElements + moved.totalElements
      };
    },
    ...options
  });
  const ready = useQuery({
    queryKey: key('ready'),
    queryFn: () => dispatchApi.getReadyToSend(),
    ...options
  });
  const sent = useQuery({
    queryKey: key('sent'),
    queryFn: () => dispatchApi.getSent(),
    ...options
  });
  const incoming = useQuery({
    queryKey: key('incoming'),
    queryFn: () => dispatchApi.getIncoming(),
    ...options
  });
  const returns = useQuery({
    queryKey: key('returns'),
    queryFn: () => dispatchApi.getReturns(),
    ...options
  });
  const couriers = useQuery({
    queryKey: key('couriers'),
    queryFn: () => dispatchApi.getCouriers(),
    ...options
  });

  const refresh = useCallback(() => client.invalidateQueries({ queryKey: [ROOT] }), [client]);
  return { unassigned, assigned, ready, sent, incoming, returns, couriers, refresh };
}
