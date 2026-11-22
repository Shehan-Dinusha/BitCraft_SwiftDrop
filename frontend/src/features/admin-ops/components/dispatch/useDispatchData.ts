import { useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { dispatchApi, type SriLankanDistrict } from '@/services/api/dispatchApi';
import { mergeActiveAssignments } from './dispatchQueues';

/** Every list refreshes itself at this pace while the browser tab is visible. */
const REFRESH_MS = 10_000;
const PAGE_SIZE = 100;
const ROOT = 'dispatch-console';

/**
 * The dispatch lists. `scope` is the district an admin looks at (the server decides for a
 * dispatcher). `hubLists` is off for an admin viewing every district: a hub belongs to one district.
 */
export function useDispatchData(
  viewer: string | undefined,
  scope: SriLankanDistrict | undefined,
  hubLists: boolean
) {
  const client = useQueryClient();
  const options = { refetchInterval: REFRESH_MS, retry: 1 };
  const key = (list: string) => [ROOT, list, viewer, scope ?? 'all'];

  const unassigned = useQuery({
    queryKey: key('unassigned'),
    queryFn: () => dispatchApi.getUnassignedParcels(scope),
    ...options
  });
  const assigned = useQuery({
    queryKey: key('assigned'),
    queryFn: async () => {
      const [first, moved] = await Promise.all([
        dispatchApi.getAssignments({
          status: 'ASSIGNED',
          page: 0,
          size: PAGE_SIZE,
          district: scope
        }),
        dispatchApi.getAssignments({
          status: 'REASSIGNED',
          page: 0,
          size: PAGE_SIZE,
          district: scope
        })
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
    queryFn: () => dispatchApi.getReadyToSend(scope),
    enabled: hubLists,
    ...options
  });
  const incoming = useQuery({
    queryKey: key('incoming'),
    queryFn: () => dispatchApi.getIncoming(scope),
    enabled: hubLists,
    ...options
  });
  const returns = useQuery({
    queryKey: key('returns'),
    queryFn: () => dispatchApi.getReturns(scope),
    enabled: hubLists,
    ...options
  });
  const couriers = useQuery({
    queryKey: key('couriers'),
    queryFn: () => dispatchApi.getCouriers({ district: scope }),
    ...options
  });

  const refresh = useCallback(() => client.invalidateQueries({ queryKey: [ROOT] }), [client]);
  return { unassigned, assigned, ready, incoming, returns, couriers, refresh };
}
